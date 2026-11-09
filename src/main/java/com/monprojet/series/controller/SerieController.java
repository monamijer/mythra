// SerieController.java
package com.monprojet.series.controller;

import com.monprojet.series.dto.request.AvisRequest;
import com.monprojet.series.dto.request.SerieRequest;
import com.monprojet.series.dto.response.SerieResponse;
import com.monprojet.series.entity.Serie;
import com.monprojet.series.entity.StatutVisionnage;
import com.monprojet.series.mapper.SerieMapper;
import com.monprojet.series.service.SerieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs/{userId}/series")
@PreAuthorize("@securite.peutAcceder(#userId)")
@RequiredArgsConstructor
public class SerieController {

    private final SerieService serieService;

    @GetMapping
    public List<SerieResponse> lister(
            @PathVariable Long userId,
            @RequestParam(required = false) StatutVisionnage statut) {
        var series = (statut == null)
                ? serieService.listerToutes(userId)
                : serieService.listerParStatut(userId, statut);
        return series.stream().map(SerieMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public SerieResponse obtenir(@PathVariable Long userId, @PathVariable Long id) {
        return SerieMapper.toResponse(serieService.obtenirParId(id, userId));
    }

    @PostMapping
    public ResponseEntity<SerieResponse> creer(
            @PathVariable Long userId, @Valid @RequestBody SerieRequest request) {
        Serie creee = serieService.creer(userId, SerieMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(SerieMapper.toResponse(creee));
    }

    @PutMapping("/{id}")
    public SerieResponse modifier(
            @PathVariable Long userId, @PathVariable Long id, @Valid @RequestBody SerieRequest request) {
        return SerieMapper.toResponse(serieService.modifier(id, userId, SerieMapper.toEntity(request)));
    }

    @PatchMapping("/{id}/statut")
    public SerieResponse changerStatut(
            @PathVariable Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) StatutVisionnage statut) {
        return SerieMapper.toResponse(serieService.changerStatut(id, userId, statut));
    }

    /**
     * Endpoint dédié à la note personnelle et à la critique.
     * Body : { notePersonnelle: 1-10, critique: "..." }
     * Valeur null = effacer le champ correspondant.
     */
    @PatchMapping("/{id}/avis")
    public SerieResponse changerAvis(
            @PathVariable Long userId,
            @PathVariable Long id,
            @Valid @RequestBody AvisRequest request) {
        return SerieMapper.toResponse(
                serieService.changerAvis(id, userId, request.notePersonnelle(), request.critique())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long userId, @PathVariable Long id) {
        serieService.supprimer(id, userId);
        return ResponseEntity.noContent().build();
    }
}
