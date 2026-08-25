package com.school.management.service.security;

import com.school.management.config.security.SecurityAuditorAware;
import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.AfterProperty;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) de la traçabilité de l'audit sur l'utilisateur courant.
 *
 * <p>Pour tout enregistrement dérivant de {@code BaseEntity}, {@code createdBy} vaut
 * l'identifiant de l'utilisateur authentifié (jamais « admin » codé en dur), et {@code system}
 * en l'absence d'utilisateur (contexte anonyme / vide).</p>
 *
 * <p>jqwik s'exécute sur son propre moteur : un contexte Spring ciblé est amorcé une fois par
 * conteneur, avec l'audit JPA activé ({@code @EnableJpaAuditing}) alimenté par
 * {@link SecurityAuditorAware}. Chaque essai persiste une {@link UserEntity} (qui étend
 * {@code BaseEntity}) et vérifie la valeur auditée selon l'état du {@code SecurityContext}.</p>
 */
class AuditTraceabilityPropertyTest {

    private static ConfigurableApplicationContext context;
    private static UserRepository userRepository;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(AuditTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:audit-pbt;DB_CLOSE_DELAY=-1",
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

    @AfterProperty
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Provide
    Arbitrary<String> usernames() {
        // Identifiants sans « admin » codé en dur (on vérifie qu'aucune valeur figée n'est utilisée).
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
    }

    // Feature: authentication-authorization, Property 11: Traçabilité de l'audit sur l'utilisateur courant
    @Property(tries = 100)
    void auditReflectsCurrentUser(@ForAll boolean authenticated,
                                  @ForAll("usernames") String currentUser) {
        // --- Arrange : positionner le SecurityContext selon l'essai ---
        SecurityContextHolder.clearContext();
        if (authenticated) {
            var auth = new UsernamePasswordAuthenticationToken(
                    currentUser, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // --- Act : persister une entité dérivant de BaseEntity ---
        UserEntity entity = UserEntity.builder()
                .username("audited-" + COUNTER.incrementAndGet())
                .password("x")
                .role(Role.VIEWER)
                .enabled(true)
                .build();
        UserEntity saved = userRepository.saveAndFlush(entity);

        // --- Assert : createdBy reflète l'utilisateur courant, ou "system" si anonyme ---
        String expected = authenticated ? currentUser : "system";
        assertThat(saved.getCreatedBy()).isEqualTo(expected);
        assertThat(saved.getCreatedBy()).isNotEqualTo("admin");
    }

    // ------------------------------------------------------------------
    // Contexte Spring ciblé : JPA + audit activé (SecurityAuditorAware)
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
    @EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
    static class AuditTestContext {

        @Bean
        SecurityAuditorAware securityAuditorAware() {
            return new SecurityAuditorAware();
        }
    }
}
