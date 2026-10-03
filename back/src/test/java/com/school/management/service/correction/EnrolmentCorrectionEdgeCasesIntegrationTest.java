package com.school.management.service.correction;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.StudentGroupEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas limites des corrections de dates d'inscription, appelées directement sur H2 (spec
 * admin-corrections, C.6). Le comportement ordinaire est éprouvé de bout en bout par
 * {@code EnrolmentCorrectionEndpointIntegrationTest} ; ici, les données incomplètes ou rares que
 * le service doit traverser sans erreur : inscription non datée, séance sans série ou supprimée,
 * présence dans un autre groupe, deux inscriptions au même groupe.
 */
@DisplayName("Corrections de dates d'inscription — cas limites")
class EnrolmentCorrectionEdgeCasesIntegrationTest extends CorrectionIntegrationTestSupport {

    private static final CorrectionReason ARRIVAL = CorrectionReason.of(CorrectionReasonType.ARRIVAL_DATE_CORRECTED);
    private static final CorrectionReason LEFT = CorrectionReason.of(CorrectionReasonType.STUDENT_LEFT);

    @Autowired private EnrolmentCorrectionService corrections;

    private Long enrolmentId;
    private SessionEntity jan7;

    @BeforeEach
    void amineSinceSeptember() {
        enrolmentId = studentGroupRepository.findByGroupIdAndStudentId(group.getId(), student.getId()).get(0).getId();
        window(enrolmentId, LocalDate.of(2029, 9, 1), null);
        jan7 = s1First;
    }

    private List<String> effects(CorrectionOutcome<?> outcome) {
        return outcome.preview().effects().stream().map(CorrectionEffect::description).toList();
    }

    @Test
    @DisplayName("sans présence à noter (null) : accepté comme une liste vide")
    void nullMarksAreEmpty() {
        assertThat(corrections.correctArrival(enrolmentId, LocalDate.of(2029, 10, 1), null, ARRIVAL,
                CorrectionMode.PREVIEW, null).preview().effects()).hasSize(1);
    }

    @Test
    @DisplayName("inscription close : son arrivée se corrige avant le départ")
    void closedEnrolmentArrivalBeforeDeparture() {
        window(enrolmentId, LocalDate.of(2029, 9, 1), LocalDate.of(2030, 1, 14));

        assertThat(corrections.correctArrival(enrolmentId, LocalDate.of(2029, 10, 1), Map.of(), ARRIVAL,
                CorrectionMode.PREVIEW, null).preview().effects().get(0).description())
                .isEqualTo("Arrivée de Amine Belkacem dans « Math 1ère A » : 01/09/2029 → 01/10/2029");
    }

    @Test
    @DisplayName("inscription non datée : l'arrivée se pose, le départ s'enregistre")
    void undatedEnrolment() {
        jdbc.update("UPDATE student_groups SET date_assigned = NULL WHERE id = ?", enrolmentId);

        assertThat(effects(corrections.correctArrival(enrolmentId, LocalDate.of(2029, 10, 1), Map.of(), ARRIVAL,
                CorrectionMode.PREVIEW, null)).get(0))
                .isEqualTo("Arrivée de Amine Belkacem dans « Math 1ère A » : non datée → 01/10/2029");
        assertThat(effects(corrections.setDeparture(enrolmentId, LocalDate.of(2030, 1, 14), false, Map.of(), LEFT,
                CorrectionMode.PREVIEW, null)).get(0)).contains("enregistré au 14/01/2030");
    }

    @Test
    @DisplayName("deux inscriptions au groupe : corriger l'une sans toucher l'autre est admis, dans les deux sens")
    void twoEnrolmentsWithoutOverlap() {
        window(enrolmentId, LocalDate.of(2029, 9, 1), LocalDate.of(2029, 12, 31));
        Long returned = enrol(LocalDate.of(2030, 2, 1));

        assertThat(corrections.setDeparture(enrolmentId, LocalDate.of(2029, 12, 20), false, Map.of(), LEFT,
                CorrectionMode.PREVIEW, null).preview().effects()).isNotEmpty();
        assertThat(corrections.correctArrival(returned, LocalDate.of(2030, 2, 3), Map.of(), ARRIVAL,
                CorrectionMode.PREVIEW, null).preview().effects()).isNotEmpty();
        assertThatThrownBy(() -> corrections.setDeparture(enrolmentId, LocalDate.of(2030, 2, 10), false, Map.of(),
                LEFT, CorrectionMode.PREVIEW, null))
                .hasMessageContaining("chevaucherait l'autre inscription");
    }

    @Test
    @DisplayName("autre inscription non datée au groupe : ne chevauche rien")
    void undatedOtherEnrolmentNeverOverlaps() {
        Long other = enrol(LocalDate.of(2030, 2, 1));
        jdbc.update("UPDATE student_groups SET date_assigned = NULL, active = FALSE, date_left = ? WHERE id = ?",
                Timestamp.valueOf(LocalDate.of(2029, 12, 1).atStartOfDay()), other);

        assertThat(corrections.correctArrival(enrolmentId, LocalDate.of(2029, 10, 1), Map.of(), ARRIVAL,
                CorrectionMode.PREVIEW, null).preview().effects()).hasSize(1);
    }

    @Test
    @DisplayName("présences ailleurs, sans séance, ou sur une séance sans groupe ; séance supprimée : ignorées")
    void foreignOrIncompleteDataIsIgnored() {
        GroupEntity physique = groupRepository.save(GroupEntity.builder().name("Physique 1ère A")
                .price(group.getPrice()).schoolYear(year).sessionNumberPerSerie(2).build());
        SessionEntity elsewhere = sessionRepository.save(SessionEntity.builder().title("Physique").group(physique)
                .sessionTimeStart(at(LocalDate.of(2029, 9, 10))).build());
        SessionEntity orphan = sessionRepository.save(SessionEntity.builder().title("Sans groupe")
                .sessionTimeStart(at(LocalDate.of(2029, 9, 10))).build());
        mark(elsewhere, false, physique);
        mark(orphan, false, null);
        jdbc.update("INSERT INTO attendance (student_id, status, active) VALUES (?, FALSE, TRUE)", student.getId());
        SessionEntity deleted = sessionRepository.save(SessionEntity.builder().title("Supprimée").group(group)
                .sessionSeries(s1).sessionTimeStart(at(LocalDate.of(2029, 9, 10))).isFinished(true).build());
        jdbc.update("UPDATE session SET active = FALSE WHERE id = ?", deleted.getId());

        CorrectionOutcome<EnrolmentCorrection> outcome = corrections.correctArrival(enrolmentId,
                LocalDate.of(2029, 10, 1), Map.of(), ARRIVAL, CorrectionMode.PREVIEW, null);

        assertThat(outcome.preview().effects()).extracting(CorrectionEffect::type)
                .containsExactly(CorrectionEffectType.ENROLMENT_WINDOW_CHANGED);
    }

    @Test
    @DisplayName("séance validée qui entre dans la période avec déjà une présence : rien à y noter")
    void enteringSessionAlreadyMarked() {
        window(enrolmentId, LocalDate.of(2030, 1, 10), null);
        jdbc.update("UPDATE session SET is_finished = TRUE WHERE id = ?", jan7.getId());
        mark(jan7, true, group);

        assertThat(corrections.correctArrival(enrolmentId, LocalDate.of(2030, 1, 5), Map.of(), ARRIVAL,
                CorrectionMode.PREVIEW, null).preview().effects()).extracting(CorrectionEffect::type)
                .containsExactly(CorrectionEffectType.ENROLMENT_WINDOW_CHANGED);
    }

    @Test
    @DisplayName("présence notée dans la même opération")
    void presenceRecorded() {
        window(enrolmentId, LocalDate.of(2030, 1, 10), null);
        jdbc.update("UPDATE session SET is_finished = TRUE WHERE id = ?", jan7.getId());

        assertThat(effects(corrections.correctArrival(enrolmentId, LocalDate.of(2030, 1, 5), Map.of(jan7.getId(), true),
                ARRIVAL, CorrectionMode.PREVIEW, null)))
                .contains("Présence de Amine Belkacem le 07/01/2030 (« Janvier ») enregistrée");
    }

    @Test
    @DisplayName("séance sans série : son absence sort de la période et la trace n'a pas de série")
    void sessionWithoutSeries() {
        SessionEntity loose = sessionRepository.save(SessionEntity.builder().title("Hors série").group(group)
                .sessionTimeStart(at(LocalDate.of(2029, 9, 10))).build());
        mark(loose, false, group);

        // L'Aperçu est annulé : la trace a existé le temps de la mesure, l'effet seul en témoigne.
        assertThat(effects(corrections.correctArrival(enrolmentId, LocalDate.of(2029, 10, 1), Map.of(), ARRIVAL,
                CorrectionMode.PREVIEW, null)))
                .contains("Absence de Amine Belkacem le 10/09/2029 (« sans série ») retirée : hors de la nouvelle période");
    }

    // ------------------------------------------------------------------

    private void window(Long id, LocalDate arrival, LocalDate departure) {
        jdbc.update("UPDATE student_groups SET date_assigned = ?, date_left = ?, active = ? WHERE id = ?",
                Timestamp.valueOf(arrival.atStartOfDay()),
                departure == null ? null : Timestamp.valueOf(departure.atStartOfDay()), departure == null, id);
    }

    private Long enrol(LocalDate arrival) {
        return studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(group)
                .dateAssigned(at(arrival)).build()).getId();
    }

    private void mark(SessionEntity session, boolean present, GroupEntity in) {
        attendanceRepository.save(AttendanceEntity.builder().student(student).session(session)
                .sessionSeries(session.getSessionSeries()).group(in).isPresent(present).isCatchUp(false).build());
    }

    private static Date at(LocalDate day) {
        return Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }
}
