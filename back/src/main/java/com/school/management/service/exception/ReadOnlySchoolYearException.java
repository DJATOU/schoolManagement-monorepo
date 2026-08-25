package com.school.management.service.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception levée lorsqu'une opération de création, de modification ou de suppression cible des
 * données appartenant à une année scolaire qui n'est pas l'année courante (Exigence 9.2).
 *
 * <p>Les données des années scolaires passées sont en lecture seule : elles restent consultables
 * mais ne peuvent pas être modifiées. Étend {@link CustomServiceException} afin d'être prise en
 * charge automatiquement par {@code GlobalExceptionHandler} et porte un {@link HttpStatus#CONFLICT}
 * (HTTP 409).</p>
 */
public class ReadOnlySchoolYearException extends CustomServiceException {

    /** Message par défaut renvoyé lorsqu'une modification vise une année scolaire non courante. */
    public static final String DEFAULT_MESSAGE =
            "Cette année scolaire est en lecture seule : modification interdite.";

    public ReadOnlySchoolYearException() {
        super(DEFAULT_MESSAGE, HttpStatus.CONFLICT);
    }

    public ReadOnlySchoolYearException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
