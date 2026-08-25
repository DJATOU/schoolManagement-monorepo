package com.school.management.service.security;

import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Chargement d'un compte pour Spring Security à partir de son identifiant.
 *
 * <p>Convertit un {@link UserEntity} en {@link UserDetails} avec l'autorité
 * {@code ROLE_<role>} et l'état {@code enabled}. Un compte introuvable lève une
 * {@link UsernameNotFoundException} (mappée en 401 générique) ; un compte désactivé
 * ({@code enabled=false}) provoquera un {@code DisabledException} lors de l'authentification.</p>
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Compte introuvable : " + username));

        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .disabled(Boolean.FALSE.equals(user.getEnabled()))
                .build();
    }
}
