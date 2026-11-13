package com.github.nhordiienko23.travelideasai.service;

import com.github.nhordiienko23.travelideasai.dto.SaveIdeaRequest;
import com.github.nhordiienko23.travelideasai.dto.SavedIdeaResponse;
import com.github.nhordiienko23.travelideasai.dto.UpdateSavedIdeaRequest;
import com.github.nhordiienko23.travelideasai.entity.SavedIdea;
import com.github.nhordiienko23.travelideasai.exception.ResourceNotFoundException;
import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import com.github.nhordiienko23.travelideasai.repository.SavedIdeaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Saved idea service")
class SavedIdeaServiceTest {

    @Mock
    private SavedIdeaRepository savedIdeaRepository;

    @InjectMocks
    private SavedIdeaService service;

    @Test
    @DisplayName("Creates and maps a saved idea")
    void createsAndMapsSavedIdea() {
        SaveIdeaRequest request = saveRequest("Weekend in Rome", "Remember the museum");
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");

        when(savedIdeaRepository.save(any(SavedIdea.class))).thenAnswer(invocation -> {
            SavedIdea savedIdea = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedIdea, "id", 12L);
            ReflectionTestUtils.setField(savedIdea, "createdAt", createdAt);
            return savedIdea;
        });

        SavedIdeaResponse response = service.create(request);

        assertThat(response).isEqualTo(new SavedIdeaResponse(
                12L,
                "Weekend in Rome",
                "Rome",
                "Italy",
                "A historic city.",
                "Matches your interests.",
                new BigDecimal("456.78"),
                CurrencyCode.EUR,
                4,
                85,
                "Remember the museum",
                createdAt
        ));

        ArgumentCaptor<SavedIdea> captor = ArgumentCaptor.forClass(SavedIdea.class);
        verify(savedIdeaRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo(request.title());
        assertThat(captor.getValue().getDestination()).isEqualTo(request.destination());
        assertThat(captor.getValue().getCountry()).isEqualTo(request.country());
        assertThat(captor.getValue().getDescription()).isEqualTo(request.description());
        assertThat(captor.getValue().getWhyRecommended()).isEqualTo(request.whyRecommended());
        assertThat(captor.getValue().getEstimatedTotalCost())
                .isEqualByComparingTo(request.estimatedTotalCost());
        assertThat(captor.getValue().getCurrency()).isEqualTo(request.currency());
        assertThat(captor.getValue().getSuggestedDurationDays())
                .isEqualTo(request.suggestedDurationDays());
        assertThat(captor.getValue().getMatchScore()).isEqualTo(request.matchScore());
        assertThat(captor.getValue().getPersonalNote()).isEqualTo(request.personalNote());
    }

    @Test
    @DisplayName("Creates a saved idea without an optional personal note")
    void createsWithoutPersonalNote() {
        SaveIdeaRequest request = saveRequest("Trip", null);

        when(savedIdeaRepository.save(any(SavedIdea.class))).thenAnswer(invocation -> {
            SavedIdea savedIdea = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedIdea, "id", 1L);
            return savedIdea;
        });

        SavedIdeaResponse response = service.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.personalNote()).isNull();
        verify(savedIdeaRepository).save(any(SavedIdea.class));
    }

    @Test
    @DisplayName("Returns saved ideas in repository order")
    void returnsAllSavedIdeasInRepositoryOrder() {
        SavedIdea newer = savedIdea(2L, "Newer", "A note",
                Instant.parse("2026-02-02T00:00:00Z"));
        SavedIdea older = savedIdea(1L, "Older", null,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(savedIdeaRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(newer, older));

        List<SavedIdeaResponse> response = service.findAll();

        assertThat(response).containsExactly(toResponse(newer), toResponse(older));
        verify(savedIdeaRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("Returns an empty list when there are no saved ideas")
    void returnsEmptyListWhenNoSavedIdeasExist() {
        when(savedIdeaRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();

        verify(savedIdeaRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("Updates the title and personal note without changing other details")
    void updatesTitleAndPersonalNote() {
        SavedIdea existing = savedIdea(5L, "Old title", "Old note",
                Instant.parse("2026-03-01T00:00:00Z"));
        when(savedIdeaRepository.findById(5L)).thenReturn(Optional.of(existing));

        SavedIdeaResponse response = service.update(
                5L,
                new UpdateSavedIdeaRequest("New title", "New note")
        );

        assertThat(response).isEqualTo(new SavedIdeaResponse(
                5L,
                "New title",
                existing.getDestination(),
                existing.getCountry(),
                existing.getDescription(),
                existing.getWhyRecommended(),
                existing.getEstimatedTotalCost(),
                existing.getCurrency(),
                existing.getSuggestedDurationDays(),
                existing.getMatchScore(),
                "New note",
                existing.getCreatedAt()
        ));
        verify(savedIdeaRepository).findById(5L);
        verify(savedIdeaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Clears the personal note when an update supplies null")
    void clearsPersonalNote() {
        SavedIdea existing = savedIdea(6L, "Title", "Existing note",
                Instant.parse("2026-03-02T00:00:00Z"));
        when(savedIdeaRepository.findById(6L)).thenReturn(Optional.of(existing));

        SavedIdeaResponse response = service.update(
                6L,
                new UpdateSavedIdeaRequest("Title", null)
        );

        assertThat(response.personalNote()).isNull();
        assertThat(response.title()).isEqualTo("Title");
    }

    @Test
    @DisplayName("Rejects an update when the saved idea does not exist")
    void rejectsUpdateForMissingIdea() {
        when(savedIdeaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                99L,
                new UpdateSavedIdeaRequest("Title", "Note")
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Saved idea with ID 99 was not found.");

        verify(savedIdeaRepository, never()).save(any());
        verify(savedIdeaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Deletes an existing saved idea")
    void deletesExistingIdea() {
        SavedIdea existing = savedIdea(7L, "Title", null,
                Instant.parse("2026-04-01T00:00:00Z"));
        when(savedIdeaRepository.findById(7L)).thenReturn(Optional.of(existing));

        service.delete(7L);

        verify(savedIdeaRepository).findById(7L);
        verify(savedIdeaRepository).delete(existing);
    }

    @Test
    @DisplayName("Rejects deletion when the saved idea does not exist")
    void rejectsDeletionForMissingIdea() {
        when(savedIdeaRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Saved idea with ID 404 was not found.");

        verify(savedIdeaRepository, never()).delete(any());
    }

    private static SaveIdeaRequest saveRequest(String title, String personalNote) {
        return new SaveIdeaRequest(
                title,
                "Rome",
                "Italy",
                "A historic city.",
                "Matches your interests.",
                new BigDecimal("456.78"),
                CurrencyCode.EUR,
                4,
                85,
                personalNote
        );
    }

    private static SavedIdea savedIdea(
            Long id,
            String title,
            String personalNote,
            Instant createdAt
    ) {
        SavedIdea idea = new SavedIdea(
                title,
                "Rome",
                "Italy",
                "A historic city.",
                "Matches your interests.",
                new BigDecimal("456.78"),
                CurrencyCode.EUR,
                4,
                85,
                personalNote
        );
        ReflectionTestUtils.setField(idea, "id", id);
        ReflectionTestUtils.setField(idea, "createdAt", createdAt);
        return idea;
    }

    private static SavedIdeaResponse toResponse(SavedIdea idea) {
        return new SavedIdeaResponse(
                idea.getId(),
                idea.getTitle(),
                idea.getDestination(),
                idea.getCountry(),
                idea.getDescription(),
                idea.getWhyRecommended(),
                idea.getEstimatedTotalCost(),
                idea.getCurrency(),
                idea.getSuggestedDurationDays(),
                idea.getMatchScore(),
                idea.getPersonalNote(),
                idea.getCreatedAt()
        );
    }
}