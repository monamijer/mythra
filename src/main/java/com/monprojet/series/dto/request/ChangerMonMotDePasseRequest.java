package com.monprojet.series.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-service password change: unlike the admin reset, the current password is required. */
public record ChangerMonMotDePasseRequest(
        @NotBlank String ancienMotDePasse,

        @NotBlank @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        String nouveauMotDePasse
) {
}
