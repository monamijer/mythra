// EpisodeRepository.java
package com.monprojet.series.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.monprojet.series.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    List<Episode> findBySaisonIdOrderByNumeroAsc(Long saisonId);

    List<Episode> findBySaison_Serie_IdOrderBySaison_NumeroAscNumeroAsc(Long serieId);

    long countBySaison_Serie_Id(Long serieId);

    long countBySaisonId(Long saisonId);

    @Query("""
    SELECT COALESCE(SUM(e.dureeMinutes), 0)
    FROM Episode e
    WHERE e.saison.serie.id = :serieId
    """)
    long sommeDureeTotaleParSerie(@Param("serieId") Long serieId);

    /** Temps total de tous les épisodes des séries possédées par un utilisateur. */
    @Query("""
    SELECT COALESCE(SUM(e.dureeMinutes), 0)
    FROM Episode e
    WHERE e.saison.serie.utilisateur.id = :utilisateurId
    """)
    long sommeDureeTotaleUtilisateur(@Param("utilisateurId") Long utilisateurId);
}