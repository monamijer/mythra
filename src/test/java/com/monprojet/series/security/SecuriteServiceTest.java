package com.monprojet.series.security;

import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.repository.SaisonRepository;
import com.monprojet.series.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecuriteServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private SaisonRepository saisonRepository;

    @InjectMocks private SecuriteService securite;

    @BeforeEach
    void avant() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private void connecter(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private Utilisateur utilisateur(Long id, String email) {
        return Utilisateur.builder().id(id).pseudo("user" + id).email(email).motDePasse("HASH").build();
    }

    // ---------- peutAcceder: {userId} of the URL versus the authenticated user ----------

    @Test
    void peutAcceder_accepteUnAdministrateurPourN_importeQuelUtilisateur() {
        connecter("admin@example.com", "ROLE_ADMIN");

        assertThat(securite.peutAcceder(42L)).isTrue();
        verifyNoInteractions(utilisateurRepository);
    }

    @Test
    void peutAcceder_accepteUnUtilisateurPourSesPropresDonnees() {
        connecter("alice@example.com", "ROLE_USER");
        when(utilisateurRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(utilisateur(5L, "alice@example.com")));

        assertThat(securite.peutAcceder(5L)).isTrue();
    }

    @Test
    void peutAcceder_refuseUnUtilisateurPourLesDonneesDUnAutre() {
        connecter("alice@example.com", "ROLE_USER");
        when(utilisateurRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(utilisateur(5L, "alice@example.com")));

        assertThat(securite.peutAcceder(6L)).isFalse();
    }

    @Test
    void peutAcceder_refuseQuandPersonneNEstConnecte() {
        assertThat(securite.peutAcceder(5L)).isFalse();
        verifyNoInteractions(utilisateurRepository);
    }

    @Test
    void peutAcceder_refuseUnVisiteurAnonyme() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "cle", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(securite.peutAcceder(5L)).isFalse();
    }

    @Test
    void peutAcceder_refuseUnTokenQuiPointeVersUnUtilisateurSupprime() {
        connecter("fantome@example.com", "ROLE_USER");
        when(utilisateurRepository.findByEmail("fantome@example.com")).thenReturn(Optional.empty());

        assertThat(securite.peutAcceder(5L)).isFalse();
    }

    // ---------- estUtilisateurCourant: like peutAcceder, but admins get no exception ----------

    @Test
    void estUtilisateurCourant_refuseUnAdministrateurPourUnAutreCompte() {
        connecter("admin@example.com", "ROLE_ADMIN");
        when(utilisateurRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(utilisateur(1L, "admin@example.com")));

        assertThat(securite.estUtilisateurCourant(42L)).isFalse();
    }

    @Test
    void estUtilisateurCourant_accepteUnUtilisateurPourSonPropreCompte() {
        connecter("alice@example.com", "ROLE_USER");
        when(utilisateurRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(utilisateur(5L, "alice@example.com")));

        assertThat(securite.estUtilisateurCourant(5L)).isTrue();
    }

    @Test
    void estUtilisateurCourant_refuseQuandPersonneNEstConnecte() {
        assertThat(securite.estUtilisateurCourant(5L)).isFalse();
        verifyNoInteractions(utilisateurRepository);
    }

    // ---------- possedeSaison: a season belongs to the owner of its series ----------

    @Test
    void possedeSaison_accepteUnAdministrateur() {
        connecter("admin@example.com", "ROLE_ADMIN");

        assertThat(securite.possedeSaison(3L)).isTrue();
        verifyNoInteractions(saisonRepository);
    }

    @Test
    void possedeSaison_accepteLeProprietaireDeLaSerie() {
        connecter("alice@example.com", "ROLE_USER");
        when(utilisateurRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(utilisateur(5L, "alice@example.com")));
        when(saisonRepository.existsByIdAndSerie_Utilisateur_Id(3L, 5L)).thenReturn(true);

        assertThat(securite.possedeSaison(3L)).isTrue();
    }

    @Test
    void possedeSaison_refuseUneSaisonQuiNAppartientPasAUtilisateur() {
        connecter("alice@example.com", "ROLE_USER");
        when(utilisateurRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(utilisateur(5L, "alice@example.com")));
        when(saisonRepository.existsByIdAndSerie_Utilisateur_Id(3L, 5L)).thenReturn(false);

        assertThat(securite.possedeSaison(3L)).isFalse();
    }

    @Test
    void possedeSaison_refuseQuandPersonneNEstConnecte() {
        assertThat(securite.possedeSaison(3L)).isFalse();
        verifyNoInteractions(saisonRepository);
    }
}
