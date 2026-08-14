package org.on7o.server.llm;

import org.on7o.server.Constants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds OpenAI configuration from {@code application.yml}.
 *
 * <p>The API key and model are read from environment variables via
 * Spring's {@code ${VAR:default}} syntax so that the key never
 * appears in source control.
 */
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private String apiKey = "";
    private String model = Constants.DEFAULT_MODEL;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
