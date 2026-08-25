package com.school.management.config.security;

import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée le compte ADMIN initial au démarrage si aucun ADMIN n'existe (idempotent).
 *
 * <p>Si un ADMIN existe déjà, ne fait rien (les comptes existants ne sont jamais écrasés).
 * Sinon, crée un ADMIN à partir de la configuration externe
 * ({@code security.admin.username} / {@code security.admin.password}), mot de passe haché en
 * BCrypt et {@code enabled=true}. Aucun secret n'est codé en dur ; si le mot de passe est
 * absent, la création est ignorée avec un message explicite.</p>
 */
@Component
public class InitialAdminRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAdminRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public InitialAdminRunner(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              @Value("${security.admin.username:admin}") String adminUsername,
                              @Value("${security.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            LOGGER.debug("Un compte ADMIN existe déjà : aucune création initiale.");
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            LOGGER.warn("Aucun compte ADMIN et ADMIN_PASSWORD absent : compte ADMIN initial NON créé. "
                    + "Définir ADMIN_PASSWORD (et éventuellement ADMIN_USERNAME) pour l'activer.");
            return;
        }
        UserEntity admin = UserEntity.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(admin);
        LOGGER.info("Compte ADMIN initial « {} » créé.", adminUsername);
    }
}
