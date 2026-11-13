package com.github.nhordiienko23.travelideasai.dto;

import com.github.nhordiienko23.travelideasai.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;

public record SavedIdeaResponse(
        Long id,
        String title,
        String destination,
        String country,
        String description,
        String whyRecommended,
        BigDecimal estimatedTotalCost,
        CurrencyCode currency,
        Integer suggestedDurationDays,
        Integer matchScore,
        String personalNote,
        Instant createdAt
) {}