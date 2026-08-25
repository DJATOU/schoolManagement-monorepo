package com.school.management.repository;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface StudentGroupRepository extends JpaRepository<StudentGroupEntity, Long> {

    @Query("SELECT sg FROM StudentGroupEntity sg WHERE sg.group.id = :groupId")
    List<StudentGroupEntity> findByGroupId(Long groupId);

    boolean existsByStudentAndGroupAndActiveTrue(StudentEntity student, GroupEntity group);

    Optional<StudentGroupEntity> findByGroupIdAndStudentIdAndActiveTrue(Long groupId, Long studentId);

    List<StudentGroupEntity> findByGroupIdAndActiveTrue(Long groupId);

    /** Vrai si l'étudiant est actuellement inscrit (affectation active) dans ce groupe. */
    boolean existsByGroupIdAndStudentIdAndActiveTrue(Long groupId, Long studentId);

    List<StudentGroupEntity> findByStudentIdAndActiveTrue(Long studentId);

    /**
     * Toutes les inscriptions de l'étudiant, <strong>clôturées comprises</strong>.
     *
     * <p>Une inscription clôturée est une ligne dont {@code active} vaut faux
     * ({@code StudentGroupService.removeStudentFromGroup} ne supprime pas la ligne, il la
     * désactive). Les variantes {@code ...AndActiveTrue} sont donc aveugles aux clôtures, alors
     * que le signalement de changement de groupe (exigence 10.1) a précisément besoin de
     * celles-ci : sans cette requête, un départ de groupe serait indétectable.</p>
     *
     * @param studentId identifiant de l'étudiant
     * @return les inscriptions de l'étudiant, actives et clôturées
     */
    List<StudentGroupEntity> findByStudentId(Long studentId);

    /**
     * Étudiants affectés au groupe au moment d'une séance : l'affectation doit être
     * antérieure au début de la séance, ce qui évite de faire apparaître comme absent
     * un étudiant inscrit après coup.
     *
     * <p>L'affectation doit aussi être encore active : sans ce filtre, un étudiant retiré
     * du groupe continuait d'apparaître sur la feuille de présence. Les lignes héritées
     * dont {@code active} est nul sont considérées actives.</p>
     */
    @Query("SELECT sg FROM StudentGroupEntity sg "
            + "WHERE sg.group.id = :groupId AND sg.dateAssigned <= :sessionDate "
            + "AND (sg.active IS NULL OR sg.active = true)")
    List<StudentGroupEntity> findByGroupIdAndDateAssignedBefore(
            @Param("groupId") Long groupId,
            @Param("sessionDate") Date sessionDate
    );

    /**
     * Étudiants distincts inscrits (inscription active) dans un groupe appartenant à l'année
     * scolaire donnée. Sert à afficher la liste figée des étudiants d'une année passée
     * (historique), l'année vivant sur le groupe et non directement sur l'étudiant.
     *
     * @param schoolYearId identifiant de l'année scolaire
     * @return les étudiants distincts inscrits dans les groupes de cette année
     */
    @Query("SELECT DISTINCT sg.student FROM StudentGroupEntity sg "
            + "WHERE sg.group.schoolYear.id = :schoolYearId AND sg.active = true")
    List<StudentEntity> findDistinctStudentsBySchoolYearId(@Param("schoolYearId") Long schoolYearId);

}