package com.school.management.service.security;

import com.school.management.dto.security.CreateUserRequestDTO;
import com.school.management.dto.security.UserResponseDTO;
import com.school.management.mapper.UserMapper;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.DuplicateUsernameException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des comptes utilisateurs (réservée à un ADMIN par la chaîne de sécurité).
 *
 * <p>Création (avec hachage BCrypt et rejet des identifiants en double en 409),
 * désactivation / réactivation, et réinitialisation de mot de passe. Toutes les réponses
 * passent par {@link UserResponseDTO} sans jamais exposer le mot de passe.</p>
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserAccountService(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /**
     * Crée un compte (mot de passe haché, {@code enabled=true}).
     *
     * @throws DuplicateUsernameException (409) si l'identifiant est déjà attribué
     */
    @Transactional
    public UserResponseDTO create(CreateUserRequestDTO request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }
        UserEntity user = UserEntity.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .enabled(true)
                .build();
        return userMapper.toResponse(userRepository.save(user));
    }

    /** Liste tous les comptes (sans mot de passe). */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    /** Désactive un compte (connexion impossible ensuite). */
    @Transactional
    public UserResponseDTO disable(Long id) {
        UserEntity user = loadUser(id);
        user.setEnabled(false);
        return userMapper.toResponse(userRepository.save(user));
    }

    /** Réactive un compte. */
    @Transactional
    public UserResponseDTO enable(Long id) {
        UserEntity user = loadUser(id);
        user.setEnabled(true);
        return userMapper.toResponse(userRepository.save(user));
    }

    /** Réinitialise le mot de passe d'un compte (ré-encodage BCrypt). */
    @Transactional
    public UserResponseDTO resetPassword(Long id, String newPassword) {
        UserEntity user = loadUser(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        return userMapper.toResponse(userRepository.save(user));
    }

    /** Charge un compte ou lève une 404. */
    private UserEntity loadUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new CustomServiceException(
                        "Compte introuvable pour l'identifiant : " + id, HttpStatus.NOT_FOUND));
    }
}
