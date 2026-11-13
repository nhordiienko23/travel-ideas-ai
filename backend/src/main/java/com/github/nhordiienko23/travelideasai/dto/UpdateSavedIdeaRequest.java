package com.github.nhordiienko23.travelideasai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSavedIdeaRequest(

        @NotBlank(message = "Title is required.")
        @Size(max = 100, message = "Title must not exceed 100 characters.")
        String title,

        @Size(max = 500, message = "Personal note must not exceed 500 characters.")
        String personalNote

) {}