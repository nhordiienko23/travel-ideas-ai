package com.github.nhordiienko23.travelideasai.dto;

public record MatchScoreResponse(
        int interestsScore,
        int budgetScore,
        int durationScore,
        int totalScore
) {
    public MatchScoreResponse {
        if (interestsScore < 0 || interestsScore > 50) {
            throw new IllegalArgumentException(
                    "Interests score must be between 0 and 50."
            );
        }

        if (budgetScore != 0 && budgetScore != 30) {
            throw new IllegalArgumentException(
                    "Budget score must be either 0 or 30."
            );
        }

        if (durationScore != 0 && durationScore != 20) {
            throw new IllegalArgumentException(
                    "Duration score must be either 0 or 20."
            );
        }

        if (totalScore < 0 || totalScore > 100) {
            throw new IllegalArgumentException(
                    "Total score must be between 0 and 100."
            );
        }

        if (totalScore != interestsScore + budgetScore + durationScore) {
            throw new IllegalArgumentException(
                    "Total score must equal the sum of all individual scores."
            );
        }
    }
}