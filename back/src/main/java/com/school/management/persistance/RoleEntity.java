package com.school.management.persistance;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 20)
    private RoleName name;

    @Column(name = "description")
    private String description;

    public enum RoleName {
        ROLE_ADMIN,      // Administrateur - accès complet
        ROLE_TEACHER,    // Enseignant - gestion des cours et notes
        ROLE_STUDENT,    // Étudiant - consultation uniquement
        ROLE_PARENT      // Tuteur/Parent - consultation des enfants
    }
}
