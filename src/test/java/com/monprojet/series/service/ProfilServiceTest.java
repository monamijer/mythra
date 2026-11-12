package com.monprojet.series.service;

import com.monprojet.series.dto.request.ChangerMonMotDePasseRequest;
import com.monprojet.series.dto.request.ModifierProfilRequest;
import com.monprojet.series.dto.response.AuthResponse;
import com.monprojet.series.entity.PhotoProfil;
import com.monprojet.series.entity.Utilisateur;
import com.monprojet.series.exception.BusinessException;
import com.monprojet.series.exception.ResourceNotFoundException;
import com.monprojet.series.repository.PhotoProfilRepository;
import com.monprojet.series.repository.UtilisateurRepository;
import com.monprojet.series.security.JwtService;
import com.monprojet.series.security.UtilisateurDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private PhotoProfilRepository photoProfilRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private UtilisateurDetailsService utilisateurDetailsService;

    @InjectMocks private ProfilService profilService;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    private Utilisateur alice() {
        return Utilisateur.builder().id(2L).pseudo("alice").email("alice@example.com").motDePasse("HASH").build();
    }

    // ---------- Profile ----------

    @Test
    void modifierProfil_metAJourLIdentiteEtRenvoieUnNouveauToken() {
        Utilisateur alice = alice();
        UserDetails details = User.withUsername("nouvelle@example.com").password("x").roles("USER").build();
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(utilisateurRepository.existsByEmail("nouvelle@example.com")).thenReturn(false);
        when(utilisateurRepository.existsByPseudo("alicia")).thenReturn(false);
        when(utilisateurRepository.save(any(Utilisateur.class))).then(returnsFirstArg());
        when(utilisateurDetailsService.construire(alice)).thenReturn(details);
        when(jwtService.genererToken(details)).thenReturn("NOUVEAU-JWT");

        AuthResponse reponse = profilService.modifierProfil(2L,
                new ModifierProfilRequest("  alicia ", " nouvelle@example.com "));

        assertThat(alice.getPseudo()).isEqualTo("alicia");
        assertThat(alice.getEmail()).isEqualTo("nouvelle@example.com");
        assertThat(reponse.token()).isEqualTo("NOUVEAU-JWT");
        assertThat(reponse.pseudo()).isEqualTo("alicia");
    }

    @Test
    void modifierProfil_refuseUnEmailDejaUtilise() {
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice()));
        when(utilisateurRepository.existsByEmail("pris@example.com")).thenReturn(true);

        assertThatThrownBy(() -> profilService.modifierProfil(2L, new ModifierProfilRequest("alice", "pris@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email");
        verify(utilisateurRepository, never()).save(any(Utilisateur.class));
    }

    @Test
    void modifierProfil_refuseUnPseudoDejaUtilise() {
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice()));
        when(utilisateurRepository.existsByPseudo("bob")).thenReturn(true);

        assertThatThrownBy(() -> profilService.modifierProfil(2L, new ModifierProfilRequest("bob", "alice@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pseudo");
        verify(utilisateurRepository, never()).save(any(Utilisateur.class));
    }

    @Test
    void modifierProfil_neCherchePasDeDoublonQuandRienNeChange() {
        Utilisateur alice = alice();
        UserDetails details = User.withUsername("alice@example.com").password("x").roles("USER").build();
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(utilisateurRepository.save(any(Utilisateur.class))).then(returnsFirstArg());
        when(utilisateurDetailsService.construire(alice)).thenReturn(details);
        when(jwtService.genererToken(details)).thenReturn("JWT");

        profilService.modifierProfil(2L, new ModifierProfilRequest("alice", "alice@example.com"));

        verify(utilisateurRepository, never()).existsByEmail(any());
        verify(utilisateurRepository, never()).existsByPseudo(any());
    }

    // ---------- Password ----------

    @Test
    void changerMotDePasse_refuseUnMauvaisMotDePasseActuel() {
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice()));
        when(passwordEncoder.matches("faux-faux", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> profilService.changerMotDePasse(2L,
                new ChangerMonMotDePasseRequest("faux-faux", "nouveau5678")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actuel");
        verify(utilisateurRepository, never()).save(any(Utilisateur.class));
    }

    @Test
    void changerMotDePasse_refuseUnNouveauMotDePasseIdentiqueALAncien() {
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice()));
        when(passwordEncoder.matches("ancien1234", "HASH")).thenReturn(true);

        assertThatThrownBy(() -> profilService.changerMotDePasse(2L,
                new ChangerMonMotDePasseRequest("ancien1234", "ancien1234")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("différent");
    }

    @Test
    void changerMotDePasse_enregistreLeNouveauHash() {
        Utilisateur alice = alice();
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("ancien1234", "HASH")).thenReturn(true);
        when(passwordEncoder.matches("nouveau5678", "HASH")).thenReturn(false);
        when(passwordEncoder.encode("nouveau5678")).thenReturn("NOUVEAU-HASH");

        profilService.changerMotDePasse(2L, new ChangerMonMotDePasseRequest("ancien1234", "nouveau5678"));

        assertThat(alice.getMotDePasse()).isEqualTo("NOUVEAU-HASH");
        verify(utilisateurRepository).save(alice);
    }

    // ---------- Photo ----------

    @Test
    void definirPhoto_accepteUnJpegEtMemoriseLaVersion() {
        Utilisateur alice = alice();
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(photoProfilRepository.findById(2L)).thenReturn(Optional.empty());
        when(utilisateurRepository.save(any(Utilisateur.class))).then(returnsFirstArg());

        Utilisateur resultat = profilService.definirPhoto(2L, JPEG);

        ArgumentCaptor<PhotoProfil> capture = ArgumentCaptor.forClass(PhotoProfil.class);
        verify(photoProfilRepository).save(capture.capture());
        assertThat(capture.getValue().getUtilisateurId()).isEqualTo(2L);
        assertThat(capture.getValue().getTypeContenu()).isEqualTo("image/jpeg");
        assertThat(capture.getValue().getDonnees()).isEqualTo(JPEG);
        assertThat(resultat.getPhotoVersion()).isNotNull();
    }

    @Test
    void definirPhoto_refuseUnFichierQuiNEstPasUneImage() {
        byte[] texte = "<svg onload=alert(1)></svg>".getBytes();

        assertThatThrownBy(() -> profilService.definirPhoto(2L, texte))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Format");
        verify(photoProfilRepository, never()).save(any(PhotoProfil.class));
    }

    @Test
    void definirPhoto_refuseUnFichierTropGros() {
        byte[] enorme = new byte[ProfilService.TAILLE_MAX_PHOTO + 1];
        System.arraycopy(JPEG, 0, enorme, 0, JPEG.length);

        assertThatThrownBy(() -> profilService.definirPhoto(2L, enorme))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1 Mo");
    }

    @Test
    void definirPhoto_refuseUnFichierVide() {
        assertThatThrownBy(() -> profilService.definirPhoto(2L, new byte[0]))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun fichier");
    }

    @Test
    void supprimerPhoto_efface_etRemetLaVersionANull() {
        Utilisateur alice = alice();
        alice.setPhotoVersion(123L);
        PhotoProfil photo = new PhotoProfil(2L, "image/jpeg", JPEG);
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(photoProfilRepository.findById(2L)).thenReturn(Optional.of(photo));
        when(utilisateurRepository.save(any(Utilisateur.class))).then(returnsFirstArg());

        Utilisateur resultat = profilService.supprimerPhoto(2L);

        verify(photoProfilRepository).delete(photo);
        assertThat(resultat.getPhotoVersion()).isNull();
    }

    @Test
    void obtenirPhoto_leveUneErreurQuandIlNyEnAPas() {
        when(photoProfilRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profilService.obtenirPhoto(2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- Format detection (file signature, not file name) ----------

    @Test
    void detecterType_reconnaitJpegPngEtWebp() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};

        assertThat(ProfilService.detecterType(JPEG)).isEqualTo("image/jpeg");
        assertThat(ProfilService.detecterType(png)).isEqualTo("image/png");
        assertThat(ProfilService.detecterType(webp)).isEqualTo("image/webp");
    }

    @Test
    void detecterType_refuseGifSvgEtContenuTropCourt() {
        assertThat(ProfilService.detecterType("GIF89a......".getBytes())).isNull();
        assertThat(ProfilService.detecterType("<svg></svg>".getBytes())).isNull();
        assertThat(ProfilService.detecterType(new byte[] {(byte) 0xFF, (byte) 0xD8})).isNull();
        assertThat(ProfilService.detecterType(new byte[0])).isNull();
    }
}
