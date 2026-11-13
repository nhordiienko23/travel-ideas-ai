package com.github.nhordiienko23.travelideasai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import com.github.nhordiienko23.travelideasai.model.Interest;
import com.github.nhordiienko23.travelideasai.model.TransportMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

public record RecommendationRequest(

        @NotBlank(message = "Departure city is required.")
        @Size(min = 2, max = 100,
                message = "Departure city must contain between 2 and 100 characters.")
        String departureCity,

        @NotEmpty(message = "Select at least one interest.")
        @Size(max = 8, message = "You can select no more than 8 interests.")
        List<@NotNull(message = "Interest cannot be null.") Interest> interests,

        @NotNull(message = "Budget is required.")
        @DecimalMin(value = "0", inclusive = false,
                message = "Budget must be greater than zero.")
        @Digits(integer = 10, fraction = 2,
                message = "Budget must have at most 10 integer digits and 2 decimal places.")
        BigDecimal budget,

        @NotNull(message = "Currency is required.")
        CurrencyCode currency,

        @NotNull(message = "Trip duration is required.")
        @Min(value = 1, message = "Trip duration must be at least 1 day.")
        @Max(value = 30, message = "Trip duration must not exceed 30 days.")
        Integer durationDays,

        @NotNull(message = "Preferred transport is required.")
        TransportMode preferredTransport

) {
    public RecommendationRequest {
        departureCity = departureCity == null ? null : departureCity.strip();
    }

    @JsonIgnore
    @AssertTrue(message = "Interests must not contain duplicate values.")
    public boolean isInterestsUnique() {
        return interests == null
                || new HashSet<>(interests).size() == interests.size();
    }
}