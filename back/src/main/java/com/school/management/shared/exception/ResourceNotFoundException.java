package com.school.management.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception levée lorsqu'une ressource demandée n'est pas trouvée en base de données.
 * Retourne automatiquement un code HTTP 404 (NOT FOUND).
 *
 * Usage:
 * <pre>
 * throw new ResourceNotFoundException("Student", 123L);
 * // Message: "Student not found with id: 123"
 * </pre>
 *
 * @author Claude Code
 * @since Phase 1 Refactoring
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object resourceId;

    /**
     * Constructeur avec type de ressource et identifiant
     *
     * @param resourceType le type de ressource (ex: "Student", "Group")
     * @param resourceId l'identifiant de la ressource non trouvée
     */
    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(String.format("%s not found with id: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    /**
     * Constructeur avec message personnalisé
     *
     * @param message le message d'erreur personnalisé
     */
    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getResourceId() {
        return resourceId;
    }
}
