package org.on7o.server.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.on7o.server.stt.Transcription;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that a text thought is stored as a first-class thought and is
 * indistinguishable from a transcribed capture to the stages downstream of it.
 */
class ThoughtServiceTest {

    private ThoughtStore store;
    private ThoughtService service;

    @BeforeEach
    void setUp(@TempDir Path root) throws IOException {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(root.toString());

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        store = new ThoughtStore(properties, mapper);
        service = new ThoughtService(store);
    }

    @Test
    void storesTextThoughtWithoutAudio() throws IOException {
        Instant capturedAt = OffsetDateTime.parse("2026-08-24T12:30:00-03:00").toInstant();

        Thought thought = service.ingestText("Almocei com o Bob hoje.", capturedAt,
                Thought.SOURCE_SYNTHETIC, "127.0.0.1");

        assertThat(thought.capturedAt()).isEqualTo(Instant.parse("2026-08-24T15:30:00Z"));
        assertThat(thought.source()).isEqualTo(Thought.SOURCE_SYNTHETIC);
        assertThat(thought.isAudio()).isFalse();
        assertThat(thought.isDerived()).isFalse();
        assertThat(thought.audioFile()).isNull();
        assertThat(thought.durationMs()).isZero();
        assertThat(Files.exists(store.root().resolve(thought.id()).resolve("audio.wav"))).isFalse();
    }

    @Test
    void writesTranscriptionTheInterpretationStagesCanRead() throws IOException {
        Thought thought = service.ingestText("Discutimos o contrato da ACME.",
                Instant.parse("2026-08-24T15:30:00Z"), Thought.SOURCE_SYNTHETIC, null);

        Optional<Transcription> transcription = store.findTranscription(thought.id());

        assertThat(transcription).isPresent();
        assertThat(transcription.get().thoughtId()).isEqualTo(thought.id());
        assertThat(transcription.get().text()).isEqualTo("Discutimos o contrato da ACME.");
        assertThat(transcription.get().engine()).isEqualTo(ThoughtService.TEXT_ENGINE);
    }

    @Test
    void textThoughtIsReadableThroughTheStoreAndListing() throws IOException {
        Thought thought = service.ingestText("Paguei a mensalidade.",
                Instant.parse("2026-08-24T15:30:00Z"), Thought.SOURCE_SYNTHETIC, null);

        assertThat(store.find(thought.id())).isPresent();
        assertThat(store.find(thought.id()).orElseThrow().capturedAt()).isEqualTo(thought.capturedAt());
        assertThat(store.list(10)).extracting(Thought::id).contains(thought.id());
    }

    @Test
    void rejectsAudioPathForAThoughtWithoutAudio() throws IOException {
        Thought thought = service.ingestText("Sem audio.", Instant.parse("2026-08-24T15:30:00Z"),
                Thought.SOURCE_SYNTHETIC, null);

        assertThatThrownBy(() -> store.audioPath(thought))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(thought.id());
    }

    @Test
    void metadataWrittenBeforeTheSourceFieldExistedReadsBackAsAudio() {
        Thought legacy = new Thought("20260813T205713Z-b12bd098", "m5",
                Instant.parse("2026-08-13T20:57:13Z"), Instant.parse("2026-08-13T20:57:14Z"),
                "audio.wav", 1024, 980, 30, 16000, 1, 16, "192.168.0.10", null, null, null);

        assertThat(legacy.source()).isEqualTo(Thought.SOURCE_AUDIO);
        assertThat(legacy.isAudio()).isTrue();
    }
}
