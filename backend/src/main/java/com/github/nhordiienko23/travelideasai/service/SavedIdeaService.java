package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.dto.SaveIdeaRequest;
import com.github.nhordiienko23.travelideasai.dto.SavedIdeaResponse;
import com.github.nhordiienko23.travelideasai.dto.UpdateSavedIdeaRequest;
import com.github.nhordiienko23.travelideasai.entity.SavedIdea;
import com.github.nhordiienko23.travelideasai.exception.ResourceNotFoundException;
import com.github.nhordiienko23.travelideasai.repository.SavedIdeaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedIdeaService {

    private final SavedIdeaRepository savedIdeaRepository;

    @Transactional
    public SavedIdeaResponse create(SaveIdeaRequest request) {
        SavedIdea savedIdea = new SavedIdea(
                request.title(),
                request.destination(),
                request.country(),
                request.description(),
                request.whyRecommended(),
                request.estimatedTotalCost(),
                request.currency(),
                request.suggestedDurationDays(),
                request.matchScore(),
                request.personalNote()
        );

        return toResponse(savedIdeaRepository.save(savedIdea));
    }

    @Transactional(readOnly = true)
    public List<SavedIdeaResponse> findAll() {
        return savedIdeaRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SavedIdeaResponse update(Long id, UpdateSavedIdeaRequest request) {
        SavedIdea savedIdea = findEntityById(id);
        savedIdea.updateDetails(request.title(), request.personalNote());
        return toResponse(savedIdea);
    }

    @Transactional
    public void delete(Long id) {
        savedIdeaRepository.delete(findEntityById(id));
    }

    private SavedIdea findEntityById(Long id) {
        return savedIdeaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Saved idea with ID " + id + " was not found."
                ));
    }

    private SavedIdeaResponse toResponse(SavedIdea savedIdea) {
        return new SavedIdeaResponse(
                savedIdea.getId(),
                savedIdea.getTitle(),
                savedIdea.getDestination(),
                savedIdea.getCountry(),
                savedIdea.getDescription(),
                savedIdea.getWhyRecommended(),
                savedIdea.getEstimatedTotalCost(),
                savedIdea.getCurrency(),
                savedIdea.getSuggestedDurationDays(),
                savedIdea.getMatchScore(),
                savedIdea.getPersonalNote(),
                savedIdea.getCreatedAt()
        );
    }
}