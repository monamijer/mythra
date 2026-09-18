package com.monprojet.series.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Payload du endpoint PATCH .../series/{id}/avis
 * Les deux champs sont optionnels : null = effacer.
 */
public record AvisRequest(
        @Min(value = 1, message = "La note doit être entre 1 et 10")
        @Max(value = 10, message = "La note doit être entre 1 et 10")
        Integer notePersonnelle,

        @Size(max = 500, message = "La critique ne peut pas dépasser 500 caractères")
        String critique
) {}