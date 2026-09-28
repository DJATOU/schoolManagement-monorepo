package com.school.management.service.security;

import com.school.management.dto.security.CreateUserRequestDTO;
import com.school.management.mapper.UserMapper;
import com.school.management.persistance.Role;
import com.school.management.repository.UserRepository;
import com.school.management.service.exception.DuplicateUsernameException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test de propriété (jqwik) du rejet des identifiants en double.
 */
class UserAccountServicePropertyTest {

    @Provide
    Arbitrary<String> texts() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30);
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.ADMIN, Role.VIEWER);
    }

    // Feature: authentication-authorization, Property 10: Rejet des identifiants en double
    @Property(tries = 100)
    void duplicateUsernameRejected(@ForAll("texts") String username,
                                   @ForAll("texts") String password,
                                   @ForAll("roles") Role role) {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserMapper mapper = mock(UserMapper.class);
        // L'identifiant est déjà attribué.
        when(userRepository.existsByUsername(username)).thenReturn(true);

        UserAccountService service = new UserAccountService(userRepository, encoder, mapper);

        assertThatThrownBy(() -> service.create(new CreateUserRequestDTO(username, password, role)))
                .isInstanceOf(DuplicateUsernameException.class);

        // Les comptes existants restent inchangés : aucune sauvegarde n'a lieu.
        verify(userRepository, never()).save(any());
    }
}
