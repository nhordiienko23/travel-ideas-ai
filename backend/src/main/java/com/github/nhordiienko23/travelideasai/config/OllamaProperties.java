package com.github.nhordiienko23.travelideasai.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.ai.ollama")
public record OllamaProperties(

        @NotBlank(message = "Ollama base URL must not be blank.")
        String baseUrl,

        @NotBlank(message = "Ollama model name must not be blank.")
        String model,

        @NotNull(message = "Ollama connect timeout is required.")
        Duration connectTimeout,

        @NotNull(message = "Ollama read timeout is required.")
        Duration readTimeout

) {
    public OllamaProperties {
        if (connectTimeout != null
                && (connectTimeout.isZero() || connectTimeout.isNegative())) {
            throw new IllegalArgumentException(
                    "Ollama connect timeout must be positive."
            );
        }

        if (readTimeout != null
                && (readTimeout.isZero() || readTimeout.isNegative())) {
            throw new IllegalArgumentException(
                    "Ollama read timeout must be positive."
            );
        }
    }
}