package com.school.management.config;

import com.school.management.config.security.JwtAuthenticationFilter;
import com.school.management.config.security.RestAccessDeniedHandler;
import com.school.management.config.security.RestAuthenticationEntryPoint;
import com.school.management.service.security.AppUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration de sécurité : authentification JWT stateless et autorisation par rôle.
 *
 * <p>Points publics : connexion et documentation Swagger. Gestion des comptes réservée à ADMIN.
 * Lecture (GET) autorisée aux deux rôles ; écritures (POST/PUT/PATCH/DELETE) réservées à ADMIN
 * (sinon 403). Le backend reste l'autorité : le masquage UI n'est qu'une commodité.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Par défaut : localhost + Vercel (branche principale + previews)
    @Value("${cors.allowed.origins:http://localhost:4200,https://school-management-monorepo.vercel.app,https://school-management-monorepo-*.vercel.app}")
    private String allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final AppUserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          AppUserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    // Origines : setAllowedOriginPatterns pour supporter les wildcards
                    List<String> origins = Arrays.asList(allowedOrigins.split(","));
                    config.setAllowedOriginPatterns(origins);
                    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                // API stateless + JWT : pas de CSRF ni de session serveur
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // Points d'accès publics
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers(
                                "/v2/api-docs",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-resources",
                                "/swagger-resources/**",
                                "/configuration/ui",
                                "/configuration/security",
                                "/swagger-ui/**",
                                "/webjars/**",
                                "/swagger-ui.html").permitAll()
                        // Sonde de disponibilité : la vérification d'état de Docker interroge
                        // ce point d'accès sans jeton (elle n'a pas de session pour en obtenir
                        // un). Il ne divulgue rien : show-details=never limite la réponse à
                        // {"status":"UP"}. Il n'est pas publié sur l'hôte et nginx ne relaie pas
                        // /actuator, donc il reste confiné au réseau interne Docker.
                        .requestMatchers("/actuator/health").permitAll()
                        // Gestion des comptes : ADMIN uniquement (avant les règles génériques)
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                        // Encaissements : données financières réservées à ADMIN, y compris en
                        // lecture. Doit précéder la règle générique GET ouverte aux deux rôles.
                        .requestMatchers(HttpMethod.GET, "/api/groups/*/revenue").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/revenue/**").hasRole("ADMIN")
                        // Lecture : les deux rôles
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ADMIN", "VIEWER")
                        // Écriture : ADMIN uniquement
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)   // 401
                        .accessDeniedHandler(accessDeniedHandler))            // 403
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Encodeur de mots de passe (BCrypt) réutilisé par les services. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Fournisseur d'authentification basé sur le chargement de compte + BCrypt. */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /** Expose l'AuthenticationManager pour le service d'authentification. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
