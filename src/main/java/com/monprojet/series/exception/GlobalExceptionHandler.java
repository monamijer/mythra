// GlobalExceptionHandler.java
package com.monprojet.series.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.monprojet.series.exception.TmdbIndisponibleException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> gererIntrouvable(ResourceNotFoundException ex) {
        var body = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(), "Ressource introuvable", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> gererRegleMetier(BusinessException ex) {
        var body = new ApiErrorResponse(
                HttpStatus.CONFLICT.value(), "Règle métier violée", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Triggered by @Valid failures on @RequestBody DTOs (SerieRequest,
    // UtilisateurRequest...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> gererValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " : " + err.getDefaultMessage())
                .toList();
        var body = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(), "Requête invalide",
                "Un ou plusieurs champs sont invalides", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> gererRouteIntrouvable(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        var body = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(), "Route introuvable", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Thrown by @PreAuthorize checks: must be 403, not swallowed by the catch-all below
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> gererAccesRefuse(AccessDeniedException ex) {
        var body = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(), "Accès refusé",
                "Vous n'avez pas le droit d'accéder à cette ressource.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // Safety net — never leaks a raw stack trace to the client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> gererErreurInattendue(Exception ex) {
        var body = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erreur interne",
                "Une erreur inattendue est survenue");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(TmdbIndisponibleException.class)
    public ResponseEntity<ApiErrorResponse> gererTmdbIndisponible(TmdbIndisponibleException ex) {
        var body = new ApiErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(), "Service externe indisponible", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}