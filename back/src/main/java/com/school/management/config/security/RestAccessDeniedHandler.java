package com.school.management.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Gestionnaire d'accès refusé : renvoie <strong>403</strong> avec un message français lorsqu'un
 * utilisateur authentifié tente une opération non autorisée par son rôle (par exemple un VIEWER
 * effectuant une écriture, ou tout non-ADMIN sur la gestion des comptes).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":\"FORBIDDEN\",\"message\":\"Accès refusé : vous n'avez pas les droits nécessaires.\","
                        + "\"errorCode\":\"FORBIDDEN\"}");
    }
}
