package com.school.management.service.exception;

import org.springframework.http.HttpStatus;

/**
 * Levée lorsqu'une création de compte utilise un identifiant déjà attribué.
 *
 * <p>Mappée en <strong>409 Conflict</strong> : les comptes existants restent inchangés.</p>
 */
public class DuplicateUsernameException extends CustomServiceException {

    public DuplicateUsernameException(String username) {
        super("L'identifiant « " + username + " » est déjà utilisé.", HttpStatus.CONFLICT);
    }
}
