// SerieResponse.java
package com.monprojet.series.dto.response;

public record SerieResponse(
        Long id,
        String titre,
        String genre,
        String description,
        Integer anneeSortie,
        Double note,
        String imageUrl,
        Long tmdbId,
        String statutVisionnage
) {}