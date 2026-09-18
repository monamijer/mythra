// SerieRequest.java
package com.monprojet.series.dto.request;

import com.monprojet.series.entity.StatutVisionnage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SerieRequest(
        @NotBlank(message = "Le titre est obligatoire") String titre,
        String genre,
        String description,
        Integer anneeSortie,
        @Min(0) @Max(10) Double note,
        String imageUrl,
        StatutVisionnage statutVisionnage,

        @Min(value = 1, message = "La note personnelle doit être entre 1 et 10")
        @Max(value = 10, message = "La note personnelle doit être entre 1 et 10")
        Integer notePersonnelle,

        @Size(max = 500, message = "La critique ne peut pas dépasser 500 caractères")
        String critique
) {}