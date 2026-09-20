package com.monprojet.series.controller;

import com.monprojet.series.dto.response.StatsUtilisateurResponse;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.service.StatsService;
import com.monprojet.series.service.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/utilisateurs/{userId}/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final UtilisateurService utilisateurService;

    @GetMapping
    public StatsUtilisateurResponse stats(@PathVariable Long userId) {
        verifierAcces(userId);
        return statsService.calculer(userId);
    }

    private void verifierAcces(Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean estAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (estAdmin) return;

        String emailConnecte = authentication.getName();
        Utilisateur utilisateurConnecte = utilisateurService.obtenirParEmail(emailConnecte);

        if (!utilisateurConnecte.getId().equals(userId)) {
            throw new AccessDeniedException("Accès refusé à cette ressource.");
        }
    }
}