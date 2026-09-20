// SerieRepository.java
package com.monprojet.series.repository;

import com.monprojet.series.entity.Serie;
import com.monprojet.series.entity.StatutVisionnage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SerieRepository extends JpaRepository<Serie, Long> {

    Optional<Serie> findByTmdbId(Long tmdbId);

    List<Serie> findByUtilisateurId(Long utilisateurId);

    List<Serie> findByUtilisateurIdAndStatutVisionnage(Long utilisateurId, StatutVisionnage statut);

    Optional<Serie> findByIdAndUtilisateurId(Long id, Long utilisateurId);

    Optional<Serie> findByTmdbIdAndUtilisateurId(Long tmdbId, Long utilisateurId);

    // ---------- Stats utilisateur ----------

    long countByUtilisateur_Id(Long utilisateurId);

    long countByUtilisateur_IdAndStatutVisionnage(Long utilisateurId, StatutVisionnage statut);

    long countByUtilisateur_IdAndNotePersonnelleIsNotNull(Long utilisateurId);
}