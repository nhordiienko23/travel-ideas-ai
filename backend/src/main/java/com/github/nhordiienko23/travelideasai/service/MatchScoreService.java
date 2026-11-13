package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.dto.AiTravelRecommendation;
import com.github.nhordiienko23.travelideasai.dto.MatchScoreResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationRequest;
import com.github.nhordiienko23.travelideasai.model.Interest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
@Service
public class MatchScoreService {

    public MatchScoreResponse calculateScore(
            RecommendationRequest request,
            AiTravelRecommendation recommendation
    ) {
        List<Interest> matchingInterests =
                findMatchingInterests(request, recommendation);

        int interestsScore = calculateInterestsScore(
                request.interests(), matchingInterests
        );
        int budgetScore = calculateBudgetScore(
                request.budget(), recommendation.estimatedTotalCost()
        );
        int durationScore = calculateDurationScore(
                request.durationDays(), recommendation.suggestedDurationDays()
        );

        return new MatchScoreResponse(
                interestsScore,
                budgetScore,
                durationScore,
                interestsScore + budgetScore + durationScore
        );
    }

    public List<Interest> findMatchingInterests(
            RecommendationRequest request,
            AiTravelRecommendation recommendation
    ) {
        if (request == null || recommendation == null) {
            throw new IllegalArgumentException(
                    "Request and recommendation are required."
            );
        }

        validateInterests(request.interests(), "Request interests");
        validateInterests(recommendation.interestTags(), "Recommendation interest tags");

        Set<Interest> requested = new LinkedHashSet<>(request.interests());
        Set<Interest> tags = new HashSet<>(recommendation.interestTags());

        return requested.stream()
                .filter(tags::contains)
                .toList();
    }

    private int calculateInterestsScore(
            List<Interest> requestedInterests,
            List<Interest> matchingInterests
    ) {
        int uniqueRequestedCount = new HashSet<>(requestedInterests).size();

        return BigDecimal.valueOf(50)
                .multiply(BigDecimal.valueOf(matchingInterests.size()))
                .divide(BigDecimal.valueOf(uniqueRequestedCount), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private int calculateBudgetScore(BigDecimal budget, BigDecimal estimatedTotalCost) {
        if (budget == null || estimatedTotalCost == null
                || budget.signum() <= 0 || estimatedTotalCost.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Budget and estimated total cost must be greater than zero."
            );
        }

        return estimatedTotalCost.compareTo(budget) <= 0 ? 30 : 0;
    }

    private int calculateDurationScore(Integer durationDays, Integer suggestedDurationDays) {
        if (durationDays == null || suggestedDurationDays == null
                || durationDays < 1 || durationDays > 30
                || suggestedDurationDays < 1 || suggestedDurationDays > 30) {
            throw new IllegalArgumentException(
                    "Trip durations must be between 1 and 30 days."
            );
        }

        return suggestedDurationDays <= durationDays ? 20 : 0;
    }

    private void validateInterests(List<Interest> interests, String name) {
        if (interests == null || interests.isEmpty()
                || interests.stream().anyMatch(interest -> interest == null)) {
            throw new IllegalArgumentException(
                    name + " must contain at least one non-null interest."
            );
        }
    }
}