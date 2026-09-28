package com.school.management.persistance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Compte utilisateur de l'application (authentification & autorisation).
 *
 * <p>Porte l'identifiant unique de connexion, le mot de passe <strong>haché (BCrypt)</strong>,
 * le rôle ({@link Role#ADMIN} / {@link Role#VIEWER}) et un indicateur d'activation. La table
 * est nommée {@code app_user} car {@code user} est un mot réservé PostgreSQL. Comme les autres
 * entités, elle étend {@link BaseEntity} : ses propres écritures sont donc auditées.</p>
 */
@Entity
@Table(name = "app_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifiant unique de connexion
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    // Mot de passe haché (BCrypt) — jamais stocké ni exposé en clair
    @Column(name = "password", nullable = false)
    private String password;

    // Rôle du compte (ADMIN : accès complet ; VIEWER : lecture seule)
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    // Indicateur d'activation : un compte désactivé ne peut plus se connecter
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
