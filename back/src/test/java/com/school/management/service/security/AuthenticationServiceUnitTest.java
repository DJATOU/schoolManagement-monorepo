package com.school.management.service.security;

import com.school.management.dto.security.AuthResponseDTO;
import com.school.management.dto.security.LoginRequestDTO;
import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AuthenticationService} : chemin de succès (jeton + rôle exposés)
 * et cas limite où l'authentification réussit mais le compte est introuvable en base.
 */
class AuthenticationServiceUnitTest {

    private final AuthenticationManager authManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthenticationService service =
            new AuthenticationService(authManager, jwtService, userRepository);

    @Test
    void login_success_returnsTokenUsernameRoleAndExpiry() {
        UserEntity user = UserEntity.builder()
                .username("alice").password("hashed").role(Role.ADMIN).enabled(true).build();
        Instant expiry = Instant.now().plusSeconds(3600);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("alice", Role.ADMIN)).thenReturn("jwt-token");
        when(jwtService.extractExpiration("jwt-token")).thenReturn(expiry);

        AuthResponseDTO response = service.login(new LoginRequestDTO("alice", "secret"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.expiresAt()).isEqualTo(expiry);
    }

    @Test
    void login_authOkButUserMissing_throws401() {
        // authManager.authenticate ne lève pas (succès), mais le compte est absent en base.
        when(userRepository.findByUsername(eq("ghost"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequestDTO("ghost", "secret")))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
