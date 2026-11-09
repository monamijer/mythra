// SaisonController.java
package com.monprojet.series.controller;

import com.monprojet.series.dto.request.SaisonRequest;
import com.monprojet.series.dto.response.SaisonResponse;
import com.monprojet.series.mapper.SaisonMapper;
import com.monprojet.series.service.SaisonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("@securite.peutAcceder(#userId)")
@RequiredArgsConstructor
public class SaisonController {

    private final SaisonService saisonService;

    @GetMapping("/api/utilisateurs/{userId}/series/{serieId}/saisons")
    public List<SaisonResponse> listerParSerie(@PathVariable Long userId, @PathVariable Long serieId) {
        return saisonService.listerParSerie(serieId, userId).stream().map(SaisonMapper::toResponse).toList();
    }

    @PostMapping("/api/utilisateurs/{userId}/series/{serieId}/saisons")
    public ResponseEntity<SaisonResponse> creer(
            @PathVariable Long userId, @PathVariable Long serieId, @Valid @RequestBody SaisonRequest request) {
        var creee = saisonService.creer(serieId, userId, SaisonMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(SaisonMapper.toResponse(creee));
    }
}