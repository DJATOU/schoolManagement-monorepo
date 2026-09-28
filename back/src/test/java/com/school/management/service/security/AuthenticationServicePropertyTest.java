package com.school.management.service.security;

import com.school.management.dto.security.LoginRequestDTO;
import com.school.management.repository.UserRepository;
import com.school.management.service.exception.CustomServiceException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test de propriété (jqwik) de la connexion refusée pour identifiants invalides ou compte
 * désactivé. L'{@link AuthenticationManager} est mocké pour lever les différents cas d'échec.
 */
class AuthenticationServicePropertyTest {

    private static final String GENERIC_MESSAGE = "Identifiant ou mot de passe invalide.";

    @Provide
    Arbitrary<String> texts() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30);
    }

    @Provide
    Arbitrary<Integer> failureKind() {
        return Arbitraries.integers().between(0, 2);
    }

    // Feature: authentication-authorization, Property 2: Connexion refusée pour identifiants invalides ou compte désactivé
    @Property(tries = 100)
    void loginRejectedForInvalidCredentialsOrDisabledAccount(@ForAll("texts") String username,
                                                             @ForAll("texts") String password,
                                                             @ForAll("failureKind") int kind) {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);

        // Trois formes d'échec : identifiant inexistant, mot de passe incorrect, compte désactivé.
        AuthenticationException failure = switch (kind) {
            case 0 -> new UsernameNotFoundException("inconnu");
            case 1 -> new BadCredentialsException("mauvais mot de passe");
            default -> new DisabledException("compte désactivé");
        };
        when(authManager.authenticate(any())).thenThrow(failure);

        AuthenticationService service =
                new AuthenticationService(authManager, jwtService, userRepository);

        CustomServiceException ex = catchThrowableOfType(
                () -> service.login(new LoginRequestDTO(username, password)),
                CustomServiceException.class);

        // Rejet 401 avec un message générique identique dans tous les cas (ne révèle pas le champ).
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getMessage()).isEqualTo(GENERIC_MESSAGE);
    }
}
