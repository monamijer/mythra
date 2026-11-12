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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What a user may change about their own account: identity, password and photo. */
@Service
@RequiredArgsConstructor
@Transactional
public class ProfilService {

    /** Largest photo accepted, in bytes. The browser shrinks pictures far below this. */
    public static final int TAILLE_MAX_PHOTO = 1_048_576;

    private final UtilisateurRepository utilisateurRepository;
    private final PhotoProfilRepository photoProfilRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UtilisateurDetailsService utilisateurDetailsService;

    /**
     * Updates pseudo and email. The JWT identifies the user by email, so a new
     * token is issued with the answer: the caller must replace the old one.
     */
    public AuthResponse modifierProfil(Long id, ModifierProfilRequest request) {
        Utilisateur u = obtenir(id);
        String pseudo = request.pseudo().trim();
        String email = request.email().trim();

        if (!email.equals(u.getEmail()) && utilisateurRepository.existsByEmail(email)) {
            throw new BusinessException("Cet email est déjà utilisé.");
        }
        if (!pseudo.equals(u.getPseudo()) && utilisateurRepository.existsByPseudo(pseudo)) {
            throw new BusinessException("Ce pseudo est déjà utilisé.");
        }

        u.setPseudo(pseudo);
        u.setEmail(email);
        utilisateurRepository.save(u);

        String token = jwtService.genererToken(utilisateurDetailsService.construire(u));
        return new AuthResponse(token, u.getId(), u.getPseudo(), u.getRole().name(),
                u.getStatut().name(), "Profil mis à jour.");
    }

    public void changerMotDePasse(Long id, ChangerMonMotDePasseRequest request) {
        Utilisateur u = obtenir(id);
        // 409 (not 401/403) on purpose: the front end treats those as an expired session
        if (!passwordEncoder.matches(request.ancienMotDePasse(), u.getMotDePasse())) {
            throw new BusinessException("Le mot de passe actuel est incorrect.");
        }
        if (passwordEncoder.matches(request.nouveauMotDePasse(), u.getMotDePasse())) {
            throw new BusinessException("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        u.setMotDePasse(passwordEncoder.encode(request.nouveauMotDePasse()));
        utilisateurRepository.save(u);
    }

    // ---------- Photo ----------

    public Utilisateur definirPhoto(Long id, byte[] donnees) {
        // The file is checked first: it is cheaper than loading the user
        if (donnees == null || donnees.length == 0) {
            throw new BusinessException("Aucun fichier reçu.");
        }
        if (donnees.length > TAILLE_MAX_PHOTO) {
            throw new BusinessException("La photo dépasse la taille maximale de 1 Mo.");
        }
        String type = detecterType(donnees);
        if (type == null) {
            throw new BusinessException("Format non pris en charge : utilisez une image JPEG, PNG ou WebP.");
        }

        Utilisateur u = obtenir(id);
        PhotoProfil photo = photoProfilRepository.findById(id).orElseGet(PhotoProfil::new);
        photo.setUtilisateurId(id);
        photo.setTypeContenu(type);
        photo.setDonnees(donnees);
        photoProfilRepository.save(photo);

        u.setPhotoVersion(System.currentTimeMillis());
        return utilisateurRepository.save(u);
    }

    @Transactional(readOnly = true)
    public PhotoProfil obtenirPhoto(Long id) {
        return photoProfilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune photo de profil pour l'utilisateur : id=" + id));
    }

    public Utilisateur supprimerPhoto(Long id) {
        Utilisateur u = obtenir(id);
        photoProfilRepository.findById(id).ifPresent(photoProfilRepository::delete);
        u.setPhotoVersion(null);
        return utilisateurRepository.save(u);
    }

    /**
     * Identifies the image by its first bytes (magic numbers), never by the
     * file name or the Content-Type sent by the client. Only JPEG, PNG and WebP
     * are allowed: SVG is refused on purpose because it can carry scripts.
     *
     * @return the MIME type, or null when the content is not a supported image
     */
    static String detecterType(byte[] d) {
        if (d.length >= 3
                && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8
                && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 12
                && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private Utilisateur obtenir(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : id=" + id));
    }
}
