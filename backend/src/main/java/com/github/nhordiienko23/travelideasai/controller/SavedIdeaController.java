package com.github.nhordiienko23.travelideasai.controller;

import com.github.nhordiienko23.travelideasai.dto.SaveIdeaRequest;
import com.github.nhordiienko23.travelideasai.dto.SavedIdeaResponse;
import com.github.nhordiienko23.travelideasai.dto.UpdateSavedIdeaRequest;
import com.github.nhordiienko23.travelideasai.service.SavedIdeaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ideas")
@RequiredArgsConstructor
public class SavedIdeaController {

    private final SavedIdeaService savedIdeaService;

    @PostMapping
    public ResponseEntity<SavedIdeaResponse> create(
            @Valid @RequestBody SaveIdeaRequest request
    ) {
        SavedIdeaResponse response = savedIdeaService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SavedIdeaResponse>> findAll() {
        return ResponseEntity.ok(savedIdeaService.findAll());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SavedIdeaResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSavedIdeaRequest request
    ) {
        SavedIdeaResponse response = savedIdeaService.update(id, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        savedIdeaService.delete(id);

        return ResponseEntity.noContent().build();
    }
}