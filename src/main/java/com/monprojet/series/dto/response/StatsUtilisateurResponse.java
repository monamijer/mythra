package com.monprojet.series.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tableau de bord statistique d'un utilisateur.
 * Toutes les durées sont en minutes pour rester simples à agréger côté front.
 */
public record StatsUtilisateurResponse(
        // Compteurs de séries par statut
        long seriesTotal,
        long seriesTerminees,
        long seriesEnCours,
        long seriesAVoir,
        long seriesAbandonnees,

        // Temps (minutes)
        long tempsTotalVisionneMinutes,
        long tempsRestantMinutes,

        // Épisodes
        long episodesVus,
        long episodesTotal,

        // Notes personnelles
        Double notePersonnelleMoyenne,
        long seriesNotees,
        long seriesNonNotees,

        // Top genres (par épisodes vus)
        List<GenreStat> topGenres,

        // Derniers épisodes marqués comme vus
        List<VisionnageRecent> activiteRecente
) {
    public record GenreStat(String genre, long episodesVus, long minutesVisionnees) {}

    public record VisionnageRecent(
            String serieTitre,
            String episodeTitre,
            Integer saisonNumero,
            Integer episodeNumero,
            LocalDateTime dateVisionnage
    ) {}
}