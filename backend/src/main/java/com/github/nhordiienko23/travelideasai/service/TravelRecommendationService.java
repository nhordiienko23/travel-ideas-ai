
package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.dto.AiRecommendationBatch;
import com.github.nhordiienko23.travelideasai.dto.AiTravelRecommendation;
import com.github.nhordiienko23.travelideasai.dto.MatchScoreResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationBatchResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationRequest;
import com.github.nhordiienko23.travelideasai.dto.RecommendationResponse;
import com.github.nhordiienko23.travelideasai.exception.InvalidAiResponseException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TravelRecommendationService {

    private final TravelRecommendationAssistant assistant;
    private final MatchScoreService matchScoreService;
    private final Validator validator;

    public RecommendationBatchResponse generateRecommendations(
            RecommendationRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Recommendation request is required."
            );
        }

        AiRecommendationBatch batch =
                assistant.generateRecommendations(request);

        validateBatch(batch);

        List<RecommendationResponse> recommendations =
                new ArrayList<>(3);

        for (AiTravelRecommendation recommendation : batch.recommendations()) {

            MatchScoreResponse score =
                    matchScoreService.calculateScore(request, recommendation);

            recommendations.add(new RecommendationResponse(
                    recommendation.destination(),
                    recommendation.country(),
                    recommendation.description(),
                    recommendation.whyRecommended(),
                    matchScoreService.findMatchingInterests(
                            request,
                            recommendation
                    ),
                    recommendation.estimatedTotalCost(),
                    request.currency(),
                    recommendation.suggestedDurationDays(),
                    score
            ));
        }

        return new RecommendationBatchResponse(recommendations);
    }

    private void validateBatch(AiRecommendationBatch batch) {

        if (batch == null) {
            throw new InvalidAiResponseException(
                    "AI response must not be null."
            );
        }

        Set<ConstraintViolation<AiRecommendationBatch>> violations =
                validator.validate(batch);

        if (!violations.isEmpty()) {
            ConstraintViolation<AiRecommendationBatch> violation =
                    violations.iterator().next();

            throw new InvalidAiResponseException(
                    "AI response validation failed: "
                            + violation.getPropertyPath()
                            + " "
                            + violation.getMessage()
            );
        }

        validateUniqueDestinations(batch);
    }

    private void validateUniqueDestinations(AiRecommendationBatch batch) {

        Set<List<String>> destinations = new HashSet<>();

        for (AiTravelRecommendation recommendation :
                batch.recommendations()) {

            List<String> destinationKey = List.of(
                    recommendation.destination()
                            .strip()
                            .toLowerCase(Locale.ROOT),

                    recommendation.country()
                            .strip()
                            .toLowerCase(Locale.ROOT)
            );

            if (!destinations.add(destinationKey)) {
                throw new InvalidAiResponseException(
                        "AI recommendations must have unique "
                                + "destination and country pairs."
                );
            }
        }
    }
}
