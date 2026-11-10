// AuthService.java
package com.monprojet.series.service;

import com.monprojet.series.dto.request.ConnexionRequest;
import com.monprojet.series.dto.request.InscriptionRequest;
import com.monprojet.series.dto.response.AuthResponse;
import com.monprojet.series.entity.StatutUtilisateur;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.exception.BusinessException;
import com.monprojet.series.exception.CompteNonApprouveException;
import com.monprojet.series.exception.IdentifiantsInvalidesException;
import com.monprojet.series.repository.UtilisateurRepository;
import com.monprojet.series.security.JwtService;
import com.monprojet.series.security.UtilisateurDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UtilisateurDetailsService utilisateurDetailsService;
    private final JwtService jwtService;

    /** Creates an account in the EN_ATTENTE status: no token until an admin approves it. */
    public AuthResponse inscrire(InscriptionRequest request) {
        if (utilisateurRepository.existsByEmail(request.email())) {
            throw new BusinessException("Cet email est déjà utilisé.");
        }
        if (utilisateurRepository.existsByPseudo(request.pseudo())) {
            throw new BusinessException("Ce pseudo est déjà utilisé.");
        }

        Utilisateur utilisateur = utilisateurRepository.save(Utilisateur.builder()
                .pseudo(request.pseudo())
                .email(request.email())
                .motDePasse(passwordEncoder.encode(request.motDePasse()))
                .build()); // role USER and status EN_ATTENTE come from the entity defaults

        return reponse(null, utilisateur,
                "Votre compte a été créé et est en attente d'approbation par un administrateur.");
    }

    public AuthResponse connecter(ConnexionRequest request) {
        // 1. Credentials check (BCrypt comparison is done by Spring Security)
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.motDePasse()));
        } catch (AuthenticationException e) {
            throw new IdentifiantsInvalidesException();
        }

        Utilisateur utilisateur = utilisateurRepository.findByEmail(request.email())
                .orElseThrow(IdentifiantsInvalidesException::new);

        // 2. Account status, checked before any token is issued
        if (utilisateur.getStatut() == StatutUtilisateur.EN_ATTENTE) {
            throw new CompteNonApprouveException(
                    "Votre compte est en attente d'approbation par un administrateur.");
        }
        if (utilisateur.getStatut() == StatutUtilisateur.REFUSE) {
            throw new CompteNonApprouveException(
                    "Votre compte a été refusé. Contactez un administrateur.");
        }

        // 3. Last login date and token
        utilisateur.setDerniereConnexion(LocalDateTime.now());
        utilisateurRepository.save(utilisateur);

        String token = jwtService.genererToken(utilisateurDetailsService.construire(utilisateur));
        return reponse(token, utilisateur, "Connexion réussie.");
    }

    private AuthResponse reponse(String token, Utilisateur u, String message) {
        return new AuthResponse(token, u.getId(), u.getPseudo(), u.getRole().name(), u.getStatut().name(), message);
    }
}
