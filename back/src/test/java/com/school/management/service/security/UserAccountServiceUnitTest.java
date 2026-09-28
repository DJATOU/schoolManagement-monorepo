package com.school.management.service.security;

import com.school.management.dto.security.UserResponseDTO;
import com.school.management.mapper.UserMapper;
import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link UserAccountService} couvrant {@code enable}, {@code findAll} et la
 * branche « compte introuvable » (404) de {@code loadUser}.
 */
class UserAccountServiceUnitTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserMapper mapper = mock(UserMapper.class);
    private final UserAccountService service = new UserAccountService(userRepository, encoder, mapper);

    private UserEntity user(long id, boolean enabled) {
        UserEntity u = UserEntity.builder()
                .id(id).username("u" + id).password("h").role(Role.VIEWER).enabled(enabled).build();
        return u;
    }

    @Test
    void enable_setsEnabledTrue() {
        UserEntity u = user(1L, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(UserEntity.class)))
                .thenReturn(new UserResponseDTO(1L, "u1", Role.VIEWER, true));

        UserResponseDTO dto = service.enable(1L);

        assertThat(u.getEnabled()).isTrue();
        assertThat(dto.enabled()).isTrue();
    }

    @Test
    void findAll_mapsAllAccounts() {
        when(userRepository.findAll()).thenReturn(List.of(user(1L, true), user(2L, false)));
        when(mapper.toResponse(any(UserEntity.class)))
                .thenReturn(new UserResponseDTO(1L, "u1", Role.VIEWER, true));

        List<UserResponseDTO> all = service.findAll();

        assertThat(all).hasSize(2);
    }

    @Test
    void disable_unknownId_throws404() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(999L))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resetPassword_reencodesNewPassword() {
        UserEntity u = user(5L, true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(encoder.encode("newpass")).thenReturn("ENCODED");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(UserEntity.class)))
                .thenReturn(new UserResponseDTO(5L, "u5", Role.VIEWER, true));

        service.resetPassword(5L, "newpass");

        assertThat(u.getPassword()).isEqualTo("ENCODED");
    }
}
