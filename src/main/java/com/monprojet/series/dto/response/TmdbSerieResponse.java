// TmdbSerieResponse.java — what our own API actually returns to the client
package com.monprojet.series.dto.response;

public record TmdbSerieResponse(
        Long tmdbId,
        String titre,
        String description,
        String dateDiffusion,
        Double note,
        String imageUrl,
        // Nouveaux champs — nullables pour les listes paginées (non remplis là-bas)
        String dateDernierEpisode,
        Integer nombreSaisons,
        Integer nombreEpisodes,
        String statut
) {
    /**
     * Constructeur compact pour les usages simples (listes paginées)
     * qui n'ont pas besoin des nouveaux champs.
     */
    public TmdbSerieResponse(Long tmdbId, String titre, String description,
                             String dateDiffusion, Double note, String imageUrl) {
        this(tmdbId, titre, description, dateDiffusion, note, imageUrl, null, null, null, null);
    }
}