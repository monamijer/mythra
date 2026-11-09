// TmdbImportController.java
package com.monprojet.series.controller;

import com.monprojet.series.dto.response.SerieResponse;
import com.monprojet.series.mapper.SerieMapper;
import com.monprojet.series.service.TmdbService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/utilisateurs/{userId}/tmdb")
@PreAuthorize("@securite.peutAcceder(#userId)")
@RequiredArgsConstructor
public class TmdbImportController {

    private final TmdbService tmdbService;

    @PostMapping("/importer/{tmdbId}")
    public ResponseEntity<SerieResponse> importer(@PathVariable Long userId, @PathVariable Long tmdbId) {
        var serieImportee = tmdbService.importerSerie(userId, tmdbId);
        return ResponseEntity.status(HttpStatus.CREATED).body(SerieMapper.toResponse(serieImportee));
    }
}