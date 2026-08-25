package com.school.management.repository;

import com.school.management.persistance.TeacherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<TeacherEntity, Long> {

    // Custom methods:
    List<TeacherEntity> findByLastName(String lastName);

    List<TeacherEntity> findByFirstNameAndLastName(String firstName, String lastName);

    // Method to find teachers associated with a specific group
    List<TeacherEntity> findByGroups_Id(Long groupId);

    @Query("SELECT CONCAT(t.firstName, ' ', t.lastName) FROM TeacherEntity t WHERE t.id = :id")
    Optional<String> findTeacherNameById(Long id);

    /**
     * Nombre d'enseignants non désactivés (statistiques dashboard).
     *
     * <p>La suppression d'un enseignant est logique ({@code active = false}) : un
     * {@code count()} brut continuerait donc de compter les enseignants retirés. Les lignes
     * antérieures au champ peuvent avoir {@code active} à null, traitées comme actives.</p>
     */
    @Query("SELECT COUNT(t) FROM TeacherEntity t WHERE t.active IS NULL OR t.active = true")
    long countActive();

    /**
     * Enseignants non désactivés.
     *
     * <p>Utilisé par les listes : un enseignant désactivé doit disparaître de l'interface,
     * sinon l'action « désactiver » n'a aucun effet visible.</p>
     */
    @Query("SELECT t FROM TeacherEntity t WHERE t.active IS NULL OR t.active = true")
    List<TeacherEntity> findAllActive();

    /**
     * Nombre d'enseignants intervenus sur une année scolaire donnée (statistiques dashboard).
     *
     * <p>Un enseignant est rattaché à une année via les groupes qu'il encadre : on compte donc
     * les enseignants distincts des groupes actifs de cette année. Utilisé pour les années
     * <strong>passées</strong>, dont l'effectif est un historique figé — l'effectif global
     * ({@link #countActive()}) répondrait à la place « combien d'enseignants aujourd'hui ».</p>
     */
    @Query("SELECT COUNT(DISTINCT g.teacher.id) FROM GroupEntity g "
            + "WHERE g.schoolYear.id = :schoolYearId "
            + "AND (g.active IS NULL OR g.active = true) "
            + "AND g.teacher IS NOT NULL "
            + "AND (g.teacher.active IS NULL OR g.teacher.active = true)")
    long countDistinctBySchoolYearId(@Param("schoolYearId") Long schoolYearId);
}
