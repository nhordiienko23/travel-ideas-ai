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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("Saved Idea Service Tests")
class SavedIdeaServiceTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-01-02T03:04:05Z");

    @Mock
    private SavedIdeaRepository savedIdeaRepository;

    @InjectMocks
    private SavedIdeaService service;

    // --------------------------------------------------
    // Create
    // --------------------------------------------------

    @Test
    @DisplayName("Should create and return a saved idea with all fields")
    void createsAndMapsSavedIdea() {
        SaveIdeaRequest request =
                saveRequest("Weekend in Rome", "Remember the museum");

        when(savedIdeaRepository.save(any(SavedIdea.class)))
                .thenAnswer(invocation -> {
                    SavedIdea savedIdea = invocation.getArgument(0);

                    ReflectionTestUtils.setField(savedIdea, "id", 12L);
                    ReflectionTestUtils.setField(
                            savedIdea,
                            "createdAt",
                            CREATED_AT
                    );

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
                CREATED_AT
        ));

        ArgumentCaptor<SavedIdea> captor =
                ArgumentCaptor.forClass(SavedIdea.class);

        verify(savedIdeaRepository).save(captor.capture());

        SavedIdea savedIdea = captor.getValue();

        assertThat(savedIdea.getTitle()).isEqualTo(request.title());
        assertThat(savedIdea.getDestination()).isEqualTo(request.destination());
        assertThat(savedIdea.getCountry()).isEqualTo(request.country());
        assertThat(savedIdea.getDescription()).isEqualTo(request.description());
        assertThat(savedIdea.getWhyRecommended())
                .isEqualTo(request.whyRecommended());
        assertThat(savedIdea.getEstimatedTotalCost())
                .isEqualByComparingTo(request.estimatedTotalCost());
        assertThat(savedIdea.getCurrency()).isEqualTo(request.currency());
        assertThat(savedIdea.getSuggestedDurationDays())
                .isEqualTo(request.suggestedDurationDays());
        assertThat(savedIdea.getMatchScore()).isEqualTo(request.matchScore());
        assertThat(savedIdea.getPersonalNote()).isEqualTo(request.personalNote());

        verifyNoMoreInteractions(savedIdeaRepository);
    }

    @Test
    @DisplayName("Should create a saved idea without an optional personal note")
    void createsWithoutPersonalNote() {
        SaveIdeaRequest request = saveRequest("Weekend trip", null);

        when(savedIdeaRepository.save(any(SavedIdea.class)))
                .thenAnswer(invocation -> {
                    SavedIdea savedIdea = invocation.getArgument(0);
                    ReflectionTestUtils.setField(savedIdea, "id", 1L);
                    return savedIdea;
                });

        SavedIdeaResponse response = service.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Weekend trip");
        assertThat(response.personalNote()).isNull();

        verify(savedIdeaRepository).save(any(SavedIdea.class));
        verifyNoMoreInteractions(savedIdeaRepository);
    }

    // --------------------------------------------------
    // Read
    // --------------------------------------------------

    @Test
    @DisplayName("Should return saved ideas in repository order")
    void returnsAllSavedIdeasInRepositoryOrder() {
        SavedIdea newer = savedIdea(
                2L,
                "Newer",
                "A note",
                Instant.parse("2026-02-02T00:00:00Z")
        );

        SavedIdea older = savedIdea(
                1L,
                "Older",
                null,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(savedIdeaRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(newer, older));

        List<SavedIdeaResponse> response = service.findAll();

        assertThat(response)
                .containsExactly(
                        toResponse(newer),
                        toResponse(older)
                );

        verify(savedIdeaRepository).findAllByOrderByCreatedAtDesc();
        verifyNoMoreInteractions(savedIdeaRepository);
    }

    @Test
    @DisplayName("Should return an empty list when no saved ideas exist")
    void returnsEmptyListWhenNoSavedIdeasExist() {
        when(savedIdeaRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();

        verify(savedIdeaRepository).findAllByOrderByCreatedAtDesc();
        verifyNoMoreInteractions(savedIdeaRepository);
    }

    // --------------------------------------------------
    // Update
    // --------------------------------------------------

    @Test
    @DisplayName("Should update the title and personal note without changing other fields")
    void updatesTitleAndPersonalNote() {
        SavedIdea existing = savedIdea(
                5L,
                "Old title",
                "Old note",
                Instant.parse("2026-03-01T00:00:00Z")
        );

        when(savedIdeaRepository.findById(5L))
                .thenReturn(Optional.of(existing));

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

        assertThat(existing.getTitle()).isEqualTo("New title");
        assertThat(existing.getPersonalNote()).isEqualTo("New note");

        verify(savedIdeaRepository).findById(5L);
        verify(savedIdeaRepository, never()).save(any(SavedIdea.class));
        verify(savedIdeaRepository, never()).delete(any(SavedIdea.class));
        verifyNoMoreInteractions(savedIdeaRepository);
    }

    @Test
    @DisplayName("Should clear the personal note when the update supplies null")
    void clearsPersonalNote() {
        SavedIdea existing = savedIdea(
                6L,
                "Title",
                "Existing note",
                Instant.parse("2026-03-02T00:00:00Z")
        );

        when(savedIdeaRepository.findById(6L))
                .thenReturn(Optional.of(existing));

        SavedIdeaResponse response = service.update(
                6L,
                new UpdateSavedIdeaRequest("Title", null)
        );

        assertThat(response.personalNote()).isNull();
        assertThat(response.title()).isEqualTo("Title");
        assertThat(existing.getPersonalNote()).isNull();

        verify(savedIdeaRepository).findById(6L);
        verify(savedIdeaRepository, never()).save(any(SavedIdea.class));
        verifyNoMoreInteractions(savedIdeaRepository);
    }

    @Test
    @DisplayName("Should reject an update when the saved idea does not exist")
    void rejectsUpdateForMissingIdea() {
        when(savedIdeaRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                99L,
                new UpdateSavedIdeaRequest("Title", "Note")
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Saved idea with ID 99 was not found.");

        verify(savedIdeaRepository).findById(99L);
        verify(savedIdeaRepository, never()).save(any(SavedIdea.class));
        verify(savedIdeaRepository, never()).delete(any(SavedIdea.class));
        verifyNoMoreInteractions(savedIdeaRepository);
    }

    // --------------------------------------------------
    // Delete
    // --------------------------------------------------

    @Test
    @DisplayName("Should delete an existing saved idea")
    void deletesExistingIdea() {
        SavedIdea existing = savedIdea(
                7L,
                "Title",
                null,
                Instant.parse("2026-04-01T00:00:00Z")
        );

        when(savedIdeaRepository.findById(7L))
                .thenReturn(Optional.of(existing));

        service.delete(7L);

        verify(savedIdeaRepository).findById(7L);
        verify(savedIdeaRepository).delete(existing);
        verify(savedIdeaRepository, never()).save(any(SavedIdea.class));
        verifyNoMoreInteractions(savedIdeaRepository);
    }

    @Test
    @DisplayName("Should reject deletion when the saved idea does not exist")
    void rejectsDeletionForMissingIdea() {
        when(savedIdeaRepository.findById(404L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Saved idea with ID 404 was not found.");

        verify(savedIdeaRepository).findById(404L);
        verify(savedIdeaRepository, never()).delete(any(SavedIdea.class));
        verify(savedIdeaRepository, never()).save(any(SavedIdea.class));
        verifyNoMoreInteractions(savedIdeaRepository);
    }

    // --------------------------------------------------
    // Test helpers
    // --------------------------------------------------

    private static SaveIdeaRequest saveRequest(
            String title,
            String personalNote
    ) {
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
