package com.school.management.util;

import com.school.management.service.correction.RefundFloorException;
import com.school.management.service.correction.StalePreviewException;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.session.AbsenceOutsideWindowException;
import com.school.management.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.validation.ObjectError;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(CustomServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomServiceException(CustomServiceException e) {
        HttpStatus status = e.getStatus() != null ? e.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        ApiErrorResponse error = new ApiErrorResponse(status, e.getMessage(), status.name());
        logger.error("CustomServiceException: {}", e.getMessage());
        return new ResponseEntity<>(error, status);
    }

    /**
     * Aperçu périmé : 409 avec le nouvel Aperçu, que l'écran présente à la place de l'ancien
     * (exigence 4.3). Plus spécifique que {@link CustomServiceException}, ce gestionnaire l'emporte.
     */
    @ExceptionHandler(StalePreviewException.class)
    public ResponseEntity<StalePreviewErrorResponse> handleStalePreviewException(StalePreviewException e) {
        logger.warn("Stale correction preview: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new StalePreviewErrorResponse(
                HttpStatus.CONFLICT, e.getMessage(), "STALE_PREVIEW", e.getPreview(), e.getPreviewToken()));
    }

    /**
     * Versé qui passerait sous le remboursé : 409 nommant les remboursements en cause
     * (exigence 2.4).
     */
    @ExceptionHandler(RefundFloorException.class)
    public ResponseEntity<RefundFloorErrorResponse> handleRefundFloorException(RefundFloorException e) {
        logger.warn("Correction below refunds: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new RefundFloorErrorResponse(
                HttpStatus.CONFLICT, e.getMessage(), "REFUND_FLOOR", e.getBlockingRefunds()));
    }

    /**
     * Absence hors Fenêtre_Inscription : 409 nommant chaque ligne refusée, pour que l'écran les
     * retire en une fois et revalide (exigence 7.5).
     */
    @ExceptionHandler(AbsenceOutsideWindowException.class)
    public ResponseEntity<RejectedAbsencesErrorResponse> handleAbsenceOutsideWindowException(
            AbsenceOutsideWindowException e) {
        logger.warn("Absences outside enrolment window: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new RejectedAbsencesErrorResponse(
                HttpStatus.CONFLICT, e.getMessage(), "ABSENCE_OUTSIDE_WINDOW", e.getRejected()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(ResourceNotFoundException e) {
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.NOT_FOUND, e.getMessage(), "NOT_FOUND");
        logger.warn("Resource not found: {}", e.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    /**
     * Entité absente en base : renvoie 404 au lieu d'une 500. Les services lèvent
     * {@link EntityNotFoundException} (JPA) aussi bien que {@link ResourceNotFoundException} ;
     * les deux doivent donner la même réponse au client.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFoundException(EntityNotFoundException e) {
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.NOT_FOUND, e.getMessage(), "NOT_FOUND");
        logger.warn("Entity not found: {}", e.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    /**
     * Valeur invalide fournie par le client (identifiant non numérique, date mal formée) :
     * renvoie 400 plutôt qu'une 500, qui laissait croire à une panne serveur.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage(), "BAD_REQUEST");
        logger.warn("Invalid request: {}", e.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Paramètre de chemin ou de requête d'un type inattendu — « 2030-13-40 » pour une date,
     * « abc » pour un identifiant : 400, le paramètre nommé. Le gestionnaire générique en faisait
     * une 500, comme une panne serveur.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "Paramètre « " + e.getName() + " » invalide : " + e.getValue();
        logger.warn("Invalid request parameter: {}", message);
        return new ResponseEntity<>(new ApiErrorResponse(HttpStatus.BAD_REQUEST, message, "BAD_REQUEST"),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.BAD_REQUEST, errorMessage, "VALIDATION_ERROR");
        logger.error("Validation error: {}", errorMessage);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Méthode HTTP non supportée sur l'endpoint (ex. GET sur un endpoint POST/PATCH) :
     * renvoie 405 Method Not Allowed plutôt qu'une 500 trompeuse.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(),
                "METHOD_NOT_ALLOWED");
        logger.warn("Method not supported: {}", e.getMessage());
        return new ResponseEntity<>(error, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Adresse sans point d'entrée (par exemple un point d'entrée retiré, appelé par un écran resté
     * ouvert sur l'ancienne version) : 404, et non une 500 qui ferait croire à une panne.
     *
     * <p>{@code @EnableWebMvc} (WebConfig) écarte les ressources statiques par défaut : une adresse
     * inconnue lève {@link NoHandlerFoundException}, quelle que soit sa méthode.</p>
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoEndpoint(HttpServletRequest request) {
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.NOT_FOUND, "Adresse inconnue : " + endpoint,
                "NOT_FOUND");
        logger.warn("No endpoint: {}", endpoint);
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e) {
        logger.error("Internal server error: {}", e.getMessage(), e);
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "INTERNAL_SERVER_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

}
