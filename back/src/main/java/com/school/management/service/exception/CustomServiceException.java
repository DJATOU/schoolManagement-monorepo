package com.school.management.service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomServiceException extends RuntimeException {
    private final HttpStatus status;

    public CustomServiceException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
    }

    public CustomServiceException(String message) {
        super(message);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR; // Valeur par défaut
    }

    public CustomServiceException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR; // Valeur par défaut
    }

    public CustomServiceException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
