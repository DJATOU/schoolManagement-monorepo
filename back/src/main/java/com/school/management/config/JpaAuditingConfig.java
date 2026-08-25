package com.school.management.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Active l'audit JPA de Spring Data, alimenté par {@code SecurityAuditorAware} pour renseigner
 * automatiquement {@code createdBy} / {@code updatedBy} avec l'utilisateur courant.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
public class JpaAuditingConfig {
}
