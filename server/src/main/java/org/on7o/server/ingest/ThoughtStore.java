package org.on7o.server.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.on7o.server.stt.Transcription;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Writes captures to disk, one directory per thought:
 *
 * <pre>
 * {root}/{id}/audio.wav
 * {root}/{id}/thought.json
 * </pre>
 *
 * The filesystem is deliberately the whole storage layer for now. Ids are
 * timestamp-prefixed so that lexical order is chronological order.
 */
@Service
public class ThoughtStore {

    private static final Logger log = LoggerFactory.getLogger(ThoughtStore.class);
    private static final DateTimeFormatter ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String AUDIO_FILE = "audio.wav";
    private static final String META_FILE = "thought.json";
    private static final String TRANSCRIPTION_FILE = "transcription.json";
    private static final int COPY_BUFFER = 8 * 1024;

    private final StorageProperties properties;
    private final ObjectMapper objectMapper;
    private final Path root;

    public ThoughtStore(StorageProperties properties, ObjectMapper objectMapper) throws IOException {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.root = Path.of(properties.getRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("on7o thought store at {}", root);
    }

    public Path root() {
        return root;
    }

    /**
     * Streams a capture to disk. {@code body} may be raw PCM (wrapped in a WAV
     * container here) or an already-formed WAV file, and its length may be
     * unknown up front — the header is patched once the stream ends.
     */
    public Thought store(InputStream body,
                         PcmFormat declaredFormat,
                         boolean bodyIsWav,
                         String deviceId,
                         Instant capturedAt,
                         String remoteAddress) throws IOException {

        Instant receivedAt = Instant.now();
        String id = newId(receivedAt);
        Path dir = root.resolve(id);
        Files.createDirectories(dir);

        try {
            Path audio = dir.resolve(AUDIO_FILE);
            PcmFormat format = declaredFormat;
            long pcmBytes;

            try (RandomAccessFile out = new RandomAccessFile(audio.toFile(), "rw")) {
                if (bodyIsWav) {
                    byte[] header = body.readNBytes(WavHeader.SIZE);
                    PcmFormat parsed = WavHeader.parse(header);
                    if (parsed != null) {
                        format = parsed;
                    } else {
                        log.warn("capture {} declared audio/wav but header is unreadable; "
                                + "keeping declared format {}", id, declaredFormat);
                    }
                    out.write(header);
                    pcmBytes = copy(body, out, header.length);
                } else {
                    out.write(WavHeader.of(format, 0));
                    pcmBytes = copy(body, out, WavHeader.SIZE);
                    out.seek(0);
                    out.write(WavHeader.of(format, pcmBytes));
                }
            }

            long audioBytes = Files.size(audio);
            Thought thought = new Thought(
                    id,
                    deviceId,
                    capturedAt != null ? capturedAt : receivedAt,
                    receivedAt,
                    AUDIO_FILE,
                    audioBytes,
                    pcmBytes,
                    format.durationMs(pcmBytes),
                    format.sampleRate(),
                    format.channels(),
                    format.bitsPerSample(),
                    remoteAddress);

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dir.resolve(META_FILE).toFile(), thought);
            return thought;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(dir);
            throw e;
        }
    }

    public Optional<Thought> find(String id) {
        Path meta = resolveSafely(id).resolve(META_FILE);
        if (!Files.isRegularFile(meta)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(meta.toFile(), Thought.class));
        } catch (IOException e) {
            log.warn("unreadable metadata for thought {}", id, e);
            return Optional.empty();
        }
    }

    /**
     * Stores what speech-to-text understood, alongside the capture it came from.
     * Written as its own file so a new transcription never rewrites the thought.
     */
    public void saveTranscription(Transcription transcription) throws IOException {
        Path dir = resolveSafely(transcription.thoughtId());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(dir.resolve(TRANSCRIPTION_FILE).toFile(), transcription);
    }

    public Optional<Transcription> findTranscription(String id) {
        Path file = resolveSafely(id).resolve(TRANSCRIPTION_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), Transcription.class));
        } catch (IOException e) {
            log.warn("unreadable transcription for thought {}", id, e);
            return Optional.empty();
        }
    }

    public Path audioPath(Thought thought) {
        return resolveSafely(thought.id()).resolve(thought.audioFile());
    }

    /** Most recent captures first. */
    public List<Thought> list(int limit) throws IOException {
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .map(this::find)
                    .flatMap(Optional::stream)
                    .limit(limit)
                    .toList();
        }
    }

    /**
     * Resolves an id against the store root, rejecting anything that would escape
     * it. The endpoint is unauthenticated, so path traversal is the one input
     * check that still has to hold.
     */
    private Path resolveSafely(String id) {
        Path resolved = root.resolve(id).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("invalid thought id: " + id);
        }
        return resolved;
    }

    private long copy(InputStream in, RandomAccessFile out, long alreadyWritten) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total + alreadyWritten > properties.getMaxBytes()) {
                throw new CaptureTooLargeException(properties.getMaxBytes());
            }
            out.write(buffer, 0, read);
        }
        return total;
    }

    private String newId(Instant at) {
        return ID_TIME.format(at) + "-" + Long.toHexString(ThreadLocalRandom.current().nextLong(1L << 32));
    }

    private void deleteQuietly(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            log.warn("could not clean up partial capture at {}", dir, e);
        }
    }
}
