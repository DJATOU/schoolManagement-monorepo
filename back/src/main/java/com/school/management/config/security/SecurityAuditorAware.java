package com.school.management.config.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fournit l'identifiant de l'utilisateur courant pour l'audit JPA
 * ({@code createdBy} / {@code updatedBy}).
 *
 * <p>Lit l'utilisateur du {@code SecurityContext}. En l'absence d'utilisateur authentifié
 * (contexte système, tâche d'initialisation), renvoie l'identifiant de repli explicite
 * {@code system} — jamais la valeur codée en dur « admin ».</p>
 */
@Component("securityAuditorAware")
public class SecurityAuditorAware implements AuditorAware<String> {

    private static final String SYSTEM = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.of(SYSTEM);
        }
        return Optional.of(auth.getName());
    }
}
