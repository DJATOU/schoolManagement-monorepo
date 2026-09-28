package com.school.management.config.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link SecurityAuditorAware} couvrant les branches de repli « system » :
 * contexte vide (null), jeton non authentifié, et jeton anonyme ; ainsi que le cas authentifié.
 */
class SecurityAuditorAwareUnitTest {

    private final SecurityAuditorAware auditorAware = new SecurityAuditorAware();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedUser_returnsUsername() {
        var auth = new UsernamePasswordAuthenticationToken(
                "alice", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(auditorAware.getCurrentAuditor()).contains("alice");
    }

    @Test
    void noAuthentication_returnsSystem() {
        SecurityContextHolder.clearContext();
        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    @Test
    void notAuthenticated_returnsSystem() {
        var auth = new UsernamePasswordAuthenticationToken("bob", null);
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    @Test
    void anonymousToken_returnsSystem() {
        var anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }
}
