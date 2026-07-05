package com.school.management.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Default: localhost + Vercel (main + preview branches)
    @Value("${cors.allowed.origins:http://localhost:4200,https://school-management-monorepo.vercel.app,https://school-management-monorepo-*.vercel.app}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();

                    // Origins - utiliser setAllowedOriginPatterns pour supporter les wildcards
                    List<String> origins = Arrays.asList(allowedOrigins.split(","));
                    config.setAllowedOriginPatterns(origins);

                    // Methods
                    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

                    // Headers (on ouvre tout pour être tranquille avec Angular)
                    config.setAllowedHeaders(List.of("*"));

                    // Si tu n’utilises pas de cookies/session côté front, tu peux même mettre false
                    config.setAllowCredentials(true);

                    return config;
                }))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/api/v1/aith/**", // (ptit typo ici, tu voulais sans doute /auth/**)
                                "v2/api-docs",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-resources",
                                "/swagger-resources/**",
                                "/configuration/ui",
                                "/configuration/security",
                                "swagger-ui/**",
                                "/webjars/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
