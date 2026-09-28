package com.school.management.service.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception levée lorsqu'aucune année scolaire courante n'est définie (Exigence 13.1).
 *
 * <p>Étend {@link CustomServiceException} afin d'être prise en charge automatiquement par
 * {@code GlobalExceptionHandler} et porte un {@link HttpStatus} clair. Le message par défaut
 * indique explicitement qu'« aucune année scolaire courante n'est définie ».</p>
 */
public class NoCurrentSchoolYearException extends CustomServiceException {

    /** Message par défaut renvoyé lorsqu'aucune année scolaire courante n'existe. */
    public static final String DEFAULT_MESSAGE = "Aucune année scolaire courante définie.";

    public NoCurrentSchoolYearException() {
        super(DEFAULT_MESSAGE, HttpStatus.NOT_FOUND);
    }

    public NoCurrentSchoolYearException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
