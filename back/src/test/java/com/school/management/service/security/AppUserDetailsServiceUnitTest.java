package com.school.management.service.security;

import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AppUserDetailsService} : compte trouvé (autorité + activé),
 * compte désactivé, et compte introuvable.
 */
class AppUserDetailsServiceUnitTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AppUserDetailsService service = new AppUserDetailsService(userRepository);

    private UserEntity user(String username, Role role, boolean enabled) {
        return UserEntity.builder()
                .username(username)
                .password("hashed")
                .role(role)
                .enabled(enabled)
                .build();
    }

    @Test
    void loadUser_foundEnabled_hasRoleAuthorityAndEnabled() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user("alice", Role.ADMIN, true)));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("hashed");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUser_foundDisabled_isNotEnabled() {
        when(userRepository.findByUsername("bob"))
                .thenReturn(Optional.of(user("bob", Role.VIEWER, false)));

        UserDetails details = service.loadUserByUsername("bob");

        assertThat(details.isEnabled()).isFalse();
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_VIEWER");
    }

    @Test
    void loadUser_notFound_throwsUsernameNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
