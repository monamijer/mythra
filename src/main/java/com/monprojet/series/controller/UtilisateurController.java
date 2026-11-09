package com.monprojet.series.controller;

import com.monprojet.series.dto.response.UtilisateurResponse;
import com.monprojet.series.mapper.UtilisateurMapper;
import com.monprojet.series.service.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

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
}
