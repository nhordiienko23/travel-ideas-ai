package com.github.nhordiienko23.travelideasai.entity;

import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "saved_ideas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedIdea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 1000)
    private String whyRecommended;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedTotalCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Column(nullable = false)
    private Integer suggestedDurationDays;

    @Column(nullable = false)
    private Integer matchScore;

    @Column(length = 500)
    private String personalNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public SavedIdea(
            String title,
            String destination,
            String country,
            String description,
            String whyRecommended,
            BigDecimal estimatedTotalCost,
            CurrencyCode currency,
            Integer suggestedDurationDays,
            Integer matchScore,
            String personalNote
    ) {
        this.title = title;
        this.destination = destination;
        this.country = country;
        this.description = description;
        this.whyRecommended = whyRecommended;
        this.estimatedTotalCost = estimatedTotalCost;
        this.currency = currency;
        this.suggestedDurationDays = suggestedDurationDays;
        this.matchScore = matchScore;
        this.personalNote = personalNote;
    }

    public void updateDetails(String title, String personalNote) {
        this.title = title;
        this.personalNote = personalNote;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}