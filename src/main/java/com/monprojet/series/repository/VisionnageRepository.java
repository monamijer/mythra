// VisionnageRepository.java
package com.monprojet.series.repository;

import com.monprojet.series.entity.Visionnage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VisionnageRepository extends JpaRepository<Visionnage, Long> {

    boolean existsByUtilisateur_IdAndEpisode_Id(Long utilisateurId, Long episodeId);

    Optional<Visionnage> findByUtilisateur_IdAndEpisode_Id(Long utilisateurId, Long episodeId);

    long countByUtilisateur_IdAndEpisode_Saison_Serie_Id(Long utilisateurId, Long serieId);

    List<Visionnage> findByUtilisateur_IdAndEpisode_Saison_Serie_Id(Long utilisateurId, Long serieId);

    @Query("""
        SELECT COALESCE(SUM(e.dureeMinutes), 0)
        FROM Visionnage v JOIN v.episode e
        WHERE v.utilisateur.id = :utilisateurId
        AND e.saison.serie.id = :serieId
        """)
    long sommeDureeVisionneeParSerie(
            @Param("utilisateurId") Long utilisateurId,
            @Param("serieId") Long serieId
    );

    long countByUtilisateur_IdAndEpisode_SaisonId(Long utilisateurId, Long saisonId);

    // ---------- Stats utilisateur ----------

    /** Tous les visionnages d'un utilisateur (pour calculs de stats). */
    List<Visionnage> findByUtilisateur_Id(Long utilisateurId);

    /** Nombre total d'épisodes vus par un utilisateur. */
    long countByUtilisateur_Id(Long utilisateurId);

    /** Durée totale visionnée par un utilisateur, tous épisodes confondus. */
    @Query("""
        SELECT COALESCE(SUM(e.dureeMinutes), 0)
        FROM Visionnage v JOIN v.episode e
        WHERE v.utilisateur.id = :utilisateurId
        """)
    long sommeDureeVisionneeTotale(@Param("utilisateurId") Long utilisateurId);

    /** 10 derniers visionnages d'un utilisateur, du plus récent au plus ancien. */
    List<Visionnage> findTop10ByUtilisateur_IdOrderByDateVisionnageDesc(Long utilisateurId);
}