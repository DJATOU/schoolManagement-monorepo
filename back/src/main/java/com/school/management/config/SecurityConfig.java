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

    // CORS Configuration: Configure allowed origins via environment variable
    // Default: http://localhost:4200 (local development)
    // Production: Set CORS_ALLOWED_ORIGINS to your Vercel frontend URL (e.g., https://your-app.vercel.app)
    // Multiple origins: separate with comma (e.g., https://app1.vercel.app,https://app2.vercel.app)
    @Value("${cors.allowed.origins:http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    // Split comma-separated origins and convert to list
                    List<String> origins = Arrays.asList(allowedOrigins.split(","));
                    config.setAllowedOrigins(origins);
                    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                    config.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/v1/aith/**",
                                "v2/api-docs",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-resources",
                                "/swagger-resources/**",
                                "/configuration/ui",
                                "/configuration/security",
                                "swagger-ui/**",
                                "/webjars/**",
                                "/swagger-ui.html").permitAll() // Allow Swagger UI
                        .anyRequest().permitAll()
                ) .csrf(AbstractHttpConfigurer::disable)// Désactiver CSRF si nécessaire, spécialement pour les API REST
               ;
        return http.build();
    }
}
