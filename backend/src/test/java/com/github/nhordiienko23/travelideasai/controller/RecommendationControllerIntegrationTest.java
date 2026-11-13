package com.github.nhordiienko23.travelideasai.controller;

import com.github.nhordiienko23.travelideasai.dto.MatchScoreResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationBatchResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationResponse;
import com.github.nhordiienko23.travelideasai.exception.GlobalExceptionHandler;
import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import com.github.nhordiienko23.travelideasai.model.Interest;
import com.github.nhordiienko23.travelideasai.service.TravelRecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("Recommendation controller integration")
class RecommendationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelRecommendationService travelRecommendationService;

    @Test
    @DisplayName("Returns recommendations for a valid request")
    void returnsRecommendationsForValidRequest() throws Exception {
        var response = new RecommendationBatchResponse(List.of(
                recommendation("Rome", "Italy"),
                recommendation("Lisbon", "Portugal"),
                recommendation("Prague", "Czechia")
        ));

        when(travelRecommendationService.generateRecommendations(any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departureCity": "Kyiv",
                                  "interests": ["HISTORY", "FOOD"],
                                  "budget": 1500,
                                  "currency": "EUR",
                                  "durationDays": 7,
                                  "preferredTransport": "PLANE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.recommendations.length()").value(3))
                .andExpect(jsonPath("$.recommendations[0].destination").value("Rome"))
                .andExpect(jsonPath("$.recommendations[0].country").value("Italy"))
                .andExpect(jsonPath("$.recommendations[0].currency").value("EUR"))
                .andExpect(jsonPath("$.recommendations[0].matchScore.totalScore").value(100));

        verify(travelRecommendationService).generateRecommendations(any());
    }

    @Test
    @DisplayName("Returns validation errors and does not call the service for an invalid request")
    void rejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departureCity": "Kyiv",
                                  "interests": ["HISTORY"],
                                  "budget": 0,
                                  "currency": "EUR",
                                  "durationDays": 7,
                                  "preferredTransport": "PLANE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.budget").isArray());

        verifyNoInteractions(travelRecommendationService);
    }

    @Test
    @DisplayName("Returns bad request for malformed JSON")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departureCity": "Kyiv",
                                  "budget":
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request body"));

        verifyNoInteractions(travelRecommendationService);
    }

    @Test
    @DisplayName("Returns bad request for an invalid enum value")
    void rejectsInvalidEnumValue() throws Exception {
        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departureCity": "Kyiv",
                                  "interests": ["HISTORY"],
                                  "budget": 1500,
                                  "currency": "GBP",
                                  "durationDays": 7,
                                  "preferredTransport": "PLANE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request body"));

        verifyNoInteractions(travelRecommendationService);
    }

    private RecommendationResponse recommendation(
            String destination,
            String country
    ) {
        return new RecommendationResponse(
                destination,
                country,
                "A city break destination.",
                "Matches your interests.",
                List.of(Interest.HISTORY, Interest.FOOD),
                new BigDecimal("1200.00"),
                CurrencyCode.EUR,
                7,
                new MatchScoreResponse(50, 30, 20, 100)
        );
    }
}
