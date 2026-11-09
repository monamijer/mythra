// SecuriteService.java
package com.monprojet.series.security;

import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.repository.SaisonRepository;
import com.monprojet.series.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single place where "who may touch what" is decided. Controllers reference it
 * from @PreAuthorize("@securite.peutAcceder(#userId)"), so no controller has to
 * repeat the check by hand.
 */
@Component("securite")
@RequiredArgsConstructor
public class SecuriteService {

    private final UtilisateurRepository utilisateurRepository;
    private final SaisonRepository saisonRepository;

    /** True for an admin, or when {userId} is the authenticated user's own id. */
    public boolean peutAcceder(Long userId) {
        Authentication authentication = authentificationCourante();
        if (authentication == null) return false;
        if (estAdmin(authentication)) return true;
        return idUtilisateurCourant(authentication)
                .map(id -> id.equals(userId))
                .orElse(false);
    }

    /**
     * True for an admin, or when the season belongs to a series owned by the
     * authenticated user. An unknown season gives false as well, so a caller
     * cannot tell "does not exist" from "belongs to someone else".
     */
    public boolean possedeSaison(Long saisonId) {
        Authentication authentication = authentificationCourante();
        if (authentication == null) return false;
        if (estAdmin(authentication)) return true;
        return idUtilisateurCourant(authentication)
                .map(id -> saisonRepository.existsByIdAndSerie_Utilisateur_Id(saisonId, id))
                .orElse(false);
    }

    private Authentication authentificationCourante() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (authentication != null && authentication.isAuthenticated()) ? authentication : null;
    }

    private boolean estAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(autorite -> autorite.getAuthority().equals("ROLE_ADMIN"));
    }

    // The JWT carries the email only, so it is resolved to an id here.
    private Optional<Long> idUtilisateurCourant(Authentication authentication) {
        return utilisateurRepository.findByEmail(authentication.getName()).map(Utilisateur::getId);
    }
}
