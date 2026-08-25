package com.school.management.controller;

import com.school.management.dto.security.AuthResponseDTO;
import com.school.management.dto.security.LoginRequestDTO;
import com.school.management.service.security.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Contrôleur d'authentification (mince).
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/login} (public) : connexion, renvoie un jeton et le rôle.</li>
 *   <li>{@code GET /api/v1/auth/me} (authentifié) : renvoie l'identifiant et le rôle courants,
 *       utile au frontend pour restaurer l'état de session.</li>
 * </ul>
 *
 * <p>La logique réside dans {@link AuthenticationService} ; le contrôleur reste mince.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /** Connexion : identifiant + mot de passe → jeton JWT. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    /** Renvoie l'identifiant et le rôle de l'utilisateur courant (depuis le SecurityContext). */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElse(null);
        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "role", role != null ? role : ""));
    }
}
