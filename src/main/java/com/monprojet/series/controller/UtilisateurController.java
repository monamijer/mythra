package com.monprojet.series.controller;

import com.monprojet.series.dto.request.ChangerMonMotDePasseRequest;
import com.monprojet.series.dto.request.ModifierProfilRequest;
import com.monprojet.series.dto.response.AuthResponse;
import com.monprojet.series.dto.response.UtilisateurResponse;
import com.monprojet.series.entity.PhotoProfil;
import com.monprojet.series.mapper.UtilisateurMapper;
import com.monprojet.series.service.ProfilService;
import com.monprojet.series.service.UtilisateurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;
    private final ProfilService profilService;

    // Listing every account is an administration task
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UtilisateurResponse> lister() {
        return utilisateurService.listerTous().stream().map(UtilisateurMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securite.peutAcceder(#id)")
    public UtilisateurResponse obtenir(@PathVariable Long id) {
        return UtilisateurMapper.toResponse(utilisateurService.obtenirParId(id));
    }

    // ---------- Profile: personal actions, owner only (even admins cannot act for someone else) ----------

    @PutMapping("/{id}/profil")
    @PreAuthorize("@securite.estUtilisateurCourant(#id)")
    public AuthResponse modifierProfil(@PathVariable Long id, @Valid @RequestBody ModifierProfilRequest request) {
        return profilService.modifierProfil(id, request);
    }

    @PutMapping("/{id}/mot-de-passe")
    @PreAuthorize("@securite.estUtilisateurCourant(#id)")
    public ResponseEntity<Void> changerMotDePasse(
            @PathVariable Long id, @Valid @RequestBody ChangerMonMotDePasseRequest request) {
        profilService.changerMotDePasse(id, request);
        return ResponseEntity.noContent().build();
    }

    // ---------- Profile photo ----------

    // POST rather than PUT: multipart bodies are only guaranteed on POST
    @PostMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@securite.estUtilisateurCourant(#id)")
    public UtilisateurResponse definirPhoto(
            @PathVariable Long id, @RequestParam("fichier") MultipartFile fichier) throws IOException {
        return UtilisateurMapper.toResponse(profilService.definirPhoto(id, fichier.getBytes()));
    }

    // The front end fetches it with the JWT, so the photo is not publicly reachable
    @GetMapping("/{id}/photo")
    @PreAuthorize("@securite.peutAcceder(#id)")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        PhotoProfil photo = profilService.obtenirPhoto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getTypeContenu()))
                // The client appends ?v=<photoVersion>, so a new upload changes the URL
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(photo.getDonnees());
    }

    // An admin may remove a photo (moderation); only the owner may upload one
    @DeleteMapping("/{id}/photo")
    @PreAuthorize("@securite.peutAcceder(#id)")
    public ResponseEntity<Void> supprimerPhoto(@PathVariable Long id) {
        profilService.supprimerPhoto(id);
        return ResponseEntity.noContent().build();
    }
}
