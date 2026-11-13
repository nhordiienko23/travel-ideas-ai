package com.github.nhordiienko23.travelideasai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record AiRecommendationBatch(

        @NotNull(message = "AI response must contain a recommendations list.")
        @Size(min = 3, max = 3,
                message = "AI must return exactly 3 recommendations.")
        List<
                @NotNull(message = "AI recommendation cannot be null.")
                @Valid AiTravelRecommendation
                > recommendations

) {

    @JsonIgnore
    @AssertTrue(message = "AI recommendations must have unique destination and country pairs.")
    public boolean isDestinationsUnique() {
        if (recommendations == null) {
            return true;
        }

        Set<List<String>> uniqueDestinations = new HashSet<>();

        for (AiTravelRecommendation recommendation : recommendations) {
            if (recommendation == null) {
                continue;
            }

            String destination = recommendation.destination();
            String country = recommendation.country();

            if (destination == null || country == null) {
                continue;
            }

            List<String> normalizedDestination = List.of(
                    destination.strip().toLowerCase(Locale.ROOT),
                    country.strip().toLowerCase(Locale.ROOT)
            );

            if (!uniqueDestinations.add(normalizedDestination)) {
                return false;
            }
        }

        return true;
    }
}