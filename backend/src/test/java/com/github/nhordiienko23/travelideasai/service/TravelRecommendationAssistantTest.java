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
                  "whyRecommended": "Krakow matches history and architecture.",
                  "interestTags": ["HISTORY", "ARCHITECTURE"],
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
    @DisplayName("Should generate three valid recommendations")
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

        assertEquals(3, batch.recommendations().get(0).suggestedDurationDays());
        assertEquals("Vienna", batch.recommendations().get(1).destination());
        assertEquals("Krakow", batch.recommendations().get(2).destination());

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
        assertTrue(prompt.contains("Do not calculate or return any Smart Trip Match scores"));

        assertTrue(prompt.contains("NATURE"));
        assertTrue(prompt.contains("HISTORY"));
        assertTrue(prompt.contains("FOOD"));
        assertTrue(prompt.contains("CULTURE"));
        assertTrue(prompt.contains("BEACH"));
        assertTrue(prompt.contains("ADVENTURE"));
        assertTrue(prompt.contains("ARCHITECTURE"));
        assertTrue(prompt.contains("SHOPPING"));
    }

    // --------------------------------------------------
    // Invalid request
    // --------------------------------------------------

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
        RuntimeException cause = new RuntimeException("Connection refused");

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
        reset(chatModel);

        doReturn(null).when(chatModel).chat(any(UserMessage.class));

        assertThrows(
                InvalidAiResponseException.class,
                () -> assistant.generateRecommendations(validRequest())
        );
    }

    @Test
    @DisplayName("Should reject ChatResponse without AiMessage")
    void shouldRejectMissingAiMessage() {
        ChatResponse response = mock(ChatResponse.class);

        doReturn(null).when(response).aiMessage();

        reset(chatModel);
        doReturn(response).when(chatModel).chat(any(UserMessage.class));

        assertThrows(
                InvalidAiResponseException.class,
                () -> assistant.generateRecommendations(validRequest())
        );
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
    // Invalid JSON syntax and formatting
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject malformed JSON, Markdown fences and trailing text")
    void shouldRejectInvalidJsonFormatting() {
        assertInvalidJson("{ invalid json");

        assertInvalidJson("""
                ```json
                {"recommendations": []}
                ```
                """);

        assertInvalidJson("""
                {"recommendations": []} Extra explanation
                """);
    }

    @Test
    @DisplayName("Should reject JSON null as the entire response")
    void shouldRejectNullJsonRoot() {
        assertInvalidJson("null");
    }

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
    @DisplayName("Should reject incompatible root structure")
    void shouldRejectWrongRootStructure() {
        assertInvalidJson("""
                [
                  {"destination": "Prague"}
                ]
                """);
    }

    // --------------------------------------------------
    // Invalid enum values
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
    @DisplayName("Should reject estimatedTotalCost with incompatible type")
    void shouldRejectInvalidEstimatedTotalCostType() {
        String invalidJson = VALID_JSON.replace(
                "\"estimatedTotalCost\": 450.00",
                "\"estimatedTotalCost\": \"expensive\""
        );

        assertInvalidJson(invalidJson);
    }

    @Test
    @DisplayName("Should reject destination with incompatible type")
    void shouldRejectInvalidDestinationType() {
        String invalidJson = VALID_JSON.replace(
                "\"destination\": \"Prague\"",
                "\"destination\": {\"name\": \"Prague\"}"
        );

        assertInvalidJson(invalidJson);
    }

    // --------------------------------------------------
    // Unexpected fields
    // --------------------------------------------------

    @Test
    @DisplayName("Should reject unexpected fields")
    void shouldRejectUnexpectedFields() {
        String invalidJson = VALID_JSON.replace(
                "\"whyRecommended\": \"Prague matches history and food.\",",
                """
                "whyRecommended": "Prague matches history and food.",
                "unexpectedField": "unexpected value",
                """
        );

        assertInvalidJson(invalidJson);
    }

    @Test
    @DisplayName("Should reject Smart Trip Match score fields returned by AI")
    void shouldRejectScoreFieldsReturnedByAi() {
        String invalidJson = VALID_JSON.replace(
                "\"whyRecommended\": \"Prague matches history and food.\",",
                """
                "whyRecommended": "Prague matches history and food.",
                "score": 95,
                "interestsScore": 50,
                """
        );

        assertInvalidJson(invalidJson);
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

        if (json == null) {
            doReturn(null)
                    .when(chatModel)
                    .chat(any(UserMessage.class));
            return;
        }

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
    }
}
