package com.school.management.config.security;

import com.school.management.persistance.Role;
import com.school.management.service.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtre d'authentification JWT exécuté une fois par requête.
 *
 * <p>Extrait le jeton {@code Bearer}, le valide via {@link JwtService} et, s'il est valide,
 * peuple le {@code SecurityContext} avec l'autorité {@code ROLE_<role>}. En l'absence d'en-tête,
 * pour un en-tête non {@code Bearer} ou un jeton invalide/expiré, il n'authentifie pas : la
 * chaîne de sécurité renverra alors un 401 sur les ressources protégées.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            final String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                final String username = jwtService.extractUsername(token);
                if (username != null && jwtService.isTokenValid(token, username)) {
                    Role role = jwtService.extractRole(token);
                    var authToken = new UsernamePasswordAuthenticationToken(
                            username, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                // Jeton invalide/illisible : on n'authentifie pas ; l'entry point renverra 401.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
