package com.github.nhordiienko23.travelideasai.controller;

import com.github.nhordiienko23.travelideasai.dto.RecommendationBatchResponse;
import com.github.nhordiienko23.travelideasai.dto.RecommendationRequest;
import com.github.nhordiienko23.travelideasai.service.TravelRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final TravelRecommendationService travelRecommendationService;

    @PostMapping
    public ResponseEntity<RecommendationBatchResponse> generateRecommendations(
            @Valid @RequestBody RecommendationRequest request
    ) {
        RecommendationBatchResponse response =
                travelRecommendationService.generateRecommendations(request);

        return ResponseEntity.ok(response);
    }
}