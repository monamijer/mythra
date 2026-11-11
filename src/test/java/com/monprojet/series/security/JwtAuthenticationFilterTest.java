package com.monprojet.series.security;

import com.monprojet.series.entity.RoleUtilisateur;
import com.monprojet.series.entity.StatutUtilisateur;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UtilisateurDetailsService utilisateurDetailsService;
    @Mock private UtilisateurRepository utilisateurRepository;

    @InjectMocks private JwtAuthenticationFilter filtre;

    @BeforeEach
    void avant() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private void appeler(String enTeteAuthorization) throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest();
        if (enTeteAuthorization != null) {
            requete.addHeader("Authorization", enTeteAuthorization);
        }
        filtre.doFilter(requete, new MockHttpServletResponse(), new MockFilterChain());
    }

    private Utilisateur alice(StatutUtilisateur statut) {
        return Utilisateur.builder()
                .id(1L).pseudo("alice").email("alice@example.com").motDePasse("HASH")
                .role(RoleUtilisateur.USER).statut(statut).build();
    }

    @Test
    void sansEnTeteAuthorization_personneNEstAuthentifie() throws Exception {
        appeler(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void enTeteSansBearer_personneNEstAuthentifie() throws Exception {
        appeler("Basic abcdef");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void tokenIllisible_personneNEstAuthentifie() throws Exception {
        when(jwtService.extraireEmail("MAUVAIS")).thenThrow(new RuntimeException("signature invalide"));

        appeler("Bearer MAUVAIS");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenValidePourUnCompteApprouve_authentifieAvecSonRole() throws Exception {
        Utilisateur alice = alice(StatutUtilisateur.APPROUVE);
        UserDetails details = User.withUsername("alice@example.com").password("HASH").roles("USER").build();
        when(jwtService.extraireEmail("TOKEN")).thenReturn("alice@example.com");
        when(utilisateurRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(utilisateurDetailsService.construire(alice)).thenReturn(details);
        when(jwtService.estValide("TOKEN", details)).thenReturn(true);

        appeler("Bearer TOKEN");

        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentification).isNotNull();
        assertThat(authentification.getName()).isEqualTo("alice@example.com");
        assertThat(authentification.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void tokenValidePourUnCompteRefuse_personneNEstAuthentifie() throws Exception {
        when(jwtService.extraireEmail("TOKEN")).thenReturn("alice@example.com");
        when(utilisateurRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(alice(StatutUtilisateur.REFUSE)));

        appeler("Bearer TOKEN");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(utilisateurDetailsService, never()).construire(any());
    }

    @Test
    void tokenValidePourUnCompteEnAttente_personneNEstAuthentifie() throws Exception {
        when(jwtService.extraireEmail("TOKEN")).thenReturn("alice@example.com");
        when(utilisateurRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(alice(StatutUtilisateur.EN_ATTENTE)));

        appeler("Bearer TOKEN");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenValidePourUnCompteSupprime_personneNEstAuthentifie() throws Exception {
        when(jwtService.extraireEmail("TOKEN")).thenReturn("fantome@example.com");
        when(utilisateurRepository.findByEmail("fantome@example.com")).thenReturn(Optional.empty());

        appeler("Bearer TOKEN");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenRejeteParLeServiceJwt_personneNEstAuthentifie() throws Exception {
        Utilisateur alice = alice(StatutUtilisateur.APPROUVE);
        UserDetails details = User.withUsername("alice@example.com").password("HASH").roles("USER").build();
        when(jwtService.extraireEmail("TOKEN")).thenReturn("alice@example.com");
        when(utilisateurRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(utilisateurDetailsService.construire(alice)).thenReturn(details);
        when(jwtService.estValide("TOKEN", details)).thenReturn(false);

        appeler("Bearer TOKEN");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
