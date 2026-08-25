package com.school.management.config.security;

import com.school.management.config.SecurityConfig;
import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import com.school.management.repository.UserRepository;
import com.school.management.service.security.AppUserDetailsService;
import com.school.management.service.security.JwtService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de propriété (jqwik) de la chaîne de sécurité complète : matrice d'autorisation
 * rôle × méthode (Property 4) et invariant « un refus d'écriture laisse les données
 * inchangées » (Property 5).
 *
 * <p>Un contexte web servlet complet (Tomcat embarqué, H2) est amorcé une fois par conteneur
 * avec la vraie {@link SecurityConfig}, le filtre JWT et les handlers 401/403. Un contrôleur de
 * test expose des points sous {@code /api/**} (GET + les quatre méthodes d'écriture, qui
 * persistent un marqueur) et un point sous {@code /api/v1/users/**}. Les jetons ADMIN / VIEWER
 * sont émis par le vrai {@link JwtService} ; le filtre autorise d'après le rôle porté par le
 * jeton (aucune session, stateless).</p>
 */
class SecurityAuthorizationMatrixPropertyTest {

    private static ConfigurableApplicationContext context;
    private static UserRepository userRepository;
    private static JwtService jwtService;
    private static int port;

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(SecurityMatrixTestContext.class)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:security-matrix-pbt;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--security.jwt.secret=matrix-test-secret-0123456789-abcdefghijklmnop",
                        "--security.jwt.expiration-ms=3600000",
                        "--spring.main.banner-mode=off");

        userRepository = context.getBean(UserRepository.class);
        jwtService = context.getBean(JwtService.class);
        port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterContainer
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    private String token(Role role) {
        return jwtService.generateToken(role == Role.ADMIN ? "admin-user" : "viewer-user", role);
    }

    private HttpResponse<Void> call(String method, String path, Role role) throws Exception {
        HttpRequest.BodyPublisher body = "GET".equals(method)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString("{}");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token(role))
                .header("Content-Type", "application/json")
                .method(method, body)
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.ADMIN, Role.VIEWER);
    }

    @Provide
    Arbitrary<String> methods() {
        return Arbitraries.of("GET", "POST", "PUT", "PATCH", "DELETE");
    }

    // Feature: authentication-authorization, Property 4: Autorisation par rôle (matrice rôle × méthode)
    @Property(tries = 100)
    void roleMethodAuthorizationMatrix(@ForAll("roles") Role role,
                                       @ForAll("methods") String method) throws Exception {
        int status = call(method, "/api/test-secured", role).statusCode();

        boolean isWrite = !"GET".equals(method);
        if (role == Role.ADMIN) {
            // ADMIN : autorisé sur toute méthode.
            assertThat(status).as("ADMIN %s doit être autorisé", method).isEqualTo(200);
        } else if (!isWrite) {
            // VIEWER : lecture autorisée.
            assertThat(status).as("VIEWER GET doit être autorisé").isEqualTo(200);
        } else {
            // VIEWER : écriture refusée en 403.
            assertThat(status).as("VIEWER %s doit être refusé (403)", method)
                    .isEqualTo(HttpStatus.FORBIDDEN.value());
        }

        // Règle spécifique /api/v1/users/** : ADMIN autorisé, VIEWER refusé même en GET.
        int usersStatus = call("GET", "/api/v1/users/test-ping", role).statusCode();
        if (role == Role.ADMIN) {
            assertThat(usersStatus).as("ADMIN autorisé sur /api/v1/users/**").isEqualTo(200);
        } else {
            assertThat(usersStatus).as("VIEWER refusé sur /api/v1/users/**")
                    .isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    // Feature: authentication-authorization, Property 5: Un refus d'écriture laisse les données inchangées
    @Property(tries = 100)
    void writeRefusalLeavesDataUnchanged(@ForAll("methods") String method) throws Exception {
        // On ne teste que les écritures (le GET n'écrit rien).
        net.jqwik.api.Assume.that(!"GET".equals(method));

        long before = userRepository.count();

        int status = call(method, "/api/test-secured", Role.VIEWER).statusCode();

        // L'écriture est refusée pour un VIEWER…
        assertThat(status).isEqualTo(HttpStatus.FORBIDDEN.value());
        // …et l'état persistant est identique (le marqueur n'a pas été écrit).
        assertThat(userRepository.count()).isEqualTo(before);
    }

    // ------------------------------------------------------------------
    // Contexte Spring web complet : sécurité réelle + contrôleur de test
    // ------------------------------------------------------------------

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("com.school.management.persistance")
    @EnableJpaRepositories("com.school.management.repository")
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class,
            JwtService.class,
            AppUserDetailsService.class
    })
    static class SecurityMatrixTestContext {

        @Bean
        TestSecuredController testSecuredController(UserRepository repo) {
            return new TestSecuredController(repo);
        }
    }

    /**
     * Contrôleur de test exposant GET + les quatre méthodes d'écriture sous {@code /api/**},
     * plus un point sous {@code /api/v1/users/**}. Chaque écriture persiste un marqueur : si un
     * VIEWER l'atteignait, l'état changerait — ce que la chaîne de sécurité doit empêcher (403).
     */
    @RestController
    static class TestSecuredController {

        private static final AtomicInteger COUNTER = new AtomicInteger();
        private final UserRepository repo;

        TestSecuredController(UserRepository repo) {
            this.repo = repo;
        }

        private void writeMarker() {
            repo.saveAndFlush(UserEntity.builder()
                    .username("marker-" + COUNTER.incrementAndGet())
                    .password("x")
                    .role(Role.VIEWER)
                    .enabled(true)
                    .build());
        }

        @GetMapping("/api/test-secured")
        String read() {
            return "ok";
        }

        @PostMapping("/api/test-secured")
        String create() {
            writeMarker();
            return "ok";
        }

        @PutMapping("/api/test-secured")
        String update() {
            writeMarker();
            return "ok";
        }

        @PatchMapping("/api/test-secured")
        String patch() {
            writeMarker();
            return "ok";
        }

        @DeleteMapping("/api/test-secured")
        String delete() {
            writeMarker();
            return "ok";
        }

        @GetMapping("/api/v1/users/test-ping")
        String usersPing() {
            return "ok";
        }
    }
}
