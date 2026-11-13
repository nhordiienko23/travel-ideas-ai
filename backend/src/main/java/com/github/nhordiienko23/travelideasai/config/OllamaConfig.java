package com.github.nhordiienko23.travelideasai.config;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfig {

    @Bean
    public ChatModel ollamaChatModel(OllamaProperties properties) {
        var httpClientBuilder = JdkHttpClient.builder()
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.readTimeout());

        return OllamaChatModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(properties.model())
                .temperature(0.0)
                .httpClientBuilder(httpClientBuilder)
                .build();
    }
}