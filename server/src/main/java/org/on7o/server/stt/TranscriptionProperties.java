package org.on7o.server.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuration for the speech-to-text stage. */
@ConfigurationProperties(prefix = "on7o.stt")
public class TranscriptionProperties {

    /** When false, captures are stored without being transcribed. */
    private boolean enabled = true;

    /** Base URL of a whisper.cpp server. */
    private String url = "http://127.0.0.1:8090";

    /**
     * Which model whisper.cpp was started with. Recorded with every
     * transcription: knowing what produced a reading is what makes it possible
     * to reinterpret old thoughts when a better model arrives.
     */
    private String model = "unknown";

    /** Spoken language hint. "pt" keeps Whisper from guessing Spanish on short clips. */
    private String language = "pt";

    /** Greedy decoding. Raising this trades transcription stability for variety. */
    private double temperature = 0.0;

    /**
     * How long to wait for a transcription.
     *
     * Whisper on CPU runs several times slower than real time, so this has to be
     * generous: a minute of speech can take several minutes to transcribe.
     */
    private Duration timeout = Duration.ofMinutes(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
