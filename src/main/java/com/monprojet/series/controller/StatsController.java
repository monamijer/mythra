package com.monprojet.series.controller;

import com.monprojet.series.dto.response.StatsUtilisateurResponse;
import com.monprojet.series.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/utilisateurs/{userId}/stats")
@PreAuthorize("@securite.peutAcceder(#userId)")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public StatsUtilisateurResponse stats(@PathVariable Long userId) {
        return statsService.calculer(userId);
    }
}
