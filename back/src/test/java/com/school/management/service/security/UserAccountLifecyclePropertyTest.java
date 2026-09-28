package com.school.management.service.security;

import com.school.management.dto.security.CreateUserRequestDTO;
import com.school.management.dto.security.UserResponseDTO;
import com.school.management.mapper.UserMapperImpl;
import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) du cycle de vie d'un compte, en intégration H2 réelle.
 *
 * <p>Vérifie que la création persiste un compte avec mot de passe haché retrouvable par
 * identifiant, que la désactivation met {@code enabled=false}, et que la réinitialisation
 * n'accepte plus que le nouveau mot de passe (l'ancien est rejeté).</p>
 *
 * <p>jqwik s'exécute sur son propre moteur JUnit Platform : les tranches Spring
 * ({@code @DataJpaTest}) ne s'appliquent pas. Un contexte Spring ciblé (datasource H2, JPA,
 * dépôts, transactions) est donc amorcé une fois par conteneur via {@link BeforeContainer}. Le
 * {@link UserAccountService} est instancié manuellement à partir du {@link UserRepository} et d'un
 * {@link BCryptPasswordEncoder} de force réduite (4) pour accélérer les 100 itérations.</p>
 */
class UserAccountLifecyclePropertyTest {

    private static ConfigurableApplicationContext context;
    private static UserRepository userRepository;

    // Force BCrypt réduite : tests rapides, propriété inchangée.
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    // Garantit l'unicité des identifiants entre itérations.
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(UserLifecycleTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:user-lifecycle-pbt;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off");

        userRepository = context.getBean(UserRepository.class);
    }

    @AfterContainer
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    private UserAccountService service() {
        return new UserAccountService(userRepository, ENCODER, new UserMapperImpl());
    }

    @Provide
    Arbitrary<String> passwords() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(40);
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.ADMIN, Role.VIEWER);
    }

    // Feature: authentication-authorization, Property 9: Cycle de vie d'un compte (création, désactivation, réinitialisation)
    @Property(tries = 100)
    void accountLifecycle(@ForAll("passwords") String password,
                          @ForAll("passwords") String newPassword,
                          @ForAll("roles") Role role) {
        UserAccountService service = service();
        String username = "user-" + COUNTER.incrementAndGet();

        // --- Création : persistée, mot de passe haché, retrouvable par identifiant ---
        UserResponseDTO created = service.create(new CreateUserRequestDTO(username, password, role));
        assertThat(created.username()).isEqualTo(username);
        assertThat(created.role()).isEqualTo(role);
        assertThat(created.enabled()).isTrue();

        UserEntity stored = userRepository.findByUsername(username).orElseThrow();
        assertThat(stored.getPassword()).isNotEqualTo(password);
        assertThat(ENCODER.matches(password, stored.getPassword())).isTrue();

        // --- Désactivation : enabled=false et compte non connectable ---
        UserResponseDTO disabled = service.disable(stored.getId());
        assertThat(disabled.enabled()).isFalse();
        assertThat(userRepository.findById(stored.getId()).orElseThrow().getEnabled()).isFalse();

        // --- Réinitialisation : seul le nouveau mot de passe est accepté ---
        service.resetPassword(stored.getId(), newPassword);
        String rehashed = userRepository.findById(stored.getId()).orElseThrow().getPassword();
        assertThat(ENCODER.matches(newPassword, rehashed)).isTrue();
        if (!newPassword.equals(password)) {
            assertThat(ENCODER.matches(password, rehashed)).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // Contexte Spring ciblé (équivalent aux auto-configurations de @DataJpaTest)
    // ------------------------------------------------------------------

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @EntityScan("com.school.management.persistance")
    @EnableJpaRepositories("com.school.management.repository")
    static class UserLifecycleTestContext {
    }
}
