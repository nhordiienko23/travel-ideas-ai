package com.github.nhordiienko23.travelideasai.dto;

import java.util.List;
import java.util.Objects;

public record RecommendationBatchResponse(
        List<RecommendationResponse> recommendations
) {

    public RecommendationBatchResponse {
        if (recommendations == null || recommendations.size() != 3) {
            throw new IllegalArgumentException(
                    "Recommendation batch must contain exactly 3 recommendations."
            );
        }

        if (recommendations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Recommendation batch must not contain null elements."
            );
        }

        recommendations = List.copyOf(recommendations);
    }
}