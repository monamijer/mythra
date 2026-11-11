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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UtilisateurDetailsService utilisateurDetailsService;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    private Utilisateur utilisateur(StatutUtilisateur statut) {
        return Utilisateur.builder()
                .id(1L)
                .pseudo("alice")
                .email("alice@example.com")
                .motDePasse("HASH")
                .statut(statut)
                .build();
    }

    // ---------- Inscription ----------

    @Test
    void inscrire_creeUnCompteEnAttenteAvecUnMotDePasseHache() {
        when(utilisateurRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(utilisateurRepository.existsByPseudo("alice")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse123")).thenReturn("HASH");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> {
            Utilisateur u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        AuthResponse reponse = authService.inscrire(
                new InscriptionRequest("alice", "alice@example.com", "motdepasse123"));

        // No token before an administrator approves the account
        assertThat(reponse.token()).isNull();
        assertThat(reponse.statut()).isEqualTo("EN_ATTENTE");
        assertThat(reponse.role()).isEqualTo("USER");

        // The password is stored hashed, never in clear text
        ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepository).save(captor.capture());
        assertThat(captor.getValue().getMotDePasse()).isEqualTo("HASH").isNotEqualTo("motdepasse123");
    }

    @Test
    void inscrire_refuseUnEmailDejaUtilise() {
        when(utilisateurRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.inscrire(
                new InscriptionRequest("alice", "alice@example.com", "motdepasse123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email");

        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void inscrire_refuseUnPseudoDejaUtilise() {
        when(utilisateurRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(utilisateurRepository.existsByPseudo("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.inscrire(
                new InscriptionRequest("alice", "alice@example.com", "motdepasse123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pseudo");

        verify(utilisateurRepository, never()).save(any());
    }

    // ---------- Connexion ----------

    @Test
    void connecter_refuseUnMauvaisMotDePasse() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.connecter(new ConnexionRequest("alice@example.com", "faux")))
                .isInstanceOf(IdentifiantsInvalidesException.class);

        verify(jwtService, never()).genererToken(any());
    }

    @Test
    void connecter_refuseUnCompteEnAttente() {
        when(utilisateurRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(utilisateur(StatutUtilisateur.EN_ATTENTE)));

        assertThatThrownBy(() -> authService.connecter(
                new ConnexionRequest("alice@example.com", "motdepasse123")))
                .isInstanceOf(CompteNonApprouveException.class)
                .hasMessageContaining("attente");

        verify(jwtService, never()).genererToken(any());
    }

    @Test
    void connecter_refuseUnCompteRefuse() {
        when(utilisateurRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(utilisateur(StatutUtilisateur.REFUSE)));

        assertThatThrownBy(() -> authService.connecter(
                new ConnexionRequest("alice@example.com", "motdepasse123")))
                .isInstanceOf(CompteNonApprouveException.class)
                .hasMessageContaining("refusé");

        verify(jwtService, never()).genererToken(any());
    }

    @Test
    void connecter_retourneUnTokenPourUnCompteApprouve() {
        Utilisateur alice = utilisateur(StatutUtilisateur.APPROUVE);
        UserDetails details = User.withUsername("alice@example.com").password("HASH").roles("USER").build();
        when(utilisateurRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(utilisateurDetailsService.construire(alice)).thenReturn(details);
        when(jwtService.genererToken(details)).thenReturn("TOKEN");

        AuthResponse reponse = authService.connecter(
                new ConnexionRequest("alice@example.com", "motdepasse123"));

        assertThat(reponse.token()).isEqualTo("TOKEN");
        assertThat(reponse.statut()).isEqualTo("APPROUVE");
        assertThat(alice.getDerniereConnexion()).isNotNull();
        verify(utilisateurRepository).save(alice);
    }
}
