package com.school.management.repository;

import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Accès aux données des comptes utilisateurs (authentification & autorisation).
 *
 * <p>Fournit les recherches nécessaires : chargement par identifiant (connexion et
 * {@code UserDetailsService}), vérification d'unicité de l'identifiant (création de compte),
 * et présence d'un ADMIN (création du compte ADMIN initial au démarrage).</p>
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // Chargement d'un compte par identifiant (login + UserDetailsService)
    Optional<UserEntity> findByUsername(String username);

    // Unicité de l'identifiant (création de compte)
    boolean existsByUsername(String username);

    // Présence d'un compte d'un rôle donné (ADMIN initial)
    boolean existsByRole(Role role);
}
