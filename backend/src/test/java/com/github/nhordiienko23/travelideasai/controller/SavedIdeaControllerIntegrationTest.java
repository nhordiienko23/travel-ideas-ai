package com.github.nhordiienko23.travelideasai.controller;

import com.github.nhordiienko23.travelideasai.dto.SaveIdeaRequest;
import com.github.nhordiienko23.travelideasai.dto.SavedIdeaResponse;
import com.github.nhordiienko23.travelideasai.dto.UpdateSavedIdeaRequest;
import com.github.nhordiienko23.travelideasai.exception.GlobalExceptionHandler;
import com.github.nhordiienko23.travelideasai.exception.ResourceNotFoundException;
import com.github.nhordiienko23.travelideasai.model.CurrencyCode;
import com.github.nhordiienko23.travelideasai.service.SavedIdeaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SavedIdeaController.class)
@Import(GlobalExceptionHandler.class)
class SavedIdeaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SavedIdeaService savedIdeaService;

    @Test
    void create_shouldReturn201_whenRequestIsValid() throws Exception {
        SavedIdeaResponse response = createSavedIdeaResponse();

        when(savedIdeaService.create(any(SaveIdeaRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Weekend in Krakow",
                                    "destination": "Krakow",
                                    "country": "Poland",
                                    "description": "A beautiful historic city.",
                                    "whyRecommended": "Matches your interest in history.",
                                    "estimatedTotalCost": 250.00,
                                    "currency": "PLN",
                                    "suggestedDurationDays": 3,
                                    "matchScore": 100,
                                    "personalNote": "Visit the old town."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Weekend in Krakow"))
                .andExpect(jsonPath("$.destination").value("Krakow"))
                .andExpect(jsonPath("$.country").value("Poland"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.matchScore").value(100));

        verify(savedIdeaService).create(any(SaveIdeaRequest.class));
    }

    @Test
    void create_shouldReturn400_whenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "",
                                    "destination": "",
                                    "country": "",
                                    "description": "",
                                    "whyRecommended": "",
                                    "estimatedTotalCost": 0,
                                    "currency": "PLN",
                                    "suggestedDurationDays": 0,
                                    "matchScore": 101
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(savedIdeaService);
    }

    @Test
    void findAll_shouldReturn200_whenIdeasExist() throws Exception {
        when(savedIdeaService.findAll())
                .thenReturn(List.of(createSavedIdeaResponse()));

        mockMvc.perform(get("/api/ideas"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].destination").value("Krakow"));

        verify(savedIdeaService).findAll();
    }

    @Test
    void findAll_shouldReturnEmptyArray_whenNoIdeasExist() throws Exception {
        when(savedIdeaService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(savedIdeaService).findAll();
    }

    @Test
    void update_shouldReturn200_whenRequestIsValid() throws Exception {
        SavedIdeaResponse response = new SavedIdeaResponse(
                1L,
                "Updated Krakow Trip",
                "Krakow",
                "Poland",
                "A beautiful historic city.",
                "Matches your interest in history.",
                new BigDecimal("250.00"),
                CurrencyCode.PLN,
                3,
                100,
                "Visit the castle.",
                Instant.parse("2026-11-13T10:00:00Z")
        );

        when(savedIdeaService.update(
                eq(1L),
                any(UpdateSavedIdeaRequest.class)
        )).thenReturn(response);

        mockMvc.perform(patch("/api/ideas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated Krakow Trip",
                                    "personalNote": "Visit the castle."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Updated Krakow Trip"))
                .andExpect(jsonPath("$.personalNote").value("Visit the castle."));

        verify(savedIdeaService).update(
                eq(1L),
                any(UpdateSavedIdeaRequest.class)
        );
    }

    @Test
    void update_shouldReturn400_whenTitleIsInvalid() throws Exception {
        mockMvc.perform(patch("/api/ideas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "",
                                    "personalNote": "Some note."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(savedIdeaService);
    }

    @Test
    void update_shouldReturn404_whenIdeaDoesNotExist() throws Exception {
        when(savedIdeaService.update(
                eq(99L),
                any(UpdateSavedIdeaRequest.class)
        )).thenThrow(new ResourceNotFoundException(
                "Saved idea with ID 99 was not found."
        ));

        mockMvc.perform(patch("/api/ideas/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated title",
                                    "personalNote": null
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(savedIdeaService).update(
                eq(99L),
                any(UpdateSavedIdeaRequest.class)
        );
    }

    @Test
    void delete_shouldReturn204_whenIdeaExists() throws Exception {
        mockMvc.perform(delete("/api/ideas/1"))
                .andExpect(status().isNoContent());

        verify(savedIdeaService).delete(1L);
    }

    @Test
    void delete_shouldReturn404_whenIdeaDoesNotExist() throws Exception {
        doThrow(new ResourceNotFoundException(
                "Saved idea with ID 99 was not found."
        )).when(savedIdeaService).delete(99L);

        mockMvc.perform(delete("/api/ideas/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(savedIdeaService).delete(99L);
    }

    private SavedIdeaResponse createSavedIdeaResponse() {
        return new SavedIdeaResponse(
                1L,
                "Weekend in Krakow",
                "Krakow",
                "Poland",
                "A beautiful historic city.",
                "Matches your interest in history.",
                new BigDecimal("250.00"),
                CurrencyCode.PLN,
                3,
                100,
                "Visit the old town.",
                Instant.parse("2026-11-13T10:00:00Z")
        );
    }
}