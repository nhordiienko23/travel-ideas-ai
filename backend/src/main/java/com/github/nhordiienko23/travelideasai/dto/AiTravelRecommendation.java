package com.github.nhordiienko23.travelideasai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.nhordiienko23.travelideasai.model.Interest;
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

public record AiTravelRecommendation(

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

        @NotEmpty(message = "AI recommendation must contain at least one interest tag.")
        List<@NotNull(message = "Interest tag cannot be null.") Interest> interestTags,

        @NotNull(message = "Estimated total cost is required.")
        @DecimalMin(value = "0", inclusive = false,
                message = "Estimated total cost must be greater than zero.")
        @Digits(integer = 10, fraction = 2,
                message = "Estimated total cost must have at most 10 integer digits and 2 decimal places.")
        BigDecimal estimatedTotalCost,

        @NotNull(message = "Suggested duration is required.")
        @Min(value = 1, message = "Suggested duration must be at least 1 day.")
        @Max(value = 30, message = "Suggested duration must not exceed 30 days.")
        Integer suggestedDurationDays

) {
    public AiTravelRecommendation {
        destination = destination == null ? null : destination.strip();
        country = country == null ? null : country.strip();
        description = description == null ? null : description.strip();
        whyRecommended = whyRecommended == null ? null : whyRecommended.strip();
    }

    @JsonIgnore
    @AssertTrue(message = "Interest tags must not contain duplicate values.")
    public boolean isInterestTagsUnique() {
        return interestTags == null
                || new HashSet<>(interestTags).size() == interestTags.size();
    }
}