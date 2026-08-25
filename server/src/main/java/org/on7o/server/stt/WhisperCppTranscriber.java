package org.on7o.server.stt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Speech-to-text through a local <a href="https://github.com/ggml-org/whisper.cpp">
 * whisper.cpp</a> server.
 *
 * <p>Runs entirely on this machine: captured thoughts are personal by nature
 * and never leave the LAN.
 */
@Service
public class WhisperCppTranscriber implements Transcriber {

    private static final Logger log = LoggerFactory.getLogger(WhisperCppTranscriber.class);
    private static final String ENGINE = "whisper.cpp";

    private final TranscriptionProperties properties;
    private final RestClient client;

    public WhisperCppTranscriber(TranscriptionProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, 5000));
        factory.setReadTimeout((int) Math.min(Integer.MAX_VALUE, properties.getTimeout().toMillis()));

        this.client = RestClient.builder()
                .baseUrl(properties.getUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public Transcription transcribe(Path audio, String thoughtId) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new FileSystemResource(audio));
        parts.add("response_format", "json");
        parts.add("language", properties.getLanguage());
        parts.add("temperature", String.valueOf(properties.getTemperature()));

        long start = System.currentTimeMillis();
        try {
            InferenceResponse response = client.post()
                    .uri("/inference")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(InferenceResponse.class);

            long latencyMs = System.currentTimeMillis() - start;
            if (response == null || response.text() == null) {
                throw new TranscriptionException("whisper.cpp returned an empty response");
            }

            String text = response.text().trim();
            log.info("transcribed {} in {} ms: \"{}\"", thoughtId, latencyMs, text);

            return new Transcription(
                    thoughtId,
                    text,
                    properties.getLanguage(),
                    ENGINE,
                    response.model() != null ? response.model() : properties.getModel(),
                    Instant.now(),
                    latencyMs);
        } catch (TranscriptionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TranscriptionException(
                    "whisper.cpp at " + properties.getUrl() + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            client.get().uri("/").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            log.debug("whisper.cpp not reachable at {}", properties.getUrl(), e);
            return false;
        }
    }

    /** whisper.cpp answers {@code response_format=json} with a single text field. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InferenceResponse(String text, String model) {
    }
}
