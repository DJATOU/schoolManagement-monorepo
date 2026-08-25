package com.school.management.config.security;

import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) de l'idempotence de la création du compte ADMIN initial.
 */
class InitialAdminRunnerPropertyTest {

    @Provide
    Arbitrary<String> texts() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
    }

    // Feature: authentication-authorization, Property 8: Idempotence de la création du compte ADMIN initial
    @Property(tries = 100)
    void initialAdminCreationIsIdempotent(@ForAll boolean adminExists,
                                          @ForAll("texts") String username,
                                          @ForAll("texts") String password) {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(adminExists);
        when(encoder.encode(anyString())).thenReturn("HASH");

        InitialAdminRunner runner = new InitialAdminRunner(userRepository, encoder, username, password);

        // Deux exécutions successives : l'idempotence doit tenir.
        runner.run(null);
        runner.run(null);

        if (adminExists) {
            // Un ADMIN existe déjà : aucun compte n'est créé ni écrasé.
            verify(userRepository, never()).save(any(UserEntity.class));
        } else {
            // Aucun ADMIN : exactement un compte ADMIN est créé à chaque exécution (le mock
            // existsByRole reste false), rôle ADMIN et activé — jamais écrasé d'un existant.
            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository, times(2)).save(captor.capture());
            UserEntity created = captor.getValue();
            assertThat(created.getRole()).isEqualTo(Role.ADMIN);
            assertThat(created.getEnabled()).isTrue();
            assertThat(created.getUsername()).isEqualTo(username);
        }
    }
}
