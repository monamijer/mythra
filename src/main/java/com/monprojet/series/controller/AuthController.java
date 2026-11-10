// AuthController.java
package com.monprojet.series.controller;

import com.monprojet.series.dto.request.ConnexionRequest;
import com.monprojet.series.dto.request.InscriptionRequest;
import com.monprojet.series.dto.response.AuthResponse;
import com.monprojet.series.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/inscription")
    public ResponseEntity<AuthResponse> inscription(@Valid @RequestBody InscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.inscrire(request));
    }

    @PostMapping("/connexion")
    public AuthResponse connexion(@Valid @RequestBody ConnexionRequest request) {
        return authService.connecter(request);
    }
}
