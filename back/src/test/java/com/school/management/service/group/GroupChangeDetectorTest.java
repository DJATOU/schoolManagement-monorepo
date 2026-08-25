package com.school.management.service.group;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.service.group.GroupChangeDetector.GroupChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (exemples, dépôts simulés) de {@link GroupChangeDetector}.
 *
 * <p>Le point central de ces tests est le <strong>périmètre</strong> du signalement : il porte
 * sur un changement de groupe et non sur l'appartenance à plusieurs groupes. Le test
 * {@code multiSubjectStudentIsNotFlagged} verrouille ce point ; sans lui, l'implémentation la plus
 * naturelle (deux groupes actifs sur un mois) transformerait chaque étudiant multi-matières en
 * alerte permanente que personne ne lirait (exigence 10.4).</p>
 *
 * <p>Une clôture d'inscription est ici reproduite comme en base : {@code active = false} et
 * {@code date_update} horodatée, {@code StudentGroupService.removeStudentFromGroup} désactivant
 * la ligne au lieu de la supprimer.</p>
 */
@ExtendWith(MockitoExtension.class)
class GroupChangeDetectorTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long MATHS_ID = 1L;
    private static final Long PHYSIQUE_ID = 2L;
    private static final Long ARABE_ID = 3L;

    @Mock private StudentGroupRepository studentGroupRepository;
    @Mock private AttendanceRepository attendanceRepository;

    @InjectMocks private GroupChangeDetector detector;

    // ------------------------------------------------------------------
    // Fabriques de données
    // ------------------------------------------------------------------

    private static Date date(String isoDate) {
        return Date.from(LocalDate.parse(isoDate).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static GroupEntity group(Long id, String name) {
        GroupEntity group = new GroupEntity();
        group.setId(id);
        group.setName(name);
        return group;
    }

    /** Inscription toujours active : ouverte à {@code dateAssigned}, jamais clôturée. */
    private static StudentGroupEntity open(GroupEntity group, String assignedOn) {
        StudentGroupEntity enrolment = new StudentGroupEntity();
        enrolment.setGroup(group);
        enrolment.setDateAssigned(date(assignedOn));
        enrolment.setActive(true);
        return enrolment;
    }

    /** Inscription clôturée : {@code active = false} et {@code date_update} à la clôture. */
    private static StudentGroupEntity closed(GroupEntity group, String assignedOn, String closedOn) {
        StudentGroupEntity enrolment = open(group, assignedOn);
        enrolment.setActive(false);
        enrolment.setDateUpdate(LocalDateTime.parse(closedOn + "T09:30:00"));
        return enrolment;
    }

    /** {@code Arrays.asList} et non {@code List.of} : un test injecte une ligne nulle. */
    private void givenEnrolments(StudentGroupEntity... enrolments) {
        when(studentGroupRepository.findByStudentId(STUDENT_ID)).thenReturn(Arrays.asList(enrolments));
    }

    private void givenAttended(Long groupId, long attended) {
        when(attendanceRepository.countPresentForStudentAndGroupBetween(
                eq(STUDENT_ID), eq(groupId), any(Date.class), any(Date.class)))
                .thenReturn(attended);
    }

    // ------------------------------------------------------------------
    // Exigence 10.1 et 10.3 — le changement de groupe est signalé
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Inscription clôturée dans un groupe et ouverte dans un autre le même mois : "
            + "signalement avec les deux groupes et leurs décomptes")
    void closureAndOpeningInSameMonthAreFlagged() {
        givenEnrolments(
                closed(group(MATHS_ID, "Maths 1B"), "2025-09-01", "2025-11-12"),
                open(group(PHYSIQUE_ID, "Physique 1B"), "2025-11-15"));
        givenAttended(MATHS_ID, 3);
        givenAttended(PHYSIQUE_ID, 2);

        List<GroupChange> changes = detector.detect(STUDENT_ID);

        assertThat(changes).hasSize(1);
        GroupChange change = changes.get(0);
        assertThat(change.year()).isEqualTo(2025);
        assertThat(change.month()).isEqualTo(11);
        assertThat(change.yearMonth()).hasToString("2025-11");
        assertThat(change.leftGroup().groupId()).isEqualTo(MATHS_ID);
        assertThat(change.leftGroup().groupName()).isEqualTo("Maths 1B");
        assertThat(change.leftGroup().attendedCount()).isEqualTo(3);
        assertThat(change.joinedGroup().groupId()).isEqualTo(PHYSIQUE_ID);
        assertThat(change.joinedGroup().groupName()).isEqualTo("Physique 1B");
        assertThat(change.joinedGroup().attendedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deux départs et une arrivée le même mois : un signalement par couple, "
            + "dans l'ordre chronologique")
    void everyClosureIsPairedWithEveryOpeningOfTheMonth() {
        givenEnrolments(
                closed(group(MATHS_ID, "Maths 1B"), "2025-09-01", "2025-11-12"),
                closed(group(PHYSIQUE_ID, "Physique 1B"), "2025-09-01", "2025-11-13"),
                open(group(ARABE_ID, "Arabe 1B"), "2025-11-20"));
        givenAttended(MATHS_ID, 1);
        givenAttended(PHYSIQUE_ID, 1);
        givenAttended(ARABE_ID, 4);

        List<GroupChange> changes = detector.detect(STUDENT_ID);

        assertThat(changes)
                .extracting(c -> c.leftGroup().groupId(), c -> c.joinedGroup().groupId())
                .containsExactly(tuple(MATHS_ID, ARABE_ID), tuple(PHYSIQUE_ID, ARABE_ID));
        // Le décompte du groupe rejoint n'est lu qu'une fois, malgré les deux signalements.
        verify(attendanceRepository).countPresentForStudentAndGroupBetween(
                eq(STUDENT_ID), eq(ARABE_ID), any(Date.class), any(Date.class));
    }

    @Test
    @DisplayName("Deux clôtures du même groupe le même mois : un seul signalement, "
            + "le même couple n'est pas annoncé deux fois")
    void identicalPairIsFlaggedOnlyOnce() {
        givenEnrolments(
                closed(group(MATHS_ID, "Maths 1B"), "2025-09-01", "2025-11-05"),
                closed(group(MATHS_ID, "Maths 1B"), "2025-11-07", "2025-11-20"),
                open(group(PHYSIQUE_ID, "Physique 1B"), "2025-11-22"));
        givenAttended(MATHS_ID, 2);
        givenAttended(PHYSIQUE_ID, 1);

        List<GroupChange> changes = detector.detect(STUDENT_ID);

        assertThat(changes)
                .extracting(c -> c.leftGroup().groupId(), c -> c.joinedGroup().groupId())
                .containsExactly(tuple(MATHS_ID, PHYSIQUE_ID));
    }

    // ------------------------------------------------------------------
    // Exigence 10.4 — ce qui ne doit PAS être signalé
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Étudiant inscrit simultanément à plusieurs matières, aucune clôture : "
            + "aucun signalement")
    void multiSubjectStudentIsNotFlagged() {
        givenEnrolments(
                open(group(MATHS_ID, "Maths 1B"), "2025-11-03"),
                open(group(PHYSIQUE_ID, "Physique 1B"), "2025-11-04"),
                open(group(ARABE_ID, "Arabe 1B"), "2025-11-05"));

        assertThat(detector.detect(STUDENT_ID)).isEmpty();
        // Aucune requête de comptage : le cas normal ne coûte rien.
        verifyNoInteractions(attendanceRepository);
    }

    @Test
    @DisplayName("Clôture et ouverture sur deux mois civils différents : aucun signalement")
    void closureAndOpeningInDifferentMonthsAreNotFlagged() {
        givenEnrolments(
                closed(group(MATHS_ID, "Maths 1B"), "2025-09-01", "2025-11-28"),
                open(group(PHYSIQUE_ID, "Physique 1B"), "2025-12-02"));

        assertThat(detector.detect(STUDENT_ID)).isEmpty();
        verify(attendanceRepository, never()).countPresentForStudentAndGroupBetween(
                anyLong(), anyLong(), any(Date.class), any(Date.class));
    }

    @Test
    @DisplayName("Aucune inscription clôturée : aucun signalement")
    void singleActiveEnrolmentIsNotFlagged() {
        givenEnrolments(open(group(MATHS_ID, "Maths 1B"), "2025-11-03"));

        assertThat(detector.detect(STUDENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Aucune inscription du tout : aucun signalement")
    void noEnrolmentAtAllIsNotFlagged() {
        when(studentGroupRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of());

        assertThat(detector.detect(STUDENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Départ puis retour dans le MÊME groupe le même mois : aucun signalement, "
            + "la facturation n'a pas changé de groupe")
    void reEnrolmentInTheSameGroupIsNotFlagged() {
        GroupEntity maths = group(MATHS_ID, "Maths 1B");
        givenEnrolments(
                closed(maths, "2025-09-01", "2025-11-10"),
                open(group(MATHS_ID, "Maths 1B"), "2025-11-18"));

        assertThat(detector.detect(STUDENT_ID)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Cas limites des données
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Inscription clôturée sans date de mise à jour : clôture non datable, "
            + "aucun signalement")
    void closureWithoutUpdateDateIsIgnored() {
        StudentGroupEntity undatedClosure = open(group(MATHS_ID, "Maths 1B"), "2025-09-01");
        undatedClosure.setActive(false);
        givenEnrolments(undatedClosure, open(group(PHYSIQUE_ID, "Physique 1B"), "2025-11-15"));

        assertThat(detector.detect(STUDENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Ligne héritée dont active est nul : considérée active, donc non clôturée")
    void legacyEnrolmentWithNullActiveIsTreatedAsOpen() {
        StudentGroupEntity legacy = open(group(MATHS_ID, "Maths 1B"), "2025-11-02");
        legacy.setActive(null);
        givenEnrolments(legacy, open(group(PHYSIQUE_ID, "Physique 1B"), "2025-11-15"));

        assertThat(detector.detect(STUDENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Inscription sans groupe ou sans date d'affectation : écartée sans erreur")
    void enrolmentsWithoutGroupOrDateAreSkipped() {
        StudentGroupEntity withoutGroup = new StudentGroupEntity();
        withoutGroup.setActive(false);
        withoutGroup.setDateUpdate(LocalDateTime.parse("2025-11-12T09:30:00"));

        StudentGroupEntity withoutId = new StudentGroupEntity();
        withoutId.setGroup(new GroupEntity());
        withoutId.setActive(false);

        StudentGroupEntity withoutAssignedDate = new StudentGroupEntity();
        withoutAssignedDate.setGroup(group(PHYSIQUE_ID, "Physique 1B"));
        withoutAssignedDate.setActive(true);

        givenEnrolments(withoutGroup, withoutId, withoutAssignedDate, null,
                closed(group(MATHS_ID, "Maths 1B"), "2025-09-01", "2025-11-12"));

        assertThat(detector.detect(STUDENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Identifiant d'étudiant nul : rejeté sans requête")
    void nullStudentIdIsRejected() {
        assertThatThrownBy(() -> detector.detect(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("studentId");

        verifyNoInteractions(studentGroupRepository, attendanceRepository);
    }
}
