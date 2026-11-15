
package com.github.nhordiienko23.travelideasai.config;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfig{

    private static final ResponseFormat RECOMMENDATION_RESPONSE_FORMAT =
            createRecommendationResponseFormat();

    @Bean
    public ChatModel chatModel(OllamaProperties properties) {

        JdkHttpClientBuilder httpClientBuilder = JdkHttpClient.builder()
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.readTimeout());

        return OllamaChatModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(properties.model())
                .httpClientBuilder(httpClientBuilder)
                .responseFormat(RECOMMENDATION_RESPONSE_FORMAT)
                .temperature(0.2)
                .timeout(properties.readTimeout())
                .maxRetries(0)
                .numPredict(1400)
                .build();
    }

    private static ResponseFormat createRecommendationResponseFormat() {

        JsonObjectSchema recommendationSchema =
                JsonObjectSchema.builder()
                        .addStringProperty("destination")
                        .addStringProperty("country")
                        .addStringProperty("description")
                        .addStringProperty("whyRecommended")
                        .addProperty(
                                "interestTags",
                                JsonArraySchema.builder()
                                        .items(
                                                JsonEnumSchema.builder()
                                                        .enumValues(
                                                                List.of(
                                                                        "NATURE",
                                                                        "HISTORY",
                                                                        "FOOD",
                                                                        "CULTURE",
                                                                        "BEACH",
                                                                        "ADVENTURE",
                                                                        "ARCHITECTURE",
                                                                        "SHOPPING"
                                                                )
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .addNumberProperty("estimatedTotalCost")
                        .addIntegerProperty("suggestedDurationDays")
                        .required(
                                "destination",
                                "country",
                                "description",
                                "whyRecommended",
                                "interestTags",
                                "estimatedTotalCost",
                                "suggestedDurationDays"
                        )
                        .build();

        JsonSchema jsonSchema = JsonSchema.builder()
                .name("AiRecommendationBatch")
                .rootElement(
                        JsonObjectSchema.builder()
                                .addProperty(
                                        "recommendations",
                                        JsonArraySchema.builder()
                                                .items(recommendationSchema)
                                                .build()
                                )
                                .required("recommendations")
                                .build()
                )
                .build();

        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(jsonSchema)
                .build();
    }
}
