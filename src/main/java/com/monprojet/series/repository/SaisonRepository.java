// SaisonRepository.java
package com.monprojet.series.repository;

import com.monprojet.series.entity.Saison;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SaisonRepository extends JpaRepository<Saison, Long> {

    List<Saison> findBySerieIdOrderByNumeroAsc(Long serieId);

    // Used to resolve "next season" when a season is completed (RG2)
    Optional<Saison> findBySerieIdAndNumero(Long serieId, Integer numero);

    // Ownership check used by SecuriteService (a season belongs to its series' owner)
    boolean existsByIdAndSerie_Utilisateur_Id(Long saisonId, Long utilisateurId);
}