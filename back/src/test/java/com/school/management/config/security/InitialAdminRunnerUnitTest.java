package com.school.management.config.security;

import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitaire de {@link InitialAdminRunner} couvrant la branche « ADMIN_PASSWORD absent » :
 * aucun ADMIN n'existe mais le mot de passe est vide → aucun compte n'est créé.
 */
class InitialAdminRunnerUnitTest {

    @Test
    void noAdminAndBlankPassword_doesNotCreateAccount() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        // Mot de passe vide → création ignorée avec message explicite.
        InitialAdminRunner runner = new InitialAdminRunner(userRepository, encoder, "admin", "   ");
        runner.run(null);

        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void noAdminAndNullPassword_doesNotCreateAccount() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        // Mot de passe null → branche « adminPassword == null » du garde-fou.
        InitialAdminRunner runner = new InitialAdminRunner(userRepository, encoder, "admin", null);
        runner.run(null);

        verify(userRepository, never()).save(any(UserEntity.class));
    }
}
