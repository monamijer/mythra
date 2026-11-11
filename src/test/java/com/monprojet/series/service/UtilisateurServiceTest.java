package com.monprojet.series.service;

import com.monprojet.series.entity.RoleUtilisateur;
import com.monprojet.series.entity.Serie;
import com.monprojet.series.entity.StatutUtilisateur;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.exception.BusinessException;
import com.monprojet.series.exception.ResourceNotFoundException;
import com.monprojet.series.repository.SerieRepository;
import com.monprojet.series.repository.UtilisateurRepository;
import com.monprojet.series.repository.VisionnageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilisateurServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SerieRepository serieRepository;
    @Mock private VisionnageRepository visionnageRepository;

    @InjectMocks private UtilisateurService utilisateurService;

    private Utilisateur utilisateur(Long id, RoleUtilisateur role, StatutUtilisateur statut) {
        return Utilisateur.builder()
                .id(id)
                .pseudo("user" + id)
                .email("user" + id + "@example.com")
                .motDePasse("HASH")
                .role(role)
                .statut(statut)
                .build();
    }

    // ---------- Account workflow ----------

    @Test
    void approuver_passeLeCompteAuStatutApprouve() {
        Utilisateur alice = utilisateur(2L, RoleUtilisateur.USER, StatutUtilisateur.EN_ATTENTE);
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(utilisateurRepository.save(any(Utilisateur.class))).then(returnsFirstArg());

        Utilisateur resultat = utilisateurService.approuver(2L);

        assertThat(resultat.getStatut()).isEqualTo(StatutUtilisateur.APPROUVE);
    }

    @Test
    void refuser_passeLeCompteAuStatutRefuse() {
        Utilisateur alice = utilisateur(2L, RoleUtilisateur.USER, StatutUtilisateur.APPROUVE);
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(utilisateurRepository.save(any(Utilisateur.class))).then(returnsFirstArg());

        Utilisateur resultat = utilisateurService.refuser(2L);

        assertThat(resultat.getStatut()).isEqualTo(StatutUtilisateur.REFUSE);
    }

    @Test
    void obtenirParId_leveUneExceptionSiLUtilisateurNExistePas() {
        when(utilisateurRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> utilisateurService.obtenirParId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changerMotDePasse_enregistreUnHashEtJamaisLeMotDePasseEnClair() {
        Utilisateur alice = utilisateur(2L, RoleUtilisateur.USER, StatutUtilisateur.APPROUVE);
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(passwordEncoder.encode("nouveau1234")).thenReturn("NEWHASH");
        when(utilisateurRepository.save(any(Utilisateur.class))).then(returnsFirstArg());

        Utilisateur resultat = utilisateurService.changerMotDePasse(2L, "nouveau1234");

        assertThat(resultat.getMotDePasse()).isEqualTo("NEWHASH");
    }

    // ---------- Last active administrator is protected ----------

    @Test
    void retrograder_refuseLeDernierAdministrateurActif() {
        Utilisateur admin = utilisateur(1L, RoleUtilisateur.ADMIN, StatutUtilisateur.APPROUVE);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(utilisateurRepository.findAll()).thenReturn(List.of(admin));

        assertThatThrownBy(() -> utilisateurService.retrograder(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dernier administrateur");

        verify(utilisateurRepository, never()).save(any());
        assertThat(admin.getRole()).isEqualTo(RoleUtilisateur.ADMIN);
    }

    @Test
    void refuser_refuseLeDernierAdministrateurActif() {
        Utilisateur admin = utilisateur(1L, RoleUtilisateur.ADMIN, StatutUtilisateur.APPROUVE);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(utilisateurRepository.findAll()).thenReturn(List.of(admin));

        assertThatThrownBy(() -> utilisateurService.refuser(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dernier administrateur");

        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void supprimer_refuseLeDernierAdministrateurActif() {
        Utilisateur admin = utilisateur(1L, RoleUtilisateur.ADMIN, StatutUtilisateur.APPROUVE);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(utilisateurRepository.findAll()).thenReturn(List.of(admin));

        assertThatThrownBy(() -> utilisateurService.supprimer(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dernier administrateur");

        // Nothing was deleted
        verifyNoInteractions(visionnageRepository, serieRepository);
        verify(utilisateurRepository, never()).delete(any());
    }

    @Test
    void retrograder_estPossibleQuandUnAutreAdministrateurActifExiste() {
        Utilisateur admin1 = utilisateur(1L, RoleUtilisateur.ADMIN, StatutUtilisateur.APPROUVE);
        Utilisateur admin2 = utilisateur(2L, RoleUtilisateur.ADMIN, StatutUtilisateur.APPROUVE);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(admin1));
        when(utilisateurRepository.findAll()).thenReturn(List.of(admin1, admin2));
        when(utilisateurRepository.save(any(Utilisateur.class))).then(returnsFirstArg());

        Utilisateur resultat = utilisateurService.retrograder(1L);

        assertThat(resultat.getRole()).isEqualTo(RoleUtilisateur.USER);
    }

    // ---------- Deleting a user removes everything they own, in a safe order ----------

    @Test
    void supprimer_effaceLesVisionnagesPuisLesSeriesPuisLeCompte() {
        Utilisateur alice = utilisateur(2L, RoleUtilisateur.USER, StatutUtilisateur.APPROUVE);
        Serie serie10 = Serie.builder().id(10L).build();
        Serie serie11 = Serie.builder().id(11L).build();
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(serieRepository.findByUtilisateurId(2L)).thenReturn(List.of(serie10, serie11));

        utilisateurService.supprimer(2L);

        InOrder ordre = inOrder(visionnageRepository, serieRepository, utilisateurRepository);
        ordre.verify(visionnageRepository).supprimerParUtilisateur(2L);
        ordre.verify(visionnageRepository).supprimerParSerie(10L);
        ordre.verify(visionnageRepository).supprimerParSerie(11L);
        ordre.verify(serieRepository).delete(serie10);
        ordre.verify(serieRepository).delete(serie11);
        ordre.verify(utilisateurRepository).delete(alice);
    }
}
