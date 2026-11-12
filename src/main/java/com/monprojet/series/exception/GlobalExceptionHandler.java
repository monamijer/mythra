// GlobalExceptionHandler.java
package com.monprojet.series.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/** Turns every exception into the same JSON error shape with the right HTTP status. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------- 400: the request itself is wrong ----------

    // @Valid failures on request bodies (SerieRequest, InscriptionRequest...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> gererValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " : " + err.getDefaultMessage())
                .toList();
        return reponse(HttpStatus.BAD_REQUEST, "Requête invalide",
                "Un ou plusieurs champs sont invalides", details);
    }

    // Missing body, malformed JSON, or a value that is not part of an enum
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> gererCorpsIllisible(HttpMessageNotReadableException ex) {
        return reponse(HttpStatus.BAD_REQUEST, "Requête illisible",
                "Le corps de la requête est absent, mal formé ou contient une valeur non valide.");
    }

    // e.g. ?statut=XYZ or /series/abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> gererTypeInvalide(MethodArgumentTypeMismatchException ex) {
        return reponse(HttpStatus.BAD_REQUEST, "Paramètre invalide",
                "La valeur '" + ex.getValue() + "' n'est pas valide pour le paramètre '" + ex.getName() + "'.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> gererParametreManquant(MissingServletRequestParameterException ex) {
        return reponse(HttpStatus.BAD_REQUEST, "Paramètre manquant",
                "Le paramètre '" + ex.getParameterName() + "' est obligatoire.");
    }

    // Multipart upload without the expected "fichier" part
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> gererPartieManquante(MissingServletRequestPartException ex) {
        return reponse(HttpStatus.BAD_REQUEST, "Fichier manquant",
                "Le fichier '" + ex.getRequestPartName() + "' est obligatoire.");
    }

    // Rejected by the servlet container before the controller runs (spring.servlet.multipart.*)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> gererFichierTropGros(MaxUploadSizeExceededException ex) {
        return reponse(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux",
                "Le fichier dépasse la taille maximale autorisée (1 Mo).");
    }

    // ---------- 401 / 403: who are you, and may you ----------

    @ExceptionHandler(IdentifiantsInvalidesException.class)
    public ResponseEntity<ApiErrorResponse> gererIdentifiantsInvalides(IdentifiantsInvalidesException ex) {
        return reponse(HttpStatus.UNAUTHORIZED, "Authentification échouée", ex.getMessage());
    }

    @ExceptionHandler(CompteNonApprouveException.class)
    public ResponseEntity<ApiErrorResponse> gererCompteNonApprouve(CompteNonApprouveException ex) {
        return reponse(HttpStatus.FORBIDDEN, "Compte non approuvé", ex.getMessage());
    }

    // Thrown by the @PreAuthorize checks
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> gererAccesRefuse(AccessDeniedException ex) {
        return reponse(HttpStatus.FORBIDDEN, "Accès refusé",
                "Vous n'avez pas le droit d'accéder à cette ressource.");
    }

    // ---------- 404 / 405 ----------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> gererIntrouvable(ResourceNotFoundException ex) {
        return reponse(HttpStatus.NOT_FOUND, "Ressource introuvable", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> gererRouteIntrouvable(NoResourceFoundException ex) {
        return reponse(HttpStatus.NOT_FOUND, "Route introuvable", ex.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> gererMethodeNonSupportee(HttpRequestMethodNotSupportedException ex) {
        return reponse(HttpStatus.METHOD_NOT_ALLOWED, "Méthode non autorisée",
                "La méthode " + ex.getMethod() + " n'est pas disponible sur cette route.");
    }

    // ---------- 409: the request conflicts with the current data ----------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> gererRegleMetier(BusinessException ex) {
        return reponse(HttpStatus.CONFLICT, "Règle métier violée", ex.getMessage());
    }

    // Last line of defence behind the explicit checks (unique or foreign key constraint)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> gererIntegrite(DataIntegrityViolationException ex) {
        log.warn("Contrainte d'intégrité violée : {}", ex.getMostSpecificCause().getMessage());
        return reponse(HttpStatus.CONFLICT, "Conflit de données",
                "Cette opération viole une contrainte d'intégrité (valeur déjà utilisée ou ressource encore référencée).");
    }

    // ---------- 5xx ----------

    @ExceptionHandler(TmdbIndisponibleException.class)
    public ResponseEntity<ApiErrorResponse> gererTmdbIndisponible(TmdbIndisponibleException ex) {
        return reponse(HttpStatus.SERVICE_UNAVAILABLE, "Service externe indisponible", ex.getMessage());
    }

    // Safety net: the details go to the log, never to the client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> gererErreurInattendue(Exception ex) {
        log.error("Erreur inattendue", ex);
        return reponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne", "Une erreur inattendue est survenue");
    }

    private ResponseEntity<ApiErrorResponse> reponse(HttpStatus statut, String erreur, String message) {
        return reponse(statut, erreur, message, List.of());
    }

    private ResponseEntity<ApiErrorResponse> reponse(
            HttpStatus statut, String erreur, String message, List<String> details) {
        return ResponseEntity.status(statut)
                .body(new ApiErrorResponse(statut.value(), erreur, message, details));
    }
}
