package com.github.nhordiienko23.travelideasai.dto;

import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SaveIdeaRequest(

        @NotBlank(message = "Title is required.")
        @Size(max = 100, message = "Title must not exceed 100 characters.")
        String title,

        @NotBlank(message = "Destination is required.")
        @Size(max = 100, message = "Destination must not exceed 100 characters.")
        String destination,

        @NotBlank(message = "Country is required.")
        @Size(max = 100, message = "Country must not exceed 100 characters.")
        String country,

        @NotBlank(message = "Description is required.")
        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        String description,

        @NotBlank(message = "Recommendation reason is required.")
        @Size(max = 1000, message = "Recommendation reason must not exceed 1000 characters.")
        String whyRecommended,

        @NotNull(message = "Estimated total cost is required.")
        @DecimalMin(value = "0", inclusive = false,
                message = "Estimated total cost must be greater than zero.")
        @Digits(integer = 10, fraction = 2,
                message = "Estimated total cost must have at most 10 integer digits and 2 decimal places.")
        BigDecimal estimatedTotalCost,

        @NotNull(message = "Currency is required.")
        CurrencyCode currency,

        @NotNull(message = "Suggested duration is required.")
        @Min(value = 1, message = "Suggested duration must be at least 1 day.")
        @Max(value = 30, message = "Suggested duration must not exceed 30 days.")
        Integer suggestedDurationDays,

        @NotNull(message = "Match score is required.")
        @Min(value = 0, message = "Match score must not be less than 0.")
        @Max(value = 100, message = "Match score must not exceed 100.")
        Integer matchScore,

        @Size(max = 500, message = "Personal note must not exceed 500 characters.")
        String personalNote

) {}