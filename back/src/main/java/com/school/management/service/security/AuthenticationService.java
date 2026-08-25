package com.school.management.service.security;

import com.school.management.dto.security.AuthResponseDTO;
import com.school.management.dto.security.LoginRequestDTO;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Service d'authentification (connexion).
 *
 * <p>Délègue la vérification des identifiants à l'{@link AuthenticationManager} Spring (qui
 * s'appuie sur {@link AppUserDetailsService} + {@code BCryptPasswordEncoder}). En cas de succès,
 * émet un JWT via {@link JwtService} et construit un {@link AuthResponseDTO}. En cas d'échec
 * (identifiants invalides ou compte désactivé), lève une exception traduite en <strong>401
 * générique</strong> ne révélant pas quel champ est erroné.</p>
 */
@Service
public class AuthenticationService {

    private static final String INVALID_CREDENTIALS = "Identifiant ou mot de passe invalide.";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthenticationService(AuthenticationManager authenticationManager,
                                 JwtService jwtService,
                                 UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /**
     * Authentifie un utilisateur et émet un jeton.
     *
     * @param request identifiant + mot de passe
     * @return le jeton et les informations du compte
     * @throws CustomServiceException (401) si les identifiants sont invalides ou le compte désactivé
     */
    public AuthResponseDTO login(LoginRequestDTO request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException e) {
            // Message générique unique : ne révèle pas si c'est l'identifiant ou le mot de passe.
            throw new CustomServiceException(INVALID_CREDENTIALS, e, HttpStatus.UNAUTHORIZED);
        }

        UserEntity user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new CustomServiceException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED));

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponseDTO(
                token,
                user.getUsername(),
                user.getRole(),
                jwtService.extractExpiration(token));
    }
}
