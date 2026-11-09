// VisionnageController.java
package com.monprojet.series.controller;

import com.monprojet.series.dto.response.MarquerVisionnageResponse;
import com.monprojet.series.dto.response.ProgressionResponse;
import com.monprojet.series.dto.response.ProgressionResponse.StatutProgression;
import com.monprojet.series.service.VisionnageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs/{userId}")
@PreAuthorize("@securite.peutAcceder(#userId)")
@RequiredArgsConstructor
public class VisionnageController {

    private final VisionnageService visionnageService;

    @PostMapping("/visionnages/{episodeId}")
    public ResponseEntity<MarquerVisionnageResponse> marquerCommeVu(
            @PathVariable Long userId, @PathVariable Long episodeId) {
        var reponse = visionnageService.marquerCommeVu(userId, episodeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(reponse);
    }

    @DeleteMapping("/visionnages/{episodeId}")
    public ResponseEntity<Void> annuler(@PathVariable Long userId, @PathVariable Long episodeId) {
        visionnageService.annulerVisionnage(userId, episodeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/series-en-cours")
    public List<ProgressionResponse> seriesEnCours(@PathVariable Long userId) {
        return visionnageService.listerParStatut(userId, StatutProgression.EN_COURS);
    }

    @GetMapping("/series-terminees")
    public List<ProgressionResponse> seriesTerminees(@PathVariable Long userId) {
        return visionnageService.listerParStatut(userId, StatutProgression.TERMINEE);
    }

    @GetMapping("/progression/{serieId}")
    public ProgressionResponse progression(@PathVariable Long userId, @PathVariable Long serieId) {
        return visionnageService.calculerProgression(userId, serieId);
    }
}