package com.monprojet.series.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModifierProfilRequest(
        @NotBlank @Size(max = 50, message = "Le pseudo ne doit pas dépasser 50 caractères")
        String pseudo,

        @NotBlank @Email @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
        String email
) {
}
