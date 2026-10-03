package com.school.management.controller.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.StudentAbsenceDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.CatchUpRequestRepository;
import com.school.management.service.CatchUpService;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retirer une présence de rattrapage saisie à tort (spec admin-corrections, D.2 ; exigences 9.1 à
 * 9.3, D9).
 *
 * <p>Socle : Amine Belkacem, inscrit au groupe « Math 1ère A » depuis le 01/09/2029, absent le
 * 07/01/2030 ; rattrapé le 09/01/2030 dans « Math 1ère B », séance non validée. Le rattrapage est
 * compensatoire : gratuit en B, il fait compter la séance du 07/01 comme suivie en janvier (A).</p>
 */
@AutoConfigureMockMvc
@DisplayName("POST /api/attendances/{id}/remove : présence de rattrapage")
class CatchUpRemovalEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CatchUpRequestRepository catchUpRequestRepository;
    @Autowired private CatchUpService catchUpService;

    private GroupEntity groupB;
    private SessionSeriesEntity januaryB;
    private SessionEntity jan7;
    private SessionEntity jan9InB;
    private Long absence;

    @BeforeEach
    void amineCaughtUpInGroupB() {
        Long enrolmentId = studentGroupRepository.findByGroupIdAndStudentId(group.getId(), student.getId()).get(0).getId();
        jdbc.update("UPDATE student_groups SET date_assigned = ? WHERE id = ?",
                Timestamp.valueOf(LocalDate.of(2029, 9, 1).atStartOfDay()), enrolmentId);
        groupB = groupRepository.save(GroupEntity.builder().name("Math 1ère B").price(group.getPrice())
                .schoolYear(year).sessionNumberPerSerie(2).build());
        januaryB = persistSeries(groupB, "Janvier B", date(2030, 1, 9));
        jan7 = sessionOn(s1, LocalDate.of(2030, 1, 7));
        jan9InB = sessionOn(januaryB, LocalDate.of(2030, 1, 9));
        jdbc.update("UPDATE session SET is_finished = TRUE WHERE id = ?", jan7.getId());
        absence = attendanceRepository.save(AttendanceEntity.builder().student(student).session(jan7).sessionSeries(s1)
                .group(group).isPresent(false).isJustified(false).isCatchUp(false).build()).getId();
    }

    /** Les demandes de rattrapage ne sont pas vidées par le socle : elles bloqueraient sa purge. */
    @AfterEach
    void removeCatchUpRequests() {
        catchUpRequestRepository.deleteAll();
    }

    @Test
    @DisplayName("Aperçu : rattrapage retiré, le 07/01 redevient à rattraper, la demande serait annulée ; "
            + "janvier (A) perd la séance comptée suivie ; rien n'est écrit")
    void previewNamesBothGroups() throws Exception {
        Long catchUp = catchUp(jan7, CatchUpBillingState.RESOLVED, true);
        CatchUpRequestEntity request = completedRequest();

        remove(catchUp, "preview", "DATA_ENTRY_ERROR", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview.effects[0].type").value("CATCH_UP_REMOVED"))
                .andExpect(jsonPath("$.preview.effects[0].description").value("Rattrapage de Amine Belkacem du "
                        + "09/01/2030 (Math 1ère B) retiré : séance manquée du 07/01/2030 (Math 1ère A), déjà payée : oui"))
                .andExpect(jsonPath("$.preview.effects[1].type").value("CATCH_UP_REOPENED"))
                .andExpect(jsonPath("$.preview.effects[1].description").value(
                        "Séance du 07/01/2030 (Math 1ère A) : de nouveau à rattraper pour Amine Belkacem"))
                .andExpect(jsonPath("$.preview.effects[2].type").value("CATCH_UP_REQUEST_CANCELLED"))
                .andExpect(jsonPath("$.preview.effects[2].description").value(
                        "Demande de rattrapage sur la séance du 09/01/2030 annulée"))
                .andExpect(jsonPath("$.preview.series[0].seriesName").value("Janvier"))
                .andExpect(jsonPath("$.preview.series[0].groupName").value("Math 1ère A"))
                .andExpect(jsonPath("$.preview.series[0].before.dueSoFar").value(2000.0))
                .andExpect(jsonPath("$.preview.series[0].after.dueSoFar").value(0.0))
                .andExpect(jsonPath("$.preview.series.length()").value(1));

        assertThat(active(catchUp)).isTrue();
        assertThat(statusOf(request)).isEqualTo("COMPLETED");
        assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
    }

    @Test
    @DisplayName("Confirmation : présence désactivée, demande annulée, absence de nouveau rattrapable (9.2) ; "
            + "la trace garde la séance manquée et la décision « déjà payée » (9.3)")
    void confirmReopensTheCatchUpRight() throws Exception {
        Long catchUp = catchUp(jan7, CatchUpBillingState.RESOLVED, true);
        CatchUpRequestEntity request = completedRequest();
        assertThat(eligibleAbsences()).doesNotContain(absence);
        String token = token(remove(catchUp, "preview", "DATA_ENTRY_ERROR", null, null));

        remove(catchUp, "confirm", "DATA_ENTRY_ERROR", null, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.attendanceId").value(catchUp))
                .andExpect(jsonPath("$.result.active").value(false));

        assertThat(active(catchUp)).isFalse();
        assertThat(count("SELECT COUNT(*) FROM attendance WHERE id = " + catchUp)).as("désactivée, pas supprimée")
                .isEqualTo(1);
        assertThat(statusOf(request)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM catch_up_request WHERE id = ?", String.class,
                request.getId())).isEqualTo("Présence de rattrapage retirée par correction (DATA_ENTRY_ERROR)");
        assertThat(eligibleAbsences()).contains(absence);
        assertThat(attendanceRepository.existsByStudentIdAndMissedSessionIdAndActiveTrue(student.getId(), jan7.getId()))
                .isFalse();

        Map<String, Object> trace = jdbc.queryForMap("SELECT * FROM correction_audit");
        assertThat(trace).containsEntry("ACTION", "CATCH_UP_REMOVED").containsEntry("DOMAIN", "ATTENDANCE")
                .containsEntry("ENTITY_ID", catchUp).containsEntry("SESSION_ID", jan9InB.getId())
                .containsEntry("SERIES_ID", januaryB.getId()).containsEntry("GROUP_ID", groupB.getId());
        assertThat((String) trace.get("OLD_VALUE")).contains("\"active\":true", "\"billingState\":\"RESOLVED\"",
                "\"missedSessionId\":" + jan7.getId(), "\"missedSessionDay\":\"07/01/2030\"",
                "\"missedSessionAlreadyPaid\":true",
                "\"catchUpRequests\":[{\"id\":" + request.getId() + ",\"status\":\"COMPLETED\"}]");
        assertThat((String) trace.get("NEW_VALUE")).contains("\"active\":false",
                "\"catchUpRequests\":[{\"id\":" + request.getId() + ",\"status\":\"CANCELLED\"}]");
    }

    @Test
    @DisplayName("ensuite, l'absence d'origine se corrige de nouveau : le verrou du rattrapage est levé")
    void theAbsenceIsUnlocked() throws Exception {
        Long catchUp = catchUp(jan7, CatchUpBillingState.RESOLVED, true);
        String token = token(remove(catchUp, "preview", "DATA_ENTRY_ERROR", null, null));
        remove(catchUp, "confirm", "DATA_ENTRY_ERROR", null, token).andExpect(status().isOk());

        send("/api/attendances/" + absence + "/correct/preview", "{\"present\":true,\"reasonType\":\"DATA_ENTRY_ERROR\"}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Motif « Autre » : son texte est repris dans la raison d'annulation de la demande")
    void otherReasonTextIsKept() throws Exception {
        Long catchUp = catchUp(jan7, CatchUpBillingState.RESOLVED, true);
        CatchUpRequestEntity request = completedRequest();
        String token = token(remove(catchUp, "preview", "OTHER", "Saisi sur le mauvais élève", null));

        remove(catchUp, "confirm", "OTHER", "Saisi sur le mauvais élève", token).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM catch_up_request WHERE id = ?", String.class,
                request.getId())).isEqualTo("Présence de rattrapage retirée par correction (OTHER : Saisi sur le mauvais "
                        + "élève)");
    }

    @Test
    @DisplayName("facturé sur place : la série d'accueil perd la séance ; le versé reste, annoncé comme trop-perçu")
    void hostBilledCatchUpLeavesAnExcess() throws Exception {
        Long catchUp = catchUp(null, CatchUpBillingState.HOST_BILLED, null);
        processing.processCatchUpPayment(student.getId(), jan9InB.getId(), 2000);
        CatchUpRequestEntity scheduled = catchUpRequestRepository.save(CatchUpRequestEntity.builder().student(student)
                .originalGroup(group).catchUpSession(jan9InB).catchUpGroup(groupB).status(CatchUpStatus.SCHEDULED)
                .build());
        String token = token(remove(catchUp, "preview", "DATA_ENTRY_ERROR", null, null)
                .andExpect(jsonPath("$.preview.effects[0].description").value("Rattrapage de Amine Belkacem du "
                        + "09/01/2030 (Math 1ère B) retiré : facturé sur place, sans séance manquée"))
                .andExpect(jsonPath("$.preview.effects[*].type").value(not(hasItem("CATCH_UP_REOPENED"))))
                .andExpect(jsonPath("$.preview.effects[*].description").value(hasItem(
                        "Demande de rattrapage sur la séance du 09/01/2030 annulée")))
                .andExpect(jsonPath("$.preview.effects[*].description").value(hasItem(
                        "Trop-perçu de 2 000,00 DA sur « Janvier B » : ni reporté ni remboursé par cette correction")))
                .andExpect(jsonPath("$.preview.series[0].seriesName").value("Janvier B"))
                .andExpect(jsonPath("$.preview.series[0].before.cost").value(2000.0))
                .andExpect(jsonPath("$.preview.series[0].after.cost").value(0.0))
                .andExpect(jsonPath("$.preview.series[0].after.paid").value(2000.0)));

        remove(catchUp, "confirm", "DATA_ENTRY_ERROR", null, token).andExpect(status().isOk());

        assertThat(statusOf(scheduled)).isEqualTo("CANCELLED");
        assertThat(cumulOf(januaryB)).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("décision « déjà payée » : non, ou non tranchée ; rattrapage à préciser sans séance manquée")
    void decisionsAreWrittenOut() throws Exception {
        Long notPaid = catchUp(jan7, CatchUpBillingState.RESOLVED, false);
        remove(notPaid, "preview", "DATA_ENTRY_ERROR", null, null)
                .andExpect(jsonPath("$.preview.effects[0].description").value(containsString("déjà payée : non")));
        jdbc.update("UPDATE attendance SET missed_session_already_paid = NULL, catch_up_billing_state = NULL WHERE id = ?",
                notPaid);
        remove(notPaid, "preview", "DATA_ENTRY_ERROR", null, null)
                .andExpect(jsonPath("$.preview.effects[0].description").value(containsString("déjà payée : non tranché")));

        jdbc.update("UPDATE attendance SET missed_session_id = NULL, catch_up_billing_state = 'PENDING' WHERE id = ?",
                notPaid);
        remove(notPaid, "preview", "DATA_ENTRY_ERROR", null, null)
                .andExpect(jsonPath("$.preview.effects[0].description").value(
                        containsString("retiré : séance manquée non renseignée")));
    }

    @Test
    @DisplayName("seules les demandes qui ont produit ce rattrapage sont annulées")
    void onlyTheProducingRequestIsCancelled() throws Exception {
        Long catchUp = catchUp(jan7, CatchUpBillingState.RESOLVED, true);
        SessionEntity jan14 = sessionOn(s1, LocalDate.of(2030, 1, 14));
        CatchUpRequestEntity cancelled = request(jan7, jan9InB, CatchUpStatus.CANCELLED);
        CatchUpRequestEntity otherMissed = request(jan14, jan9InB, CatchUpStatus.SCHEDULED);
        CatchUpRequestEntity notScheduled = request(jan7, null, CatchUpStatus.PENDING);
        String token = token(remove(catchUp, "preview", "DATA_ENTRY_ERROR", null, null)
                .andExpect(jsonPath("$.preview.effects[*].type").value(not(hasItem("CATCH_UP_REQUEST_CANCELLED")))));

        remove(catchUp, "confirm", "DATA_ENTRY_ERROR", null, token).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM catch_up_request WHERE id = ?", String.class,
                cancelled.getId())).isNull();
        assertThat(statusOf(otherMissed)).isEqualTo("SCHEDULED");
        assertThat(statusOf(notScheduled)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("refus : séance d'accueil supprimée, année close à l'accueil ou à l'origine")
    void refusals() throws Exception {
        Long catchUp = catchUp(jan7, CatchUpBillingState.RESOLVED, true);
        jdbc.update("UPDATE session SET active = FALSE WHERE id = ?", jan9InB.getId());
        remove(catchUp, "preview", "DATA_ENTRY_ERROR", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La séance du 09/01/2030 a été supprimée : ses présences ne se "
                        + "corrigent plus."));
        jdbc.update("UPDATE session SET active = TRUE WHERE id = ?", jan9InB.getId());

        SchoolYearEntity past = schoolYearRepository.save(SchoolYearEntity.builder().label("2028-2029")
                .startDate(date(2028, 9, 1)).endDate(date(2029, 6, 30)).isCurrent(false).build());
        GroupEntity old = groupRepository.save(GroupEntity.builder().name("Math 2028").price(group.getPrice())
                .schoolYear(past).sessionNumberPerSerie(2).build());
        SessionSeriesEntity october = persistSeries(old, "Octobre 2028", date(2028, 10, 2));
        SessionEntity oldSession = sessionOn(october, LocalDate.of(2028, 10, 2));

        jdbc.update("UPDATE attendance SET missed_session_id = ? WHERE id = ?", oldSession.getId(), catchUp);
        remove(catchUp, "preview", "DATA_ENTRY_ERROR", null, null).andExpect(status().isConflict());

        Long oldCatchUp = attendanceRepository.save(AttendanceEntity.builder().student(student).session(oldSession)
                .sessionSeries(october).group(old).isPresent(true).isCatchUp(true).build()).getId();
        remove(oldCatchUp, "preview", "DATA_ENTRY_ERROR", null, null).andExpect(status().isConflict());

        assertThat(active(catchUp)).isTrue();
        assertThat(active(oldCatchUp)).isTrue();
        assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /** Présence de rattrapage d'Amine le 09/01 en B, compensant {@code missed} s'il est donné. */
    private Long catchUp(SessionEntity missed, CatchUpBillingState state, Boolean alreadyPaid) {
        return attendanceRepository.save(AttendanceEntity.builder().student(student).session(jan9InB)
                .sessionSeries(januaryB).group(groupB).isPresent(true).isCatchUp(true).missedSession(missed)
                .catchUpBillingState(state).missedSessionAlreadyPaid(alreadyPaid).build()).getId();
    }

    /** La demande qui a produit le rattrapage : absence du 07/01, rattrapée le 09/01 en B. */
    private CatchUpRequestEntity completedRequest() {
        return catchUpRequestRepository.save(CatchUpRequestEntity.builder().student(student).originalSession(jan7)
                .originalGroup(group).originalAttendance(attendanceRepository.findById(absence).orElseThrow())
                .catchUpSession(jan9InB).catchUpGroup(groupB).status(CatchUpStatus.COMPLETED).build());
    }

    private CatchUpRequestEntity request(SessionEntity original, SessionEntity catchUpSession, CatchUpStatus status) {
        return catchUpRequestRepository.save(CatchUpRequestEntity.builder().student(student).originalSession(original)
                .originalGroup(group).catchUpSession(catchUpSession).catchUpGroup(groupB).status(status).build());
    }

    private ResultActions remove(Long attendanceId, String step, String reason, String text, String token)
            throws Exception {
        return send("/api/attendances/" + attendanceId + "/remove/" + step, "{\"reasonType\":\"" + reason + "\""
                + (text == null ? "" : ",\"reasonText\":\"" + text + "\"")
                + (token == null ? "" : ",\"previewToken\":\"" + token + "\"") + "}");
    }

    private ResultActions send(String url, String json) throws Exception {
        return mockMvc.perform(post(url).with(user("directrice").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(ResultActions preview) throws Exception {
        JsonNode body = objectMapper.readTree(preview.andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        return body.get("previewToken").asText();
    }

    private boolean active(Long attendanceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT active FROM attendance WHERE id = ?", Boolean.class,
                attendanceId));
    }

    private String statusOf(CatchUpRequestEntity request) {
        return jdbc.queryForObject("SELECT status FROM catch_up_request WHERE id = ?", String.class, request.getId());
    }

    private java.util.List<Long> eligibleAbsences() {
        return catchUpService.getEligibleAbsences(student.getId()).stream().map(StudentAbsenceDTO::attendanceId).toList();
    }

    private SessionEntity sessionOn(SessionSeriesEntity series, LocalDate day) {
        return sessionRepository.findAll().stream()
                .filter(s -> s.getSessionSeries() != null && s.getSessionSeries().getId().equals(series.getId()))
                .filter(s -> LocalDate.ofInstant(s.getSessionTimeStart().toInstant(), ZoneId.systemDefault()).equals(day))
                .findFirst().orElseThrow();
    }
}
