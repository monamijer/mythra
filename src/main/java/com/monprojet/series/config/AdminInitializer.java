// AdminInitializer.java
package com.monprojet.series.config;

import com.monprojet.series.entity.RoleUtilisateur;
import com.monprojet.series.entity.StatutUtilisateur;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator when the database has none yet.
 * New accounts start as EN_ATTENTE and only an admin can approve them, so
 * without this bootstrap a fresh database could never get a usable account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String email;

    @Value("${app.admin.pseudo}")
    private String pseudo;

    @Value("${app.admin.password}")
    private String motDePasse;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (utilisateurRepository.existsByRole(RoleUtilisateur.ADMIN)) {
            return;
        }
        if (utilisateurRepository.existsByEmail(email) || utilisateurRepository.existsByPseudo(pseudo)) {
            log.warn("Aucun administrateur, mais l'email ou le pseudo '{}' est déjà pris : "
                    + "aucun compte administrateur initial n'a été créé.", email);
            return;
        }

        Utilisateur admin = Utilisateur.builder()
                .pseudo(pseudo)
                .email(email)
                .motDePasse(passwordEncoder.encode(motDePasse))
                .role(RoleUtilisateur.ADMIN)
                .statut(StatutUtilisateur.APPROUVE)
                .build();
        utilisateurRepository.save(admin);

        log.info("Compte administrateur initial créé : {}", email);
    }
}
