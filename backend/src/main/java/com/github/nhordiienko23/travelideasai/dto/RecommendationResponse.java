package com.github.nhordiienko23.travelideasai.dto;

import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import com.github.nhordiienko23.travelideasai.model.Interest;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationResponse(

        String destination,

        String country,

        String description,

        String whyRecommended,

        List<Interest> matchingInterests,

        BigDecimal estimatedTotalCost,

        CurrencyCode currency,

        Integer suggestedDurationDays,

        MatchScoreResponse matchScore

) {}