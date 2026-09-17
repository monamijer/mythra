// SerieService.java
package com.monprojet.series.service;

import com.monprojet.series.entity.Serie;
import com.monprojet.series.entity.StatutVisionnage;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.exception.ResourceNotFoundException;
import com.monprojet.series.repository.SerieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SerieService {

    private final SerieRepository serieRepository;
    private final UtilisateurService utilisateurService;

    @Transactional(readOnly = true)
    public List<Serie> listerToutes(Long utilisateurId) {
        return serieRepository.findByUtilisateurId(utilisateurId);
    }

    @Transactional(readOnly = true)
    public List<Serie> listerParStatut(Long utilisateurId, StatutVisionnage statut) {
        return serieRepository.findByUtilisateurIdAndStatutVisionnage(utilisateurId, statut);
    }

    @Transactional(readOnly = true)
    public Serie obtenirParId(Long id, Long utilisateurId) {
        return serieRepository.findByIdAndUtilisateurId(id, utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Série introuvable : id=" + id));
    }

    public Serie creer(Long utilisateurId, Serie serie) {
        Utilisateur utilisateur = utilisateurService.obtenirParId(utilisateurId);
        serie.setUtilisateur(utilisateur);
        return serieRepository.save(serie);
    }

    public Serie modifier(Long id, Long utilisateurId, Serie donnees) {
        Serie existante = obtenirParId(id, utilisateurId);
        existante.setTitre(donnees.getTitre());
        existante.setGenre(donnees.getGenre());
        existante.setDescription(donnees.getDescription());
        existante.setAnneeSortie(donnees.getAnneeSortie());
        existante.setNote(donnees.getNote());
        existante.setImageUrl(donnees.getImageUrl());
        // Le statut peut être modifié via la modale d'édition OU l'endpoint dédié
        if (donnees.getStatutVisionnage() != null) {
            existante.setStatutVisionnage(donnees.getStatutVisionnage());
        }
        return serieRepository.save(existante);
    }

    /**
     * Endpoint dédié au changement rapide de statut (bouton sur la carte).
     * Si statut == null → remet le statut en mode "auto" (non forcé).
     */
    public Serie changerStatut(Long id, Long utilisateurId, StatutVisionnage statut) {
        Serie serie = obtenirParId(id, utilisateurId);
        serie.setStatutVisionnage(statut);
        return serieRepository.save(serie);
    }

    public void supprimer(Long id, Long utilisateurId) {
        Serie serie = obtenirParId(id, utilisateurId);
        serieRepository.delete(serie);
    }

    @Transactional(readOnly = true)
    public List<Serie> listerToutesAdmin() {
        return serieRepository.findAll();
    }
}