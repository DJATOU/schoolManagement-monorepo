package com.school.management.repository;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentGroupRepository extends JpaRepository<StudentGroupEntity, Long> {

    /**
     * Toutes les inscriptions du groupe, <strong>closes comprises</strong> : la Feuille_Appel
     * d'une Séance retient celles dont la fenêtre contient son jour, actives ou non (6.2).
     */
    @Query("SELECT sg FROM StudentGroupEntity sg WHERE sg.group.id = :groupId")
    List<StudentGroupEntity> findByGroupId(Long groupId);

    boolean existsByStudentAndGroupAndActiveTrue(StudentEntity student, GroupEntity group);

    Optional<StudentGroupEntity> findByGroupIdAndStudentIdAndActiveTrue(Long groupId, Long studentId);

    /**
     * Inscriptions de l'étudiant au groupe, <strong>closes comprises</strong> : un étudiant revenu
     * dans un groupe y a plusieurs fenêtres, et une absence est admise dans chacune d'elles.
     */
    List<StudentGroupEntity> findByGroupIdAndStudentId(Long groupId, Long studentId);

    List<StudentGroupEntity> findByGroupIdAndActiveTrue(Long groupId);

    /** Vrai si l'étudiant est actuellement inscrit (affectation active) dans ce groupe. */
    boolean existsByGroupIdAndStudentIdAndActiveTrue(Long groupId, Long studentId);

    List<StudentGroupEntity> findByStudentIdAndActiveTrue(Long studentId);

    /**
     * Toutes les inscriptions de l'étudiant, <strong>clôturées comprises</strong>.
     *
     * <p>Une inscription clôturée est une ligne dont {@code active} vaut faux
     * ({@code EnrolmentCorrectionService.setDeparture} ne supprime pas la ligne, il la
     * désactive et la date). Les variantes {@code ...AndActiveTrue} sont donc aveugles aux clôtures, alors
     * que le signalement de changement de groupe (exigence 10.1) a précisément besoin de
     * celles-ci : sans cette requête, un départ de groupe serait indétectable.</p>
     *
     * @param studentId identifiant de l'étudiant
     * @return les inscriptions de l'étudiant, actives et clôturées
     */
    List<StudentGroupEntity> findByStudentId(Long studentId);

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

    /**
     * L'étudiant est-il inscrit à un <strong>autre</strong> groupe de même niveau et même matière,
     * dans la même année scolaire que le groupe d'accueil ?
     *
     * <p>C'est le <strong>test déterminant du routage des rattrapages</strong> : il décide si une
     * présence hors groupe est un vrai rattrapage (une place lui est réservée ailleurs, la
     * facturation reste ancrée à la séance manquée) ou une séance à facturer sur place (aucune
     * place réservée nulle part).</p>
     *
     * <p><strong>Le type de groupe n'intervient pas.</strong> {@code group_type} désigne l'effectif
     * (petit, moyen, grand, individuel) ; il n'a rien à voir avec la paire niveau + matière testée
     * ici. Confondre les deux route l'étudiant dans le mauvais cas, donc facture au mauvais groupe.
     * La compatibilité par type et par prix est un autre test, porté par
     * {@code CatchUpService.isCompatible} : celui-ci décide <em>si</em> c'est un rattrapage, l'autre
     * <em>où</em> un rattrapage est autorisé.</p>
     *
     * <p>Deux bornes délibérées :</p>
     * <ul>
     *   <li><strong>même année scolaire</strong> : un rattrapage concerne une séance manquée cette
     *       année et rattrapée cette année. Un groupe de l'année précédente n'ouvre aucun droit ;</li>
     *   <li><strong>inscription clôturée comprise</strong> — aucun filtre sur {@code active}. Un
     *       étudiant ayant quitté un groupe reste débiteur, règle déjà admise pour l'encaissement :
     *       refuser son inscription clôturée reviendrait à requalifier son rattrapage en séance
     *       facturable sur place, donc à le faire payer deux fois.</li>
     * </ul>
     *
     * <p>Le groupe d'accueil est exclu de la recherche : un membre du groupe n'est jamais en
     * rattrapage chez lui. {@code AttendanceService} écarte déjà ce cas en amont, mais le résultat
     * ne doit pas dépendre de l'ordre des deux contrôles.</p>
     *
     * @param studentId     identifiant de l'étudiant
     * @param hostGroupId   groupe de la séance suivie, exclu de la recherche
     * @param levelId       niveau du groupe d'accueil ; aucune correspondance si nul
     * @param subjectId     matière du groupe d'accueil ; aucune correspondance si nul
     * @param schoolYearId  année scolaire du groupe d'accueil ; aucune correspondance si nulle
     * @return vrai lorsqu'un tel groupe existe (vrai rattrapage)
     */
    @Query("SELECT CASE WHEN COUNT(sg) > 0 THEN true ELSE false END "
            + "FROM StudentGroupEntity sg "
            + "WHERE sg.student.id = :studentId "
            + "AND sg.group.id <> :hostGroupId "
            + "AND sg.group.level.id = :levelId "
            + "AND sg.group.subject.id = :subjectId "
            + "AND sg.group.schoolYear.id = :schoolYearId")
    boolean existsEnrolmentInSameLevelAndSubject(
            @Param("studentId") Long studentId,
            @Param("hostGroupId") Long hostGroupId,
            @Param("levelId") Long levelId,
            @Param("subjectId") Long subjectId,
            @Param("schoolYearId") Long schoolYearId
    );

}