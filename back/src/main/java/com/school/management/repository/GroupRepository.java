package com.school.management.repository;

import com.school.management.persistance.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {
    List<GroupEntity> findByStudents_Id(Long studentId);

    // LEFT JOIN FETCH (et non JOIN FETCH) : un groupe peut avoir un type, un tarif ou un
    // enseignant non renseigné (ex. groupes importés par CSV). Un INNER JOIN exclurait alors
    // le groupe du résultat, provoquant un « groupe introuvable » (500).
    @Query("SELECT g FROM GroupEntity g " +
            "LEFT JOIN FETCH g.groupType " +
            "LEFT JOIN FETCH g.level " +
            "LEFT JOIN FETCH g.subject " +
            "LEFT JOIN FETCH g.price " +
            "LEFT JOIN FETCH g.teacher " +
            "WHERE g.id = :groupId")
    Optional<GroupEntity> findGroupWithDetailsById(@Param("groupId") Long groupId);

    // Groupes d'une année scolaire donnée (filtrage par année)
    List<GroupEntity> findBySchoolYearId(Long schoolYearId);

    // Nombre de groupes d'une année scolaire (statistiques dashboard)
    long countBySchoolYearId(Long schoolYearId);

    /**
     * Nombre de groupes non désactivés, toutes années confondues (statistiques dashboard).
     *
     * <p>La suppression d'un groupe est logique ({@code active = false}) : un {@code count()}
     * brut compterait donc encore les groupes retirés.</p>
     */
    @Query("SELECT COUNT(g) FROM GroupEntity g WHERE g.active IS NULL OR g.active = true")
    long countActive();

    /** Idem, restreint à une année scolaire. */
    @Query("SELECT COUNT(g) FROM GroupEntity g "
            + "WHERE g.schoolYear.id = :schoolYearId AND (g.active IS NULL OR g.active = true)")
    long countActiveBySchoolYearId(@Param("schoolYearId") Long schoolYearId);

    /**
     * Groupes non désactivés, toutes années confondues.
     *
     * <p>Utilisé par les listes : un groupe désactivé doit disparaître de l'interface, sinon
     * l'action « désactiver » n'a aucun effet visible.</p>
     */
    @Query("SELECT g FROM GroupEntity g WHERE g.active IS NULL OR g.active = true")
    List<GroupEntity> findAllActive();

    /** Idem, restreint à une année scolaire. */
    @Query("SELECT g FROM GroupEntity g "
            + "WHERE g.schoolYear.id = :schoolYearId AND (g.active IS NULL OR g.active = true)")
    List<GroupEntity> findActiveBySchoolYearId(@Param("schoolYearId") Long schoolYearId);

    // Groupes sans année scolaire (utilisé par la migration pour les rattacher à l'année initiale)
    List<GroupEntity> findBySchoolYearIsNull();

}
