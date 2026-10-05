package com.school.management.controller.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.CatchUpRequestRepository;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dévalider une Séance validée par erreur, et refuser toute validation sur une année close (spec
 * admin-corrections, D.3 ; exigences 10.1, 10.2).
 *
 * <p>Socle : groupe « Math 1ère A », séances à 2 000 DA ; Amine Belkacem et Lina Haddad inscrits
 * depuis le 01/09/2029 ; séance du 07/01/2030 validée.</p>
 */
@AutoConfigureMockMvc
@DisplayName("POST /api/sessions/{id}/unvalidate ; validation sur une année close")
class SessionUnvalidationEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CatchUpRequestRepository catchUpRequestRepository;

    private Long enrolmentId;
    private StudentEntity lina;
    private SessionEntity jan7;

    @BeforeEach
    void januarySeventhValidated() {
        enrolmentId = studentGroupRepository.findByGroupIdAndStudentId(group.getId(), student.getId()).get(0).getId();
        window(LocalDate.of(2029, 9, 1));
        lina = studentRepository.save(StudentEntity.builder().firstName("Lina").lastName("Haddad").build());
        studentGroupRepository.save(StudentGroupEntity.builder().student(lina).group(group)
                .dateAssigned(at(LocalDate.of(2029, 9, 1))).build());
        jan7 = sessionOn(s1, LocalDate.of(2030, 1, 7));
        validate(jan7);
    }

    @AfterEach
    void removeCatchUpRequests() {
        catchUpRequestRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Dévalidation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Dévalidation")
    class Devalidation {

        @Test
        @DisplayName("Aperçu : chaque ligne nommée, le dû d'Amine baisse ; rien n'est écrit")
        void previewListsEveryLine() throws Exception {
            // Lina saisie d'abord : les lignes sont listées par nom, pas dans l'ordre de saisie.
            Long absence = mark(lina, jan7, false);
            Long amine = mark(student, jan7, true);

            unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value(nullValue()))
                    .andExpect(jsonPath("$.preview.effects[0].type").value("SESSION_UNVALIDATED"))
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) dévalidée : 2 lignes retirées"))
                    .andExpect(jsonPath("$.preview.effects[1].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : présence de Amine Belkacem retirée"))
                    .andExpect(jsonPath("$.preview.effects[2].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : absence de Lina Haddad retirée"))
                    .andExpect(jsonPath("$.preview.series[0].studentName").value("Amine Belkacem"))
                    .andExpect(jsonPath("$.preview.series[0].before.dueSoFar").value(2000.0))
                    .andExpect(jsonPath("$.preview.series[0].after.dueSoFar").value(0.0));

            assertThat(finished(jan7)).isTrue();
            assertThat(active(amine)).isTrue();
            assertThat(active(absence)).isTrue();
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }

        @Test
        @DisplayName("Confirmation : séance à valider de nouveau, lignes désactivées ; une Trace de la séance "
                + "qui les liste (10.1), une par ligne pour le Journal")
        void confirmDeactivatesAndTraces() throws Exception {
            Long absence = mark(lina, jan7, false);
            Long amine = mark(student, jan7, true);
            String token = token(unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null));

            unvalidate(jan7, "confirm", "DATA_ENTRY_ERROR", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.sessionId").value(jan7.getId()))
                    .andExpect(jsonPath("$.result.removedLines").value(2));

            assertThat(finished(jan7)).isFalse();
            assertThat(active(amine)).isFalse();
            assertThat(active(absence)).isFalse();
            assertThat(count("SELECT COUNT(*) FROM attendance")).as("désactivées, pas supprimées").isEqualTo(2);

            List<Map<String, Object>> traces = jdbc.queryForList("SELECT * FROM correction_audit ORDER BY id");
            assertThat(traces).extracting(row -> row.get("ACTION"))
                    .containsExactly("SESSION_UNVALIDATED", "ATTENDANCE_REMOVED", "ATTENDANCE_REMOVED");
            Map<String, Object> session = traces.get(0);
            assertThat(session).containsEntry("DOMAIN", "SESSION").containsEntry("ENTITY_ID", jan7.getId())
                    .containsEntry("STUDENT_ID", null).containsEntry("SESSION_ID", jan7.getId())
                    .containsEntry("SERIES_ID", s1.getId()).containsEntry("GROUP_ID", group.getId())
                    .containsEntry("PERFORMED_BY", "directrice").containsEntry("REASON_TYPE", "DATA_ENTRY_ERROR")
                    .containsEntry("SUMMARY", "Séance du 07/01/2030 (Math 1ère A) dévalidée : 2 lignes retirées");
            assertThat((String) session.get("OLD_VALUE")).contains("\"finished\":true",
                    "{\"id\":" + amine + ",\"studentId\":" + student.getId() + ",\"student\":\"Amine Belkacem\","
                            + "\"present\":true,\"justified\":false,\"catchUp\":false}",
                    "\"student\":\"Lina Haddad\",\"present\":false");
            assertThat((String) session.get("NEW_VALUE")).contains("\"finished\":false",
                    "\"deactivated\":[" + amine + "," + absence + "]");
            assertThat(traces.get(1)).containsEntry("STUDENT_ID", student.getId());
            assertThat(traces.get(2)).containsEntry("STUDENT_ID", lina.getId());
        }

        @Test
        @DisplayName("une seule ligne, aucune ligne : le dit ; la séance redevient à valider")
        void oneOrNoLine() throws Exception {
            mark(student, jan7, true);
            unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) dévalidée : 1 ligne retirée"));

            SessionEntity jan14 = sessionOn(s1, LocalDate.of(2030, 1, 14));
            validate(jan14);
            String token = token(unvalidate(jan14, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 14/01/2030 (Math 1ère A) dévalidée, sans ligne de présence"))
                    .andExpect(jsonPath("$.preview.amountsUnchanged").value(true)));
            unvalidate(jan14, "confirm", "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(finished(jan14)).isFalse();
            assertThat(count("SELECT COUNT(*) FROM correction_audit WHERE action = 'SESSION_UNVALIDATED'")).isEqualTo(1);
        }

        @Test
        @DisplayName("présence consommée hors période : sa ventilation passe sur une autre séance de la série")
        void consumedPresenceMovesTheVentilation() throws Exception {
            window(LocalDate.of(2030, 1, 10));
            mark(student, jan7, true);
            pay(s1, 2000);
            String token = token(unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[*].type").value(hasItem("VENTILATION_MOVED")))
                    .andExpect(jsonPath("$.preview.series[0].before.cost").value(4000.0))
                    .andExpect(jsonPath("$.preview.series[0].after.cost").value(2000.0)));

            unvalidate(jan7, "confirm", "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            SessionEntity jan14 = sessionOn(s1, LocalDate.of(2030, 1, 14));
            assertThat(count("SELECT COUNT(*) FROM payment_detail WHERE active = TRUE AND session_id = " + jan14.getId()))
                    .isEqualTo(1);
            assertThat(cumulOf(s1)).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("ligne héritée sans étudiant : désactivée avec la feuille, listée en tête par la Trace de la "
                + "séance")
        void lineWithoutStudent() throws Exception {
            Long amine = mark(student, jan7, true);
            Long orphan = attendanceRepository.save(AttendanceEntity.builder().session(jan7).sessionSeries(s1).group(group)
                    .isPresent(true).build()).getId();
            String token = token(unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null));

            unvalidate(jan7, "confirm", "DATA_ENTRY_ERROR", token)
                    .andExpect(jsonPath("$.result.removedLines").value(2));

            assertThat(active(orphan)).isFalse();
            assertThat(active(amine)).isFalse();
            String session = jdbc.queryForObject("SELECT old_value FROM correction_audit WHERE action = "
                    + "'SESSION_UNVALIDATED'", String.class);
            assertThat(session).contains("{\"id\":" + orphan + ",\"studentId\":null,\"student\":null,\"present\":true");
            assertThat(session.indexOf("\"id\":" + orphan)).as("sans nom, avant Amine Belkacem")
                    .isLessThan(session.indexOf("\"id\":" + amine));
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).as("aucune Trace d'élève pour la ligne sans "
                    + "étudiant").isEqualTo(2);
        }

        @Test
        @DisplayName("séance hors série : dévalidée, Trace sans série")
        void sessionWithoutSeries() throws Exception {
            SessionEntity extra = sessionRepository.save(SessionEntity.builder().title("Séance de soutien").group(group)
                    .sessionTimeStart(date(2030, 1, 21)).build());
            validate(extra);
            mark(student, extra, true);
            String token = token(unvalidate(extra, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 21/01/2030 (Math 1ère A) dévalidée : 1 ligne retirée")));

            unvalidate(extra, "confirm", "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(finished(extra)).isFalse();
            assertThat(jdbc.queryForMap("SELECT * FROM correction_audit WHERE action = 'SESSION_UNVALIDATED'"))
                    .containsEntry("SERIES_ID", null).containsEntry("SESSION_ID", extra.getId());
        }
    }

    // ------------------------------------------------------------------
    // Rattrapages
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Rattrapages")
    class Rattrapages {

        private GroupEntity groupB;
        private SessionEntity jan9InB;
        private StudentEntity sami;

        @BeforeEach
        void groupB() {
            groupB = groupRepository.save(GroupEntity.builder().name("Math 1ère B").price(group.getPrice())
                    .schoolYear(year).sessionNumberPerSerie(2).build());
            SessionSeriesEntity januaryB = persistSeries(groupB, "Janvier B", date(2030, 1, 9));
            jan9InB = sessionOn(januaryB, LocalDate.of(2030, 1, 9));
            sami = studentRepository.save(StudentEntity.builder().firstName("Sami").lastName("Kaci").build());
            studentGroupRepository.save(StudentGroupEntity.builder().student(sami).group(groupB)
                    .dateAssigned(at(LocalDate.of(2029, 9, 1))).build());
        }

        @Test
        @DisplayName("rattrapage accueilli : retiré comme en D.2, sa demande annulée, la séance de B de nouveau à "
                + "rattraper ; les deux groupes dans l'Aperçu")
        void hostedCatchUpIsRemovedLikeD2() throws Exception {
            attendanceRepository.save(AttendanceEntity.builder().student(sami).session(jan9InB)
                    .sessionSeries(jan9InB.getSessionSeries()).group(groupB).isPresent(false).isCatchUp(false).build());
            Long catchUp = attendanceRepository.save(AttendanceEntity.builder().student(sami).session(jan7)
                    .sessionSeries(s1).group(group).isPresent(true).isCatchUp(true).missedSession(jan9InB)
                    .catchUpBillingState(CatchUpBillingState.RESOLVED).missedSessionAlreadyPaid(true).build()).getId();
            CatchUpRequestEntity request = catchUpRequestRepository.save(CatchUpRequestEntity.builder().student(sami)
                    .originalSession(jan9InB).originalGroup(groupB).catchUpSession(jan7).catchUpGroup(group)
                    .status(CatchUpStatus.COMPLETED).build());
            String token = token(unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[1].type").value("CATCH_UP_REMOVED"))
                    .andExpect(jsonPath("$.preview.effects[2].description").value(
                            "Séance du 09/01/2030 (Math 1ère B) : de nouveau à rattraper pour Sami Kaci"))
                    .andExpect(jsonPath("$.preview.effects[3].type").value("CATCH_UP_REQUEST_CANCELLED"))
                    .andExpect(jsonPath("$.preview.series[0].seriesName").value("Janvier B"))
                    .andExpect(jsonPath("$.preview.series[0].before.dueSoFar").value(2000.0))
                    .andExpect(jsonPath("$.preview.series[0].after.dueSoFar").value(0.0)));

            unvalidate(jan7, "confirm", "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(active(catchUp)).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM catch_up_request WHERE id = ?", String.class,
                    request.getId())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForList("SELECT action FROM correction_audit ORDER BY id", String.class))
                    .containsExactly("SESSION_UNVALIDATED", "CATCH_UP_REMOVED");
        }

        @Test
        @DisplayName("séance facturée sur place, sans séance manquée : retirée, la série d'accueil perd la séance")
        void hostBilledCatchUpIsRemoved() throws Exception {
            Long catchUp = attendanceRepository.save(AttendanceEntity.builder().student(sami).session(jan7)
                    .sessionSeries(s1).group(group).isPresent(true).isCatchUp(true)
                    .catchUpBillingState(CatchUpBillingState.HOST_BILLED).build()).getId();
            String token = token(unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[1].type").value("CATCH_UP_REMOVED"))
                    .andExpect(jsonPath("$.preview.series.length()").value(1))
                    .andExpect(jsonPath("$.preview.series[0].studentName").value("Sami Kaci"))
                    .andExpect(jsonPath("$.preview.series[0].seriesName").value("Janvier"))
                    .andExpect(jsonPath("$.preview.series[0].before.dueSoFar").value(2000.0))
                    .andExpect(jsonPath("$.preview.series[0].after.dueSoFar").value(0.0)));

            unvalidate(jan7, "confirm", "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(active(catchUp)).isFalse();
            assertThat(jdbc.queryForList("SELECT action FROM correction_audit ORDER BY id", String.class))
                    .containsExactly("SESSION_UNVALIDATED", "CATCH_UP_REMOVED");
        }

        @Test
        @DisplayName("absence rattrapée ailleurs : 409, la feuille garde ses lignes ; le rattrapage d'abord")
        void caughtUpAbsenceBlocks() throws Exception {
            Long absence = mark(student, jan7, false);
            attendanceRepository.save(AttendanceEntity.builder().student(student).session(jan9InB)
                    .sessionSeries(jan9InB.getSessionSeries()).group(groupB).isPresent(true).isCatchUp(true)
                    .missedSession(jan7).build());

            unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("La séance du 07/01/2030 a été rattrapée par Amine Belkacem "
                            + "le 09/01/2030 : retirez d'abord ce rattrapage."));

            assertThat(active(absence)).isTrue();
            assertThat(finished(jan7)).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // Refus
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("séance non validée : 409 ; supprimée : 409 ; inconnue : 404")
        void notValidatedDeletedOrUnknown() throws Exception {
            SessionEntity jan14 = sessionOn(s1, LocalDate.of(2030, 1, 14));
            unvalidate(jan14, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("La séance du 14/01/2030 n'est pas validée : rien à "
                            + "dévalider."));

            jdbc.update("UPDATE session SET active = FALSE WHERE id = ?", jan7.getId());
            unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "La séance du 07/01/2030 a été supprimée : ses présences ne se corrigent plus."));

            send(post("/api/sessions/999999/unvalidate/preview"), "{\"reasonType\":\"DATA_ENTRY_ERROR\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Séance introuvable : 999999"));
        }

        @Test
        @DisplayName("Motif hors de la liste, sans Motif, confirmation sans Aperçu : 400")
        void reasons() throws Exception {
            unvalidate(jan7, "preview", "DOCUMENT_RECEIVED", null).andExpect(status().isBadRequest());
            send(post("/api/sessions/" + jan7.getId() + "/unvalidate/preview"), "{}").andExpect(status().isBadRequest());
            unvalidate(jan7, "confirm", "DATA_ENTRY_ERROR", null).andExpect(status().isBadRequest());
            assertThat(finished(jan7)).isTrue();
        }

        @Test
        @DisplayName("Aperçu périmé : une ligne ajoutée depuis change la liste, la confirmation est refusée")
        void stalePreview() throws Exception {
            mark(student, jan7, true);
            String token = token(unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null));
            mark(lina, jan7, true);

            unvalidate(jan7, "confirm", "DATA_ENTRY_ERROR", token)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("STALE_PREVIEW"))
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) dévalidée : 2 lignes retirées"));
            assertThat(finished(jan7)).isTrue();
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }

        @Test
        @DisplayName("Motif changé depuis l'Aperçu : la confirmation est refusée, rien n'est écrit")
        void reasonChangedSincePreview() throws Exception {
            mark(student, jan7, true);
            String token = token(unvalidate(jan7, "preview", "DATA_ENTRY_ERROR", null));

            send(post("/api/sessions/" + jan7.getId() + "/unvalidate/confirm"), "{\"reasonType\":\"OTHER\","
                    + "\"reasonText\":\"Feuille d'un autre groupe\",\"previewToken\":\"" + token + "\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("STALE_PREVIEW"));

            assertThat(finished(jan7)).isTrue();
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }

        @Test
        @DisplayName("VIEWER : 403 sur l'Aperçu et la confirmation ; les Motifs lui sont lisibles")
        void viewer() throws Exception {
            for (String step : List.of("preview", "confirm")) {
                mockMvc.perform(post("/api/sessions/" + jan7.getId() + "/unvalidate/" + step)
                                .with(user("lecteur").roles("VIEWER")).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reasonType\":\"DATA_ENTRY_ERROR\",\"previewToken\":\"x\"}"))
                        .andExpect(status().isForbidden());
            }
            mockMvc.perform(get("/api/sessions/unvalidation-reasons").with(user("lecteur").roles("VIEWER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0]").value("DATA_ENTRY_ERROR"))
                    .andExpect(jsonPath("$[1]").value("OTHER"));
            assertThat(finished(jan7)).isTrue();
        }

        @Test
        @DisplayName("les anciens raccourcis sans Motif ni Trace n'existent plus : rien n'est désactivé ni effacé")
        void shortcutsAreGone() throws Exception {
            Long amine = mark(student, jan7, true);

            send(patch("/api/sessions/" + jan7.getId() + "/unfinish"), "{}").andExpect(status().is4xxClientError());
            send(patch("/api/attendances/deactivate/" + jan7.getId()), "{}").andExpect(status().is4xxClientError());
            send(delete("/api/attendances/session/" + jan7.getId()), null).andExpect(status().is4xxClientError());
            send(delete("/api/attendances/" + amine), null).andExpect(status().is4xxClientError());

            assertThat(finished(jan7)).isTrue();
            assertThat(active(amine)).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // Année close (10.2)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Année close : ni validation ni dévalidation (10.2)")
    class AnneeClose {

        private SessionEntity october;
        private GroupEntity old;

        @BeforeEach
        void pastYear() {
            SchoolYearEntity past = schoolYearRepository.save(SchoolYearEntity.builder().label("2028-2029")
                    .startDate(date(2028, 9, 1)).endDate(date(2029, 6, 30)).isCurrent(false).build());
            old = groupRepository.save(GroupEntity.builder().name("Math 2028").price(group.getPrice())
                    .schoolYear(past).sessionNumberPerSerie(2).build());
            studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(old)
                    .dateAssigned(at(LocalDate.of(2028, 9, 1))).build());
            SessionSeriesEntity series = persistSeries(old, "Octobre 2028", date(2028, 10, 2));
            october = sessionOn(series, LocalDate.of(2028, 10, 2));
        }

        @Test
        @DisplayName("feuille de présence, présence isolée, validation de la séance : 409, rien d'écrit")
        void validationIsRefused() throws Exception {
            send(post("/api/attendances/bulk"), "[" + line(october) + "]").andExpect(status().isConflict());
            send(post("/api/attendances"), line(october)).andExpect(status().isConflict());
            send(patch("/api/sessions/" + october.getId() + "/finish"), "{}").andExpect(status().isConflict());

            assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();
            assertThat(finished(october)).isFalse();
        }

        @Test
        @DisplayName("dévalidation : 409, la séance reste validée")
        void unvalidationIsRefused() throws Exception {
            validate(october);
            Long presence = attendanceRepository.save(AttendanceEntity.builder().student(student).session(october)
                    .sessionSeries(october.getSessionSeries()).group(old).isPresent(true).isCatchUp(false).build())
                    .getId();

            unvalidate(october, "preview", "DATA_ENTRY_ERROR", null).andExpect(status().isConflict());

            assertThat(finished(october)).isTrue();
            assertThat(active(presence)).isTrue();
        }

        @Test
        @DisplayName("ligne sans séance : 400, son année ne se résout pas ; rien d'écrit")
        void lineWithoutSession() throws Exception {
            String orphan = "{\"studentId\":" + student.getId() + ",\"groupId\":" + group.getId()
                    + ",\"isPresent\":true,\"isJustified\":false,\"isCatchUp\":false,\"description\":\"\"}";

            send(post("/api/attendances/bulk"), "[" + orphan + "]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Présence sans séance : indiquez la séance concernée."));
            send(post("/api/attendances"), orphan).andExpect(status().isBadRequest());

            assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();
        }

        @Test
        @DisplayName("année ouverte : la feuille et la validation passent toujours")
        void currentYearStillValidates() throws Exception {
            SessionEntity jan14 = sessionOn(s1, LocalDate.of(2030, 1, 14));

            send(post("/api/attendances/bulk"), "[" + line(jan14) + "]").andExpect(status().isOk());
            send(patch("/api/sessions/" + jan14.getId() + "/finish"), "{}").andExpect(status().isOk());

            assertThat(finished(jan14)).isTrue();
        }

        /** Présence d'Amine telle que l'écran de validation l'envoie. */
        private String line(SessionEntity session) {
            return "{\"studentId\":" + student.getId() + ",\"sessionId\":" + session.getId()
                    + ",\"groupId\":" + session.getSessionSeries().getGroup().getId() + ",\"sessionSeriesId\":"
                    + session.getSessionSeries().getId()
                    + ",\"isPresent\":true,\"isJustified\":false,\"isCatchUp\":false,\"description\":\"\"}";
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private ResultActions unvalidate(SessionEntity session, String step, String reason, String token) throws Exception {
        return send(post("/api/sessions/" + session.getId() + "/unvalidate/" + step), "{\"reasonType\":\"" + reason + "\""
                + (token == null ? "" : ",\"previewToken\":\"" + token + "\"") + "}");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        MockHttpServletRequestBuilder authenticated = request.with(user("directrice").roles("ADMIN"));
        return mockMvc.perform(json == null ? authenticated
                : authenticated.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(ResultActions preview) throws Exception {
        JsonNode body = objectMapper.readTree(preview.andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        return body.get("previewToken").asText();
    }

    private Long mark(StudentEntity who, SessionEntity session, boolean present) {
        return attendanceRepository.save(AttendanceEntity.builder().student(who).session(session)
                .sessionSeries(session.getSessionSeries()).group(group).isPresent(present).isJustified(false)
                .isCatchUp(false).build()).getId();
    }

    private void window(LocalDate arrival) {
        jdbc.update("UPDATE student_groups SET date_assigned = ? WHERE id = ?",
                Timestamp.valueOf(arrival.atStartOfDay()), enrolmentId);
    }

    private void validate(SessionEntity session) {
        jdbc.update("UPDATE session SET is_finished = TRUE WHERE id = ?", session.getId());
    }

    private boolean finished(SessionEntity session) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_finished FROM session WHERE id = ?", Boolean.class,
                session.getId()));
    }

    private boolean active(Long attendanceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT active FROM attendance WHERE id = ?", Boolean.class,
                attendanceId));
    }

    private SessionEntity sessionOn(SessionSeriesEntity series, LocalDate day) {
        return sessionRepository.findAll().stream()
                .filter(s -> s.getSessionSeries() != null && s.getSessionSeries().getId().equals(series.getId()))
                .filter(s -> LocalDate.ofInstant(s.getSessionTimeStart().toInstant(), ZoneId.systemDefault()).equals(day))
                .findFirst().orElseThrow();
    }

    private static java.util.Date at(LocalDate day) {
        return java.util.Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
