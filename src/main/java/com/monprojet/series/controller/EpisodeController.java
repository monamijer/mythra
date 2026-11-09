// EpisodeController.java
package com.monprojet.series.controller;

import com.monprojet.series.dto.request.EpisodeRequest;
import com.monprojet.series.dto.response.EpisodeResponse;
import com.monprojet.series.mapper.EpisodeMapper;
import com.monprojet.series.service.EpisodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("@securite.possedeSaison(#saisonId)")
@RequiredArgsConstructor
public class EpisodeController {

    private final EpisodeService episodeService;

    @GetMapping("/api/saisons/{saisonId}/episodes")
    public List<EpisodeResponse> listerParSaison(@PathVariable Long saisonId) {
        return episodeService.listerParSaison(saisonId).stream().map(EpisodeMapper::toResponse).toList();
    }

    @PostMapping("/api/saisons/{saisonId}/episodes")
    public ResponseEntity<EpisodeResponse> creer(
            @PathVariable Long saisonId, @Valid @RequestBody EpisodeRequest request) {
        var cree = episodeService.creer(saisonId, EpisodeMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(EpisodeMapper.toResponse(cree));
    }
}