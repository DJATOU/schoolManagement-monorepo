package com.school.management.util;

import com.school.management.service.exception.CustomServiceException;
import com.school.management.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e) {
        logger.error("Internal server error: {}", e.getMessage(), e);
        ApiErrorResponse error = new ApiErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "INTERNAL_SERVER_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

}
