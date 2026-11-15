package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.dto.AiRecommendationBatch;
import com.github.nhordiienko23.travelideasai.dto.RecommendationRequest;
import com.github.nhordiienko23.travelideasai.exception.AiServiceUnavailableException;
import com.github.nhordiienko23.travelideasai.exception.InvalidAiResponseException;
import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import com.github.nhordiienko23.travelideasai.model.Interest;
import com.github.nhordiienko23.travelideasai.model.TransportMode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Travel Recommendation Assistant Tests")
class TravelRecommendationAssistantTest {

    @Mock
    private ChatModel chatModel;

    private TravelRecommendationAssistant assistant;

    private static final String VALID_JSON = """
            {
              "recommendations": [
                {
                  "destination": "Prague",
                  "country": "Czech Republic",
                  "description": "A historic city with beautiful architecture.",
                  "whyRecommended": "Prague matches history and food.",
                  "interestTags": ["HISTORY", "FOOD"],
                  "estimatedTotalCost": 450.00,
                  "suggestedDurationDays": 3
                },
                {
                  "destination": "Vienna",
                  "country": "Austria",
                  "description": "A city with museums and cultural attractions.",
                  "whyRecommended": "Vienna matches history and culture.",
                  "interestTags": ["HISTORY", "CULTURE"],
                  "estimatedTotalCost": 480.00,
                  "suggestedDurationDays": 4
                },
                {
                  "destination": "Krakow",
                  "country": "Poland",
                  "description": "A historic city with a charming old town.",
                  "whyRecommended": "Krakow has no confirmed interest match.",
                  "interestTags": [],
                  "estimatedTotalCost": 250.00,
                  "suggestedDurationDays": 2
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        assistant = new TravelRecommendationAssistant(
                chatModel,
                JsonMapper.builder().build()
        );
    }

    // --------------------------------------------------
    // Successful response
    // --------------------------------------------------

    @Test
    @DisplayName("Should generate exactly three valid recommendations")
    void shouldGenerateThreeValidRecommendations() {
        mockChatResponse(VALID_JSON);

        AiRecommendationBatch batch =
                assistant.generateRecommendations(validRequest());

        assertNotNull(batch);
        assertNotNull(batch.recommendations());
        assertEquals(3, batch.recommendations().size());

        assertEquals("Prague", batch.recommendations().get(0).destination());
        assertEquals("Czech Republic", batch.recommendations().get(0).country());

        assertEquals(
                List.of(Interest.HISTORY, Interest.FOOD),
                batch.recommendations().get(0).interestTags()
        );

        assertEquals(
                0,
                new BigDecimal("450.00").compareTo(
                        batch.recommendations().get(0).estimatedTotalCost()
                )
        );

        assertEquals(
                3,
                batch.recommendations().get(0).suggestedDurationDays()
        );

        assertEquals("Vienna", batch.recommendations().get(1).destination());
        assertEquals("Krakow", batch.recommendations().get(2).destination());

        assertTrue(
                batch.recommendations().get(2).interestTags().isEmpty()
        );

        verify(chatModel, times(1)).chat(any(UserMessage.class));
    }

    @Test
    @DisplayName("Should accept an empty interest tags array")
    void shouldAcceptEmptyInterestTags() {
        mockChatResponse(VALID_JSON);

        AiRecommendationBatch batch =
                assistant.generateRecommendations(validRequest());

        assertTrue(
                batch.recommendations()
                        .get(2)
                        .interestTags()
                        .isEmpty()
        );

        verify(chatModel, times(1)).chat(any(UserMessage.class));
    }

    // --------------------------------------------------
    // Prompt construction
    // --------------------------------------------------

    @Test
    @DisplayName("Should include normalized user preferences in the prompt")
    void shouldBuildPromptWithNormalizedUserPreferences() {
        RecommendationRequest request = new RecommendationRequest(
                "  Warsaw  ",
                List.of(Interest.NATURE, Interest.FOOD),
                new BigDecimal("750.50"),
                CurrencyCode.EUR,
                5,
                TransportMode.TRAIN
        );

        mockChatResponse(VALID_JSON);

        assistant.generateRecommendations(request);

        ArgumentCaptor<UserMessage> captor =
                ArgumentCaptor.forClass(UserMessage.class);

        verify(chatModel).chat(captor.capture());

        String prompt = captor.getValue().singleText();

        assertTrue(prompt.contains("Departure city: Warsaw"));
        assertTrue(prompt.contains("Interests: NATURE, FOOD"));
        assertTrue(prompt.contains("Budget: 750.50 EUR"));
        assertTrue(prompt.contains("Available duration: 5 days"));
        assertTrue(prompt.contains("Preferred transport: TRAIN"));

        assertTrue(prompt.contains("exactly THREE"));
        assertTrue(prompt.contains("TOTAL cost"));
        assertTrue(prompt.contains("ONE person"));
        assertTrue(prompt.contains("ENTIRE trip"));
        assertTrue(prompt.contains("Prices are estimates only"));

        assertTrue(prompt.contains("An empty interestTags array is VALID."));
        assertTrue(prompt.contains(
                "The whyRecommended field must explain only interests"
        ));
        assertTrue(prompt.contains("nearest multiple of 5"));
        assertTrue(prompt.contains("The application calculates Smart Trip Match scores separately."));

        assertTrue(prompt.contains("NATURE"));
        assertTrue(prompt.contains("HISTORY"));
        assertTrue(prompt.contains("FOOD"));
        assertTrue(prompt.contains("CULTURE"));
        assertTrue(prompt.contains("BEACH"));
        assertTrue(prompt.contains("ADVENTURE"));
        assertTrue(prompt.contains("ARCHITECTURE"));
        assertTrue(prompt.contains("SHOPPING"));
    }

    @Test
    @DisplayName("Should reject null request")
    void shouldRejectNullRequest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> assistant.generateRecommendations(null)
        );

        verifyNoInteractions(chatModel);
    }

    // --------------------------------------------------
    // Ollama communication failures
    // --------------------------------------------------

    @Test
    @DisplayName("Should convert communication failures to AiServiceUnavailableException")
    void shouldHandleModelCommunicationFailure() {
        RuntimeException cause =
                new RuntimeException("Connection refused");

        when(chatModel.chat(any(UserMessage.class)))
                .thenThrow(cause);

        AiServiceUnavailableException exception = assertThrows(
                AiServiceUnavailableException.class,
                () -> assistant.generateRecommendations(validRequest())
        );

        assertEquals(
                "Ollama service is currently unavailable.",
                exception.getMessage()
        );

        assertSame(cause, exception.getCause());

        verify(chatModel, times(1)).chat(any(UserMessage.class));
    }

    // --------------------------------------------------
    // Empty model responses
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject null ChatResponse")
    void shouldRejectNullChatResponse() {
        doReturn(null)
                .when(chatModel)
                .chat(any(UserMessage.class));

        assertThrows(
                InvalidAiResponseException.class,
                () -> assistant.generateRecommendations(validRequest())
        );

        verify(chatModel, times(1)).chat(any(UserMessage.class));
    }

    @Test
    @DisplayName("Should reject ChatResponse without AiMessage")
    void shouldRejectMissingAiMessage() {
        ChatResponse response = mock(ChatResponse.class);

        doReturn(null).when(response).aiMessage();

        doReturn(response)
                .when(chatModel)
                .chat(any(UserMessage.class));

        assertThrows(
                InvalidAiResponseException.class,
                () -> assistant.generateRecommendations(validRequest())
        );

        verify(chatModel, times(1)).chat(any(UserMessage.class));
    }

    @Test
    @DisplayName("Should reject null or blank AI response text")
    void shouldRejectNullOrBlankResponseText() {
        assertInvalidResponseText(null);
        assertInvalidResponseText("");
        assertInvalidResponseText(" ");
        assertInvalidResponseText("\n\t");
    }

    // --------------------------------------------------
    // JSON extraction
    // --------------------------------------------------

    @Test
    @DisplayName("Should extract JSON from Markdown fences and surrounding text")
    void shouldExtractJsonFromSurroundingText() {
        String responseText = """
                Here is the generated JSON:

                ```json
                %s
                ```

                End of response.
                """.formatted(VALID_JSON);

        mockChatResponse(responseText);

        AiRecommendationBatch batch =
                assistant.generateRecommendations(validRequest());

        assertNotNull(batch);
        assertEquals(3, batch.recommendations().size());

        verify(chatModel, times(1)).chat(any(UserMessage.class));
    }

    @Test
    @DisplayName("Should reject malformed JSON")
    void shouldRejectMalformedJson() {
        assertInvalidJson("{ invalid json");
        assertInvalidJson("{\"recommendations\": [");
    }

    @Test
    @DisplayName("Should reject JSON null as the entire response")
    void shouldRejectNullJsonRoot() {
        assertInvalidJson("null");
    }

    @Test
    @DisplayName("Should reject a response without a JSON object")
    void shouldRejectResponseWithoutJsonObject() {
        assertInvalidJson("No recommendations available.");
    }

    // --------------------------------------------------
    // Invalid root structure
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject JSON without recommendations")
    void shouldRejectMissingRecommendationsList() {
        assertInvalidJson("{}");
    }

    @Test
    @DisplayName("Should reject null recommendations list")
    void shouldRejectNullRecommendationsList() {
        assertInvalidJson("""
                {
                  "recommendations": null
                }
                """);
    }

    @Test
    @DisplayName("Should reject recommendations when not a JSON array")
    void shouldRejectRecommendationsWithWrongType() {
        assertInvalidJson("""
                {
                  "recommendations": "Prague, Vienna, Krakow"
                }
                """);
    }

    @Test
    @DisplayName("Should reject an incompatible root structure")
    void shouldRejectWrongRootStructure() {
        assertInvalidJson("""
                [
                  {"destination": "Prague"}
                ]
                """);
    }

    // --------------------------------------------------
    // Recommendation count
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject an empty recommendations array")
    void shouldRejectEmptyRecommendations() {
        assertInvalidJson("""
                {
                  "recommendations": []
                }
                """);
    }

    @Test
    @DisplayName("Should reject fewer than three recommendations")
    void shouldRejectTooFewRecommendations() {
        String json = """
                {
                  "recommendations": [
                    {
                      "destination": "Prague",
                      "country": "Czech Republic",
                      "description": "A historic city.",
                      "whyRecommended": "A historic destination.",
                      "interestTags": [],
                      "estimatedTotalCost": 450,
                      "suggestedDurationDays": 3
                    },
                    {
                      "destination": "Vienna",
                      "country": "Austria",
                      "description": "A cultural city.",
                      "whyRecommended": "A cultural destination.",
                      "interestTags": [],
                      "estimatedTotalCost": 480,
                      "suggestedDurationDays": 3
                    }
                  ]
                }
                """;

        assertInvalidJson(json);
    }

    @Test
    @DisplayName("Should reject more than three recommendations")
    void shouldRejectTooManyRecommendations() {
        String fourthRecommendation = """
                ,
                {
                  "destination": "Brno",
                  "country": "Czech Republic",
                  "description": "A city with historic architecture.",
                  "whyRecommended": "A historic destination.",
                  "interestTags": [],
                  "estimatedTotalCost": 300,
                  "suggestedDurationDays": 2
                }
                """;

        String json = VALID_JSON.replace(
                """
                      ]
                    }
                    """,
                fourthRecommendation + """
                      ]
                    }
                    """
        );

        assertInvalidJson(json);
    }

    // --------------------------------------------------
    // Enum values
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject unknown Interest enum values")
    void shouldRejectUnknownInterestEnumValue() {
        String invalidJson = VALID_JSON.replace(
                "\"HISTORY\"",
                "\"HIKING\""
        );

        assertInvalidJson(invalidJson);
    }

    @Test
    @DisplayName("Should reject numeric Interest enum values")
    void shouldRejectNumericInterestEnumValue() {
        String invalidJson = VALID_JSON.replace(
                "\"HISTORY\"",
                "0"
        );

        assertInvalidJson(invalidJson);
    }

    // --------------------------------------------------
    // Incompatible field types
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject estimatedTotalCost with an incompatible type")
    void shouldRejectInvalidEstimatedTotalCostType() {
        String invalidJson = VALID_JSON.replace(
                "\"estimatedTotalCost\": 450.00",
                "\"estimatedTotalCost\": \"expensive\""
        );

        assertInvalidJson(invalidJson);
    }

    @Test
    @DisplayName("Should reject destination with an incompatible type")
    void shouldRejectInvalidDestinationType() {
        String invalidJson = VALID_JSON.replace(
                "\"destination\": \"Prague\"",
                "\"destination\": {\"name\": \"Prague\"}"
        );

        assertInvalidJson(invalidJson);
    }

    // --------------------------------------------------
    // Unknown JSON properties
    // --------------------------------------------------

    @Test
    @DisplayName("Should ignore unknown properties inside recommendations")
    void shouldIgnoreUnknownRecommendationFields() {
        String json = VALID_JSON.replace(
                "\"whyRecommended\": \"Prague matches history and food.\",",
                """
                "whyRecommended": "Prague matches history and food.",
                "unexpectedField": "unexpected value",
                "score": 95,
                "interestsScore": 50,
                """
        );

        mockChatResponse(json);

        AiRecommendationBatch batch =
                assistant.generateRecommendations(validRequest());

        assertEquals(3, batch.recommendations().size());
        assertEquals("Prague", batch.recommendations().get(0).destination());

        verify(chatModel, times(1)).chat(any(UserMessage.class));
    }

    @Test
    @DisplayName("Should ignore unknown properties at the root level")
    void shouldIgnoreUnknownRootFields() {
        String json = VALID_JSON.substring(
                0,
                VALID_JSON.lastIndexOf('}')
        ) + """
                ,
                  "estimatedTotalCost": 123.45
                }
                """;

        mockChatResponse(json);

        AiRecommendationBatch batch =
                assistant.generateRecommendations(validRequest());

        assertEquals(3, batch.recommendations().size());

        verify(chatModel, times(1)).chat(any(UserMessage.class));
    }

    // --------------------------------------------------
    // Suggested duration validation
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject a suggested duration of zero")
    void shouldRejectZeroSuggestedDuration() {
        String invalidJson = VALID_JSON.replace(
                "\"suggestedDurationDays\": 3",
                "\"suggestedDurationDays\": 0"
        );

        assertInvalidJson(invalidJson);
    }

    @Test
    @DisplayName("Should reject a negative suggested duration")
    void shouldRejectNegativeSuggestedDuration() {
        String invalidJson = VALID_JSON.replace(
                "\"suggestedDurationDays\": 3",
                "\"suggestedDurationDays\": -1"
        );

        assertInvalidJson(invalidJson);
    }

    @Test
    @DisplayName("Should reject a suggested duration exceeding the user's available days")
    void shouldRejectDurationExceedingMaximum() {
        String invalidJson = VALID_JSON.replace(
                "\"suggestedDurationDays\": 3",
                "\"suggestedDurationDays\": 6"
        );

        assertInvalidJson(invalidJson);
    }

    @Test
    @DisplayName("Should reject a missing suggested duration")
    void shouldRejectMissingSuggestedDuration() {
        String invalidJson = VALID_JSON.replaceAll(
                "\"estimatedTotalCost\": 450\\.00,\\s*"
                        + "\"suggestedDurationDays\": 3",
                "\"estimatedTotalCost\": 450.00"
        );

        assertNotEquals(VALID_JSON, invalidJson);
        assertFalse(invalidJson.contains("\"suggestedDurationDays\": 3"));

        assertInvalidJson(invalidJson);
    }

    @Test
    @DisplayName("Should retry generation and accept a corrected duration")
    void shouldRetryAfterInvalidDurationAndAcceptCorrectedResponse() {
        String invalidJson = VALID_JSON.replace(
                "\"suggestedDurationDays\": 3",
                "\"suggestedDurationDays\": 6"
        );

        when(chatModel.chat(any(UserMessage.class)))
                .thenReturn(
                        chatResponse(invalidJson),
                        chatResponse(VALID_JSON)
                );

        AiRecommendationBatch batch =
                assistant.generateRecommendations(validRequest());

        assertNotNull(batch);
        assertEquals(3, batch.recommendations().size());

        ArgumentCaptor<UserMessage> captor =
                ArgumentCaptor.forClass(UserMessage.class);

        verify(chatModel, times(2)).chat(captor.capture());

        String retryPrompt = captor.getAllValues()
                .get(1)
                .singleText();

        assertTrue(retryPrompt.contains("CRITICAL CORRECTION"));
        assertTrue(retryPrompt.contains("LESS THAN OR EQUAL TO 5"));
    }

    @Test
    @DisplayName("Should fail after retry when the suggested duration remains invalid")
    void shouldFailWhenDurationRemainsInvalidAfterRetry() {
        String invalidJson = VALID_JSON.replace(
                "\"suggestedDurationDays\": 3",
                "\"suggestedDurationDays\": 6"
        );

        when(chatModel.chat(any(UserMessage.class)))
                .thenReturn(
                        chatResponse(invalidJson),
                        chatResponse(invalidJson)
                );

        assertThrows(
                InvalidAiResponseException.class,
                () -> assistant.generateRecommendations(validRequest())
        );

        verify(chatModel, times(2)).chat(any(UserMessage.class));
    }

    // --------------------------------------------------
    // Test helpers
    // --------------------------------------------------

    private RecommendationRequest validRequest() {
        return new RecommendationRequest(
                "Warsaw",
                List.of(Interest.HISTORY, Interest.FOOD),
                new BigDecimal("500.00"),
                CurrencyCode.EUR,
                5,
                TransportMode.TRAIN
        );
    }

    private ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }

    private void mockChatResponse(String json) {
        reset(chatModel);

        ChatResponse response = chatResponse(json);

        doReturn(response)
                .when(chatModel)
                .chat(any(UserMessage.class));
    }

    private void assertInvalidJson(String json) {
        mockChatResponse(json);

        assertThrows(
                InvalidAiResponseException.class,
                () -> assistant.generateRecommendations(validRequest())
        );

        // Invalid AI output is retried once.
        verify(chatModel, times(2)).chat(any(UserMessage.class));
    }

    private void assertInvalidResponseText(String text) {
        AiMessage aiMessage = mock(AiMessage.class);
        doReturn(text).when(aiMessage).text();

        ChatResponse response = mock(ChatResponse.class);
        doReturn(aiMessage).when(response).aiMessage();

        reset(chatModel);

        doReturn(response)
                .when(chatModel)
                .chat(any(UserMessage.class));

        assertThrows(
                InvalidAiResponseException.class,
                () -> assistant.generateRecommendations(validRequest())
        );

        verify(chatModel, times(2)).chat(any(UserMessage.class));
    }
}
