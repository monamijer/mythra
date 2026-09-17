// SerieRepository.java
package com.monprojet.series.repository;

import com.monprojet.series.entity.Serie;
import com.monprojet.series.entity.StatutVisionnage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SerieRepository extends JpaRepository<Serie, Long> {

    // Used by the TMDB import flow to avoid creating duplicates (RG7)
    Optional<Serie> findByTmdbId(Long tmdbId);

    List<Serie> findByUtilisateurId(Long utilisateurId);

    List<Serie> findByUtilisateurIdAndStatutVisionnage(Long utilisateurId, StatutVisionnage statut);

    // Ownership-checked lookup: returns empty if the series exists but belongs to
    // someone else
    Optional<Serie> findByIdAndUtilisateurId(Long id, Long utilisateurId);

    Optional<Serie> findByTmdbIdAndUtilisateurId(Long tmdbId, Long utilisateurId);
}