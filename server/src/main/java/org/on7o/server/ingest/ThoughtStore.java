package org.on7o.server.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.nio.file.StandardOpenOption;
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
    public static final String RTHOUGHT_FILE = "rthought.ttl";
    public static final String QTHOUGHT_FILE = "qthought.ttl";
    public static final String CTHOUGHT_FILE = "cthought.ttl";
    public static final String QUESTIONS_FILE = "questions.json";
    public static final String ANSWERS_FILE = "answers.json";
    public static final String ENTITY_CONTEXT_FILE = "entity-context.txt";
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
                    remoteAddress,
                    null,
                    null,
                    Thought.SOURCE_AUDIO);

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dir.resolve(META_FILE).toFile(), thought);
            return thought;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(dir);
            throw e;
        }
    }

    /**
     * Creates a thought whose content arrived as plain text rather than as audio.
     *
     * <p>Only the metadata file is written: there is no audio to store, and the
     * transcription is saved separately by the caller so that a text thought
     * looks exactly like a transcribed capture to every downstream stage.
     *
     * @param capturedAt    when the thought happened in the world, as stated by the caller
     * @param source        origin label, for example {@link Thought#SOURCE_SYNTHETIC}
     * @param remoteAddress address the request came from, or null
     * @return the newly created thought
     */
    public Thought createTextThought(Instant capturedAt, String source, String remoteAddress)
            throws IOException {

        Instant receivedAt = Instant.now();
        String id = newId(receivedAt);
        Path dir = root.resolve(id);
        Files.createDirectories(dir);

        try {
            Thought thought = new Thought(id, null, capturedAt, receivedAt, null,
                    0, 0, 0, 0, 0, 0, remoteAddress, null, null, source);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dir.resolve(META_FILE).toFile(), thought);
            return thought;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(dir);
            throw e;
        }
    }

    /**
     * Creates a new top-level thought derived from a single entity found in
     * another thought's cThought, rather than from captured audio.
     *
     * @param parentId    id of the thought whose cThought the entity came from
     * @param entityLabel the entity's short label
     * @param context     supporting triples or tooltip text describing the entity's usage
     * @return the newly created thought
     */
    public Thought createDerivedThought(String parentId, String entityLabel, String context) throws IOException {
        Instant now = Instant.now();
        String id = newId(now);
        Path dir = root.resolve(id);
        Files.createDirectories(dir);

        Thought thought = new Thought(id, null, now, now, null, 0, 0, 0, 0, 0, 0, null,
                parentId, entityLabel, Thought.SOURCE_DERIVED);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(dir.resolve(META_FILE).toFile(), thought);
        Files.writeString(dir.resolve(ENTITY_CONTEXT_FILE), context == null ? "" : context);
        return thought;
    }

    /** Loads the supporting context saved when a derived thought was created. */
    public Optional<String> findEntityContext(String id) {
        return readText(resolveSafely(id).resolve(ENTITY_CONTEXT_FILE));
    }

    /** All thoughts previously derived from the given parent, in no particular order. */
    public List<Thought> findDerivedThoughts(String parentId) throws IOException {
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .map(this::find)
                    .flatMap(Optional::stream)
                    .filter(t -> parentId.equals(t.parentId()))
                    .toList();
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

    /**
     * Location of a capture's audio file.
     *
     * @throws IllegalArgumentException when the thought carries no audio, as is
     *         the case for derived and text thoughts
     */
    public Path audioPath(Thought thought) {
        if (thought.audioFile() == null) {
            throw new IllegalArgumentException("thought has no audio: " + thought.id());
        }
        return resolveSafely(thought.id()).resolve(thought.audioFile());
    }

    /** Saves the raw ontology (stage 1 output) for a thought. */
    public void saveRawThought(String id, String turtle) throws IOException {
        Files.writeString(resolveSafely(id).resolve(RTHOUGHT_FILE), turtle);
    }

    /** Loads the raw ontology, if it exists. */
    public Optional<String> findRawThought(String id) {
        return readText(resolveSafely(id).resolve(RTHOUGHT_FILE));
    }

    /** Saves the questioned ontology Turtle (stage 2). */
    public void saveQuestionsThought(String id, String turtle) throws IOException {
        Files.writeString(resolveSafely(id).resolve(QTHOUGHT_FILE), turtle);
    }

    /** Loads the questioned ontology Turtle, if it exists. */
    public Optional<String> findQuestionsThought(String id) {
        return readText(resolveSafely(id).resolve(QTHOUGHT_FILE));
    }

    /**
     * Writes an arbitrary artifact as JSON inside a thought's directory.
     *
     * <p>Offered as a primitive so that clarification, and later HCIN, can own
     * the shape of what they persist while path safety stays in one place here.
     *
     * @param id       thought id
     * @param filename name of the file inside the thought directory
     * @param value    the object to serialize
     */
    public void saveJson(String id, String filename, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(resolveSafely(id).resolve(filename).toFile(), value);
    }

    /**
     * Reads a JSON artifact from a thought's directory.
     *
     * @param id       thought id
     * @param filename name of the file inside the thought directory
     * @param type     the shape to read it back as
     * @return the parsed value, or empty when the file is absent or unreadable
     */
    public <T> Optional<T> readJson(String id, String filename, TypeReference<T> type) {
        Path file = resolveSafely(id).resolve(filename);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), type));
        } catch (IOException e) {
            log.warn("unreadable {} for thought {}", filename, id, e);
            return Optional.empty();
        }
    }

    /** Saves the consolidated ontology (stage 3 output). */
    public void saveConsolidatedThought(String id, String turtle) throws IOException {
        Files.writeString(resolveSafely(id).resolve(CTHOUGHT_FILE), turtle);
    }

    /** Loads the consolidated ontology, if it exists. */
    public Optional<String> findConsolidatedThought(String id) {
        return readText(resolveSafely(id).resolve(CTHOUGHT_FILE));
    }

    /**
     * Appends a directly user-asserted {@code rdf:type} triple to an existing
     * cThought, in the same handwritten RDF-star style the LLM stages use.
     * A manual assertion is not inferred, so it is always tagged Asserted
     * with full confidence.
     *
     * @param id         thought id
     * @param individual local name of the individual, under the {@code on7o:} prefix
     * @param type       local name of the class it is additionally typed as
     */
    public void appendConsolidatedType(String id, String individual, String type) throws IOException {
        Path file = resolveSafely(id).resolve(CTHOUGHT_FILE);
        String addition = "\non7o:" + individual + " rdf:type on7o:" + type + " .\n"
                + "<< on7o:" + individual + " rdf:type on7o:" + type + " >>\n"
                + "        on7o:knowledgeStatus on7o:Asserted ;\n"
                + "        on7o:confidence      1.0 .\n";
        Files.writeString(file, addition, StandardOpenOption.APPEND);
    }

    /** Returns true when the given filename exists inside the thought directory. */
    public boolean hasFile(String id, String filename) {
        return Files.isRegularFile(resolveSafely(id).resolve(filename));
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

    private Optional<String> readText(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path));
        } catch (IOException e) {
            log.warn("unreadable file {}", path, e);
            return Optional.empty();
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
