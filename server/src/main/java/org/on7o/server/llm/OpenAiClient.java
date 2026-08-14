package org.on7o.server.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.on7o.server.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Thin wrapper around the OpenAI chat completions API.
 *
 * <p>Uses Spring's {@link RestClient} with no additional SDK so the
 * dependency surface stays minimal. Authentication is via the Bearer
 * token read from {@link OpenAiProperties}.
 */
@Service
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final OpenAiProperties properties;
    private final RestClient client;

    public OpenAiClient(OpenAiProperties properties) {
        this.properties = properties;
        this.client = RestClient.builder()
                .baseUrl(Constants.OPENAI_API_URL)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    /**
     * Sends a single system + user turn to the configured model and returns
     * the assistant's reply as plain text.
     *
     * @param systemPrompt the system instruction
     * @param userContent  the user message
     * @return the raw text content of the first choice
     * @throws InterpretationException if the call fails or the response is empty
     */
    public String complete(String systemPrompt, String userContent) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new InterpretationException(
                    "OPENAI_API_KEY is not set. Export it before starting the server.");
        }

        ChatRequest request = new ChatRequest(
                properties.getModel(),
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userContent)
                )
        );

        log.debug("calling OpenAI model={} system_len={} user_len={}",
                properties.getModel(), systemPrompt.length(), userContent.length());

        try {
            ChatResponse response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null
                    || response.choices() == null
                    || response.choices().isEmpty()
                    || response.choices().get(0).message() == null) {
                throw new InterpretationException("OpenAI returned an empty response");
            }

            String content = response.choices().get(0).message().content();
            log.debug("OpenAI replied with {} chars", content == null ? 0 : content.length());
            return content == null ? "" : content.trim();

        } catch (InterpretationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InterpretationException("OpenAI API call failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal JSON structures
    // -------------------------------------------------------------------------

    private record Message(String role, String content) {}

    private record ChatRequest(
            String model,
            List<Message> messages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(
            List<Choice> choices,
            @JsonProperty("usage") Object usage) {}
}
