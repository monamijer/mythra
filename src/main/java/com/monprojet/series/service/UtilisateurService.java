// UtilisateurService.java
package com.monprojet.series.service;

import com.monprojet.series.dto.response.StatistiquesAdminResponse;
import com.monprojet.series.entity.RoleUtilisateur;
import com.monprojet.series.entity.Serie;
import com.monprojet.series.entity.StatutUtilisateur;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.exception.BusinessException;
import com.monprojet.series.exception.ResourceNotFoundException;
import com.monprojet.series.repository.SerieRepository;
import com.monprojet.series.repository.UtilisateurRepository;
import com.monprojet.series.repository.VisionnageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final SerieRepository serieRepository;
    private final VisionnageRepository visionnageRepository;

    @Transactional(readOnly = true)
    public List<Utilisateur> listerTous() {
        return utilisateurRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Utilisateur> listerParStatut(StatutUtilisateur statut) {
        return utilisateurRepository.findByStatutOrderByDateInscriptionAsc(statut);
    }

    @Transactional(readOnly = true)
    public Utilisateur obtenirParId(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : id=" + id));
    }

    @Transactional(readOnly = true)
    public Utilisateur obtenirParEmail(String email) {
        return utilisateurRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : email=" + email));
    }

    // ---------- Actions admin ----------

    public Utilisateur approuver(Long id) {
        Utilisateur u = obtenirParId(id);
        u.setStatut(StatutUtilisateur.APPROUVE);
        return utilisateurRepository.save(u);
    }

    public Utilisateur refuser(Long id) {
        Utilisateur u = obtenirParId(id);
        // Interdit de refuser le dernier admin actif (l'appli deviendrait inutilisable)
        if (u.getRole() == RoleUtilisateur.ADMIN) {
            verifierPasDernierAdmin(u, "refuser");
        }
        u.setStatut(StatutUtilisateur.REFUSE);
        return utilisateurRepository.save(u);
    }

    public Utilisateur promouvoir(Long id) {
        Utilisateur u = obtenirParId(id);
        u.setRole(RoleUtilisateur.ADMIN);
        return utilisateurRepository.save(u);
    }

    public Utilisateur retrograder(Long id) {
        Utilisateur u = obtenirParId(id);
        verifierPasDernierAdmin(u, "rétrograder");
        u.setRole(RoleUtilisateur.USER);
        return utilisateurRepository.save(u);
    }

    public Utilisateur changerMotDePasse(Long id, String nouveauMotDePasse) {
        Utilisateur u = obtenirParId(id);
        u.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        return utilisateurRepository.save(u);
    }

    /**
     * Deletes a user together with everything they own, in an order that
     * respects the foreign keys: watch records, then series (their seasons and
     * episodes follow through the cascade on Serie), then the account itself.
     */
    public void supprimer(Long id) {
        Utilisateur u = obtenirParId(id);
        verifierPasDernierAdmin(u, "supprimer");

        List<Long> serieIds = serieRepository.findByUtilisateurId(id).stream().map(Serie::getId).toList();

        visionnageRepository.supprimerParUtilisateur(id);
        serieIds.forEach(visionnageRepository::supprimerParSerie);

        // Reloaded after the bulk deletes, which clear the persistence context
        serieRepository.findByUtilisateurId(id).forEach(serieRepository::delete);
        serieRepository.flush();

        utilisateurRepository.findById(id).ifPresent(utilisateurRepository::delete);
    }

    @Transactional(readOnly = true)
    public StatistiquesAdminResponse statistiques() {
        long total = utilisateurRepository.count();
        long attente = utilisateurRepository.countByStatut(StatutUtilisateur.EN_ATTENTE);
        long approuves = utilisateurRepository.countByStatut(StatutUtilisateur.APPROUVE);
        long refuses = utilisateurRepository.countByStatut(StatutUtilisateur.REFUSE);
        long admins = utilisateurRepository.findAll().stream()
                .filter(u -> u.getRole() == RoleUtilisateur.ADMIN)
                .count();
        return new StatistiquesAdminResponse(total, attente, approuves, refuses, admins);
    }

    // ---------- Garde-fous ----------

    /**
     * Empêche de rétrograder, refuser ou supprimer le dernier administrateur
     * actif de l'application. Sans ce garde-fou, un admin pourrait se
     * "verrouiller dehors" et plus personne ne pourrait administrer la plateforme.
     */
    private void verifierPasDernierAdmin(Utilisateur cible, String action) {
        if (cible.getRole() != RoleUtilisateur.ADMIN) return;

        long nbAdminsApprouves = utilisateurRepository.findAll().stream()
                .filter(u -> u.getRole() == RoleUtilisateur.ADMIN
                          && u.getStatut() == StatutUtilisateur.APPROUVE)
                .count();

        if (nbAdminsApprouves <= 1) {
            throw new BusinessException(
                "Impossible de " + action + " le dernier administrateur actif de l'application. "
                + "Promouvez un autre utilisateur en ADMIN avant."
            );
        }
    }
}