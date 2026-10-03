package com.school.management.controller.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.persistance.AttendanceEntity;
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
import com.school.management.service.payment.PaymentCostResolver;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corriger une présence sur une Séance validée, de bout en bout (spec admin-corrections, D.1 ;
 * exigences 8.1 à 8.6).
 *
 * <p>Socle : Amine Belkacem, inscrit au groupe « Math 1ère A » depuis le 01/09/2029, séances à
 * 2 000 DA ; janvier (07/01 et 14/01) validé. Les écritures sont relues en SQL : c'est ce qui est
 * stocké qui compte.</p>
 */
@AutoConfigureMockMvc
@DisplayName("POST /api/attendances/{id}/correct|remove et /api/sessions/{id}/attendances/add")
class AttendanceCorrectionEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentCostResolver costResolver;
    @Autowired private CatchUpRequestRepository catchUpRequestRepository;

    private Long enrolmentId;
    private SessionEntity jan7;
    private SessionEntity jan14;
    private SessionEntity feb4;

    @BeforeEach
    void januaryValidated() {
        enrolmentId = studentGroupRepository.findByGroupIdAndStudentId(group.getId(), student.getId()).get(0).getId();
        window(LocalDate.of(2029, 9, 1), null);
        jan7 = sessionOn(s1, LocalDate.of(2030, 1, 7));
        jan14 = sessionOn(s1, LocalDate.of(2030, 1, 14));
        feb4 = sessionOn(s2, LocalDate.of(2030, 2, 4));
        validate(jan7);
        validate(jan14);
    }

    /** Les demandes de rattrapage ne sont pas vidées par le socle : elles bloqueraient sa purge. */
    @AfterEach
    void removeCatchUpRequests() {
        catchUpRequestRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Présent ↔ absent
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Présent → absent")
    class PresentVersAbsent {

        @Test
        @DisplayName("Aperçu : le dû à ce jour de janvier passe de 4 000 à 2 000 DA ; rien n'est écrit")
        void previewShowsTheDueAmountDrop() throws Exception {
            Long presence = mark(jan7, true, false);
            mark(jan14, true, false);

            change(presence, "preview", false, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value(nullValue()))
                    .andExpect(jsonPath("$.preview.effects[0].type").value("ATTENDANCE_CHANGED"))
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : Amine Belkacem présent → absent"))
                    .andExpect(jsonPath("$.preview.effects[0].sessionId").value(nullValue()))
                    .andExpect(jsonPath("$.preview.series[0].seriesName").value("Janvier"))
                    .andExpect(jsonPath("$.preview.series[0].before.dueSoFar").value(4000.0))
                    .andExpect(jsonPath("$.preview.series[0].after.dueSoFar").value(2000.0))
                    .andExpect(jsonPath("$.preview.series[0].after.cost").value(4000.0));

            assertThat(row(presence)).containsEntry("STATUS", true).containsEntry("ACTIVE", true);
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }

        @Test
        @DisplayName("absence justifiée dans la même action (8.2) : une trace, signée par l'administratrice")
        void absenceJustifiedInTheSameAction() throws Exception {
            Long presence = mark(jan7, true, false);
            String token = token(change(presence, "preview", false, true, "DOCUMENT_RECEIVED", null));

            change(presence, "confirm", false, true, "DOCUMENT_RECEIVED", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.attendanceId").value(presence))
                    .andExpect(jsonPath("$.result.present").value(false))
                    .andExpect(jsonPath("$.result.justified").value(true))
                    .andExpect(jsonPath("$.result.active").value(true));

            assertThat(row(presence)).containsEntry("STATUS", false).containsEntry("IS_JUSTIFIED", true);
            Map<String, Object> trace = onlyTrace();
            assertThat(trace).containsEntry("ACTION", "PRESENCE_CHANGED").containsEntry("DOMAIN", "ATTENDANCE")
                    .containsEntry("REASON_TYPE", "DOCUMENT_RECEIVED").containsEntry("PERFORMED_BY", "directrice")
                    .containsEntry("ENTITY_ID", presence).containsEntry("SESSION_ID", jan7.getId())
                    .containsEntry("SERIES_ID", s1.getId()).containsEntry("GROUP_ID", group.getId())
                    .containsEntry("SUMMARY", "Séance du 07/01/2030 (Math 1ère A) : Amine Belkacem présent → absent "
                            + "(justifié)");
            assertThat((String) trace.get("OLD_VALUE")).contains("\"present\":true", "\"justified\":false");
            assertThat((String) trace.get("NEW_VALUE")).contains("\"present\":false", "\"justified\":true");
        }

        @Test
        @DisplayName("justification omise à l'Aperçu, « false » à la confirmation : même correction, jeton valable")
        void omittedJustificationMeansNotJustified() throws Exception {
            Long presence = mark(jan7, true, false);
            String token = token(change(presence, "preview", false, null, "DATA_ENTRY_ERROR", null));

            change(presence, "confirm", false, false, "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(row(presence)).containsEntry("STATUS", false).containsEntry("IS_JUSTIFIED", false);
        }

        @Test
        @DisplayName("hors de la période de l'étudiant : 409 nommant la ligne, rien d'écrit (8.5)")
        void absenceOutsideTheWindowIsRefused() throws Exception {
            window(LocalDate.of(2030, 1, 10), null);
            Long consumed = mark(jan7, true, false);

            change(consumed, "preview", false, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ABSENCE_OUTSIDE_WINDOW"))
                    .andExpect(jsonPath("$.rejected[0].studentId").value(student.getId()))
                    .andExpect(jsonPath("$.rejected[0].message").value(containsString("Amine Belkacem")));

            assertThat(row(consumed)).containsEntry("STATUS", true);
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }
    }

    @Nested
    @DisplayName("Absent → présent")
    class AbsentVersPresent {

        @Test
        @DisplayName("justification effacée, et inscrite dans la trace (8.3) ; le dû à ce jour monte")
        void justificationIsClearedAndTraced() throws Exception {
            Long absence = mark(jan7, false, true);
            String token = token(change(absence, "preview", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : Amine Belkacem absent (justifié) → présent"))
                    .andExpect(jsonPath("$.preview.series[0].before.dueSoFar").value(0.0))
                    .andExpect(jsonPath("$.preview.series[0].after.dueSoFar").value(2000.0)));

            change(absence, "confirm", true, null, "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(row(absence)).containsEntry("STATUS", true).containsEntry("IS_JUSTIFIED", false);
            Map<String, Object> trace = onlyTrace();
            assertThat((String) trace.get("OLD_VALUE")).contains("\"present\":false", "\"justified\":true");
            assertThat((String) trace.get("NEW_VALUE")).contains("\"present\":true", "\"justified\":false");
        }

        @Test
        @DisplayName("absence non justifiée → présent : écrit « absent → présent »")
        void plainAbsence() throws Exception {
            Long absence = mark(jan7, false, false);

            change(absence, "preview", true, false, "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : Amine Belkacem absent → présent"));
        }

        @Test
        @DisplayName("présence justifiée demandée, état absent, motif hors liste : 400")
        void malformedRequests() throws Exception {
            Long absence = mark(jan7, false, false);

            change(absence, "preview", true, true, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Une présence ne se justifie pas")));
            change(absence, "preview", null, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Présent ou absent : à préciser."));
            change(absence, "preview", true, null, "WRONG_AMOUNT", null).andExpect(status().isBadRequest());
            change(absence, "preview", true, null, null, null).andExpect(status().isBadRequest());
            change(absence, "confirm", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Confirmation sans aperçu")));
            send(post("/api/attendances/" + absence + "/correct/preview"), null).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("même état : 400 ; pour une absence, la justification se modifie par « Justifier »")
        void unchangedStateIsRefused() throws Exception {
            Long presence = mark(jan7, true, false);
            Long absence = mark(jan14, false, false);

            change(presence, "preview", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Amine Belkacem est déjà noté présent le 07/01/2030 : rien à corriger."));
            change(absence, "preview", false, true, "DOCUMENT_RECEIVED", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Amine Belkacem est déjà noté absent le 14/01/2030 : sa justification se modifie "
                                    + "par « Justifier »."));
        }
    }

    // ------------------------------------------------------------------
    // Ajout
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Ajout d'une ligne manquante")
    class Ajout {

        @Test
        @DisplayName("présence manquante sur une séance validée : ajoutée, ordinaire, tracée ; le dû à ce jour monte")
        void missingPresenceIsAdded() throws Exception {
            String token = token(add(jan14, student, "preview", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].type").value("ATTENDANCE_RECORDED"))
                    .andExpect(jsonPath("$.preview.effects[0].sessionId").value(nullValue()))
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 14/01/2030 (Math 1ère A) : présence de Amine Belkacem ajoutée"))
                    .andExpect(jsonPath("$.preview.series[0].after.dueSoFar").value(2000.0)));
            assertThat(count("SELECT COUNT(*) FROM attendance")).as("Aperçu annulé").isZero();

            add(jan14, student, "confirm", true, null, "DATA_ENTRY_ERROR", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.present").value(true))
                    .andExpect(jsonPath("$.result.sessionId").value(jan14.getId()));

            Map<String, Object> added = jdbc.queryForMap("SELECT * FROM attendance WHERE session_id = ?", jan14.getId());
            assertThat(added).containsEntry("STATUS", true).containsEntry("IS_CATCH_UP", false)
                    .containsEntry("IS_JUSTIFIED", false).containsEntry("ACTIVE", true)
                    .containsEntry("GROUP_ID", group.getId()).containsEntry("SESSION_SERIES_ID", s1.getId());
            Map<String, Object> trace = onlyTrace();
            assertThat(trace).containsEntry("ACTION", "ATTENDANCE_ADDED").containsEntry("OLD_VALUE", null);
            assertThat((String) trace.get("NEW_VALUE")).contains("\"present\":true", "\"active\":true");
        }

        @Test
        @DisplayName("absence justifiée ajoutée")
        void justifiedAbsenceIsAdded() throws Exception {
            String token = token(add(jan14, student, "preview", false, true, "DOCUMENT_RECEIVED", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 14/01/2030 (Math 1ère A) : absence justifiée de Amine Belkacem ajoutée"))
                    .andExpect(jsonPath("$.preview.amountsUnchanged").value(true)));

            add(jan14, student, "confirm", false, true, "DOCUMENT_RECEIVED", token).andExpect(status().isOk());

            assertThat(jdbc.queryForMap("SELECT status, is_justified FROM attendance WHERE session_id = ?", jan14.getId()))
                    .containsEntry("STATUS", false).containsEntry("IS_JUSTIFIED", true);
        }

        @Test
        @DisplayName("présence ajoutée après le départ : facturée comme séance consommée")
        void presenceAfterDepartureIsBilledAsConsumed() throws Exception {
            window(LocalDate.of(2029, 9, 1), LocalDate.of(2030, 1, 10));
            validate(feb4);
            assertThat(costOf(s2)).isEqualByComparingTo("0.00");
            String token = token(add(feb4, student, "preview", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.series[0].seriesName").value("Février"))
                    .andExpect(jsonPath("$.preview.series[0].before.cost").value(0.0))
                    .andExpect(jsonPath("$.preview.series[0].after.cost").value(2000.0)));

            add(feb4, student, "confirm", true, null, "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(costOf(s2)).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("absence ajoutée hors de la période : 409 nommant la ligne, rien d'écrit (8.5)")
        void absenceOutsideTheWindowIsRefused() throws Exception {
            window(LocalDate.of(2030, 1, 10), null);

            add(jan7, student, "preview", false, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ABSENCE_OUTSIDE_WINDOW"));
            assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();
        }

        @Test
        @DisplayName("ligne déjà présente : 409, à corriger plutôt qu'à doubler")
        void duplicateIsRefused() throws Exception {
            mark(jan14, false, false);

            add(jan14, student, "preview", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "Amine Belkacem a déjà une ligne le 14/01/2030 : corrigez-la plutôt que d'en ajouter une."));
        }

        @Test
        @DisplayName("élève sans inscription au groupe : 409, renvoyé vers la demande de rattrapage")
        void notEnrolledIsRefused() throws Exception {
            StudentEntity lina = studentRepository.save(StudentEntity.builder().firstName("Lina").lastName("Haddad").build());

            add(jan14, lina, "preview", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Lina Haddad n'est pas inscrit au groupe « Math 1ère A » : "
                            + "un rattrapage s'enregistre par une demande de rattrapage."));
        }

        @Test
        @DisplayName("séance ou étudiant inconnus : 404 ; étudiant absent de la requête : 400")
        void unknownSessionOrStudent() throws Exception {
            send(post("/api/sessions/999999/attendances/add/preview"),
                    "{\"studentId\":" + student.getId() + ",\"present\":true,\"reasonType\":\"DATA_ENTRY_ERROR\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Séance introuvable : 999999"));
            send(post("/api/sessions/" + jan14.getId() + "/attendances/add/preview"),
                    "{\"studentId\":999999,\"present\":true,\"reasonType\":\"DATA_ENTRY_ERROR\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Étudiant introuvable : 999999"));
            send(post("/api/sessions/" + jan14.getId() + "/attendances/add/preview"),
                    "{\"present\":true,\"reasonType\":\"DATA_ENTRY_ERROR\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Étudiant à préciser."));
        }

        @Test
        @DisplayName("séance sans groupe ni série : 409, aucune inscription ne peut la concerner")
        void sessionWithoutGroupIsRefused() throws Exception {
            SessionEntity orphan = sessionRepository.save(SessionEntity.builder().title("Séance isolée").build());
            validate(orphan);

            add(orphan, student, "preview", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("La séance du (non datée) n'appartient à aucun groupe : "
                            + "aucune inscription ne peut la concerner."));
        }

        @Test
        @DisplayName("séance non validée : 409, sa feuille se modifie directement")
        void unvalidatedSessionIsRefused() throws Exception {
            add(feb4, student, "preview", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("La séance du 04/02/2030 n'est pas validée : sa feuille de "
                            + "présence se modifie directement, sans correction."));
        }
    }

    // ------------------------------------------------------------------
    // Retrait
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Retrait d'une ligne")
    class Retrait {

        @Test
        @DisplayName("par désactivation (8.4) : la ligne reste en base ; le dû à ce jour baisse ; une trace")
        void presenceIsDeactivated() throws Exception {
            Long presence = mark(jan7, true, false);
            String token = token(remove(presence, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].type").value("PRESENCE_REMOVED"))
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : présence de Amine Belkacem retirée"))
                    .andExpect(jsonPath("$.preview.series[0].after.dueSoFar").value(0.0)));

            remove(presence, "confirm", "DATA_ENTRY_ERROR", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.active").value(false))
                    .andExpect(jsonPath("$.result.present").value(true));

            assertThat(row(presence)).containsEntry("ACTIVE", false).containsEntry("STATUS", true);
            assertThat(count("SELECT COUNT(*) FROM attendance")).isEqualTo(1);
            Map<String, Object> trace = onlyTrace();
            assertThat(trace).containsEntry("ACTION", "ATTENDANCE_REMOVED");
            assertThat((String) trace.get("OLD_VALUE")).contains("\"active\":true");
            assertThat((String) trace.get("NEW_VALUE")).contains("\"active\":false");
        }

        @Test
        @DisplayName("présence consommée hors période retirée : séance non facturable, ventilation déplacée")
        void consumedPresenceRemovedMovesTheVentilation() throws Exception {
            window(LocalDate.of(2030, 1, 10), null);
            Long consumed = mark(jan7, true, false);
            pay(s1, 2000);
            assertThat(activeLinesOn(jan7)).isEqualTo(1);
            String token = token(remove(consumed, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.series[0].before.cost").value(4000.0))
                    .andExpect(jsonPath("$.preview.series[0].after.cost").value(2000.0))
                    .andExpect(jsonPath("$.preview.effects[*].type").value(hasItem("VENTILATION_MOVED"))));

            remove(consumed, "confirm", "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(activeLinesOn(jan7)).isZero();
            assertThat(activeLinesOn(jan14)).isEqualTo(1);
            assertThat(cumulOf(s1)).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("absence justifiée retirée : effet « absence retirée », aucun montant ne change")
        void absenceRemovedChangesNoAmount() throws Exception {
            Long absence = mark(jan7, false, true);

            remove(absence, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preview.effects[0].type").value("ABSENCE_REMOVED"))
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : absence justifiée de Amine Belkacem retirée"))
                    .andExpect(jsonPath("$.preview.amountsUnchanged").value(true));
        }

        @Test
        @DisplayName("absence non justifiée retirée")
        void plainAbsenceRemoved() throws Exception {
            Long absence = mark(jan7, false, false);

            remove(absence, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : absence de Amine Belkacem retirée"));
        }
    }

    // ------------------------------------------------------------------
    // Refus communs
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("ligne inconnue : 404 ; déjà retirée : 409")
        void unknownOrAlreadyRemoved() throws Exception {
            change(999_999L, "preview", false, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Présence introuvable : 999999"));
            Long removed = mark(jan7, true, false);
            jdbc.update("UPDATE attendance SET active = FALSE WHERE id = ?", removed);

            remove(removed, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Cette ligne a déjà été retirée : rien à corriger."));
        }

        @Test
        @DisplayName("présence de rattrapage : 409, renvoyée vers sa correction dédiée")
        void catchUpPresenceHasItsOwnCorrection() throws Exception {
            Long catchUp = attendanceRepository.save(AttendanceEntity.builder().student(student).session(jan7)
                    .sessionSeries(s1).group(group).isPresent(true).isCatchUp(true).build()).getId();

            remove(catchUp, "preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("Présence de rattrapage du 07/01/2030")));
        }

        @Test
        @DisplayName("séance non validée, ou validée puis supprimée : 409")
        void unvalidatedOrDeletedSession() throws Exception {
            Long onFebruary = mark(feb4, true, false);
            change(onFebruary, "preview", false, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("n'est pas validée")));

            Long onJanuary = mark(jan7, true, false);
            jdbc.update("UPDATE session SET active = FALSE WHERE id = ?", jan7.getId());
            change(onJanuary, "preview", false, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "La séance du 07/01/2030 a été supprimée : ses présences ne se corrigent plus."));
        }

        @Test
        @DisplayName("année close : 409, rien d'écrit (8.6)")
        void closedYearIsRefused() throws Exception {
            SchoolYearEntity past = schoolYearRepository.save(SchoolYearEntity.builder().label("2028-2029")
                    .startDate(date(2028, 9, 1)).endDate(date(2029, 6, 30)).isCurrent(false).build());
            GroupEntity old = groupRepository.save(GroupEntity.builder().name("Math 2028")
                    .price(group.getPrice()).schoolYear(past).sessionNumberPerSerie(2).build());
            studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(old).build());
            SessionSeriesEntity series = persistSeries(old, "Octobre 2028", date(2028, 10, 2));
            SessionEntity session = sessionOn(series, LocalDate.of(2028, 10, 2));
            validate(session);
            Long presence = attendanceRepository.save(AttendanceEntity.builder().student(student).session(session)
                    .sessionSeries(series).group(old).isPresent(true).isCatchUp(false).build()).getId();

            change(presence, "preview", false, null, "DATA_ENTRY_ERROR", null).andExpect(status().isConflict());
            add(session, student, "preview", true, null, "DATA_ENTRY_ERROR", null).andExpect(status().isConflict());

            assertThat(row(presence)).containsEntry("STATUS", true);
        }

        @Test
        @DisplayName("ligne sans étudiant ou sans séance : 409, elle ne se corrige pas ici")
        void incompleteLines() throws Exception {
            Long withoutStudent = attendanceRepository.save(AttendanceEntity.builder().session(jan7).sessionSeries(s1)
                    .group(group).isPresent(true).build()).getId();
            Long withoutSession = attendanceRepository.save(AttendanceEntity.builder().student(student).sessionSeries(s1)
                    .group(group).isPresent(true).build()).getId();

            for (Long incomplete : List.of(withoutStudent, withoutSession)) {
                remove(incomplete, "preview", "DATA_ENTRY_ERROR", null)
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.message").value(containsString("sans étudiant ou sans séance")));
            }
        }

        @Test
        @DisplayName("séance hors série : corrigée, trace sans série")
        void sessionWithoutSeries() throws Exception {
            SessionEntity extra = sessionRepository.save(SessionEntity.builder().title("Séance de soutien").group(group)
                    .sessionTimeStart(date(2030, 1, 21)).build());
            validate(extra);
            Long presence = attendanceRepository.save(AttendanceEntity.builder().student(student).session(extra)
                    .group(group).isPresent(true).isCatchUp(false).build()).getId();
            String token = token(remove(presence, "preview", "DATA_ENTRY_ERROR", null));

            remove(presence, "confirm", "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(onlyTrace()).containsEntry("SERIES_ID", null).containsEntry("SESSION_ID", extra.getId());
        }

        @Test
        @DisplayName("Aperçu périmé : une présence apparue depuis change le dû, la confirmation est refusée")
        void stalePreview() throws Exception {
            Long presence = mark(jan7, true, false);
            String token = token(change(presence, "preview", false, null, "DATA_ENTRY_ERROR", null));
            mark(jan14, true, false);

            change(presence, "confirm", false, null, "DATA_ENTRY_ERROR", token)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("STALE_PREVIEW"))
                    .andExpect(jsonPath("$.preview.series[0].after.dueSoFar").value(2000.0));

            assertThat(row(presence)).containsEntry("STATUS", true);
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }
    }

    // ------------------------------------------------------------------
    // Rattrapage lié
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Séance rattrapée ou en voie de l'être")
    class Rattrapage {

        private GroupEntity groupB;
        private SessionEntity jan9InB;

        @BeforeEach
        void anotherGroup() {
            groupB = groupRepository.save(GroupEntity.builder().name("Math 1ère B").price(group.getPrice())
                    .schoolYear(year).sessionNumberPerSerie(2).build());
            SessionSeriesEntity seriesB = persistSeries(groupB, "Janvier B", date(2030, 1, 9));
            jan9InB = sessionOn(seriesB, LocalDate.of(2030, 1, 9));
        }

        private void caughtUpIn(SessionEntity host, SessionEntity missed) {
            attendanceRepository.save(AttendanceEntity.builder().student(student).session(host)
                    .sessionSeries(host.getSessionSeries()).group(groupB).isPresent(true).isCatchUp(true)
                    .missedSession(missed).build());
        }

        @Test
        @DisplayName("absence rattrapée : ne devient pas présente, ne se retire pas ; le rattrapage d'abord")
        void caughtUpAbsenceIsLocked() throws Exception {
            Long absence = mark(jan7, false, false);
            caughtUpIn(jan9InB, jan7);

            change(absence, "preview", true, null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("La séance du 07/01/2030 a été rattrapée par Amine Belkacem "
                            + "le 09/01/2030 : retirez d'abord ce rattrapage."));
            remove(absence, "preview", "DATA_ENTRY_ERROR", null).andExpect(status().isConflict());
            // Une autre séance n'est pas concernée par ce rattrapage.
            add(jan14, student, "preview", true, null, "DATA_ENTRY_ERROR", null).andExpect(status().isOk());
        }

        @Test
        @DisplayName("présence ajoutée sur une séance rattrapée sans ligne : 409")
        void presenceAddedOnACaughtUpSession() throws Exception {
            caughtUpIn(jan9InB, jan14);

            add(jan14, student, "preview", true, null, "DATA_ENTRY_ERROR", null).andExpect(status().isConflict());
            // Une absence reste cohérente avec le rattrapage qui la compense.
            add(jan14, student, "preview", false, null, "DATA_ENTRY_ERROR", null).andExpect(status().isOk());
        }

        @Test
        @DisplayName("rattrapage sans séance manquée (facturé sur place) : ne bloque rien")
        void catchUpWithoutMissedSessionBlocksNothing() throws Exception {
            Long absence = mark(jan7, false, false);
            caughtUpIn(jan9InB, null);

            change(absence, "preview", true, null, "DATA_ENTRY_ERROR", null).andExpect(status().isOk());
        }

        @Test
        @DisplayName("demande de rattrapage en attente ou planifiée : 409 ; annulée ou effectuée : rien ne bloque")
        void openRequestsLockTheAbsence() throws Exception {
            Long absence = mark(jan7, false, false);
            CatchUpRequestEntity request = catchUpRequestRepository.save(CatchUpRequestEntity.builder().student(student)
                    .originalSession(jan7).originalGroup(group).status(CatchUpStatus.PENDING).build());

            for (CatchUpStatus open : List.of(CatchUpStatus.PENDING, CatchUpStatus.SCHEDULED)) {
                request.setStatus(open);
                catchUpRequestRepository.save(request);
                change(absence, "preview", true, null, "DATA_ENTRY_ERROR", null)
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.message").value("Une demande de rattrapage de la séance du 07/01/2030 "
                                + "est en cours pour Amine Belkacem : annulez-la d'abord."));
            }
            for (CatchUpStatus closed : List.of(CatchUpStatus.CANCELLED, CatchUpStatus.COMPLETED)) {
                request.setStatus(closed);
                catchUpRequestRepository.save(request);
                change(absence, "preview", true, null, "DATA_ENTRY_ERROR", null).andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("demande en cours pour une autre séance, ou sans séance d'origine : ne bloque rien")
        void requestForAnotherSessionBlocksNothing() throws Exception {
            Long absence = mark(jan7, false, false);
            catchUpRequestRepository.save(CatchUpRequestEntity.builder().student(student).originalSession(jan14)
                    .originalGroup(group).status(CatchUpStatus.SCHEDULED).build());
            catchUpRequestRepository.save(CatchUpRequestEntity.builder().student(student)
                    .originalGroup(group).status(CatchUpStatus.PENDING).build());

            change(absence, "preview", true, null, "DATA_ENTRY_ERROR", null).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------
    // Rôles et Motifs
    // ------------------------------------------------------------------

    @Test
    @DisplayName("VIEWER : 403 sur chaque Aperçu et confirmation, rien d'écrit ; les Motifs lui sont lisibles")
    void viewerCannotCorrect() throws Exception {
        Long presence = mark(jan7, true, false);
        List<String> urls = new ArrayList<>();
        for (String step : List.of("preview", "confirm")) {
            urls.add("/api/attendances/" + presence + "/correct/" + step);
            urls.add("/api/attendances/" + presence + "/remove/" + step);
            urls.add("/api/sessions/" + jan14.getId() + "/attendances/add/" + step);
        }
        for (String url : urls) {
            mockMvc.perform(post(url).with(user("lecteur").roles("VIEWER")).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"studentId\":" + student.getId() + ",\"present\":false,"
                                    + "\"reasonType\":\"DATA_ENTRY_ERROR\",\"previewToken\":\"x\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/attendances/correction-reasons").with(user("lecteur").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("DATA_ENTRY_ERROR"))
                .andExpect(jsonPath("$[1]").value("DOCUMENT_RECEIVED"))
                .andExpect(jsonPath("$[2]").value("OTHER"))
                .andExpect(jsonPath("$.length()").value(3));

        assertThat(row(presence)).containsEntry("STATUS", true).containsEntry("ACTIVE", true);
        assertThat(count("SELECT COUNT(*) FROM attendance")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private ResultActions change(Long attendanceId, String step, Boolean present, Boolean justified, String reason,
                                 String token) throws Exception {
        return send(post("/api/attendances/" + attendanceId + "/correct/" + step),
                body(null, present, justified, reason, token));
    }

    private ResultActions remove(Long attendanceId, String step, String reason, String token) throws Exception {
        return send(post("/api/attendances/" + attendanceId + "/remove/" + step), body(null, null, null, reason, token));
    }

    private ResultActions add(SessionEntity session, StudentEntity who, String step, Boolean present, Boolean justified,
                              String reason, String token) throws Exception {
        return send(post("/api/sessions/" + session.getId() + "/attendances/add/" + step),
                body(who.getId(), present, justified, reason, token));
    }

    private static String body(Long studentId, Boolean present, Boolean justified, String reason, String token) {
        List<String> fields = new ArrayList<>();
        if (studentId != null) {
            fields.add("\"studentId\":" + studentId);
        }
        if (present != null) {
            fields.add("\"present\":" + present);
        }
        if (justified != null) {
            fields.add("\"justified\":" + justified);
        }
        if (reason != null) {
            fields.add("\"reasonType\":\"" + reason + "\"");
        }
        if (token != null) {
            fields.add("\"previewToken\":\"" + token + "\"");
        }
        return "{" + String.join(",", fields) + "}";
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

    private void window(LocalDate arrival, LocalDate departure) {
        jdbc.update("UPDATE student_groups SET date_assigned = ?, date_left = ?, active = ? WHERE id = ?",
                Timestamp.valueOf(arrival.atStartOfDay()),
                departure == null ? null : Timestamp.valueOf(departure.atStartOfDay()), departure == null, enrolmentId);
    }

    private Long mark(SessionEntity session, boolean present, boolean justified) {
        return attendanceRepository.save(AttendanceEntity.builder().student(student).session(session)
                .sessionSeries(session.getSessionSeries()).group(group).isPresent(present).isJustified(justified)
                .isCatchUp(false).build()).getId();
    }

    private Map<String, Object> row(Long attendanceId) {
        return jdbc.queryForMap("SELECT status, is_justified, active FROM attendance WHERE id = ?", attendanceId);
    }

    /** La seule trace écrite, colonnes relues en SQL. */
    private Map<String, Object> onlyTrace() {
        List<Map<String, Object>> traces = jdbc.queryForList("SELECT * FROM correction_audit");
        assertThat(traces).hasSize(1);
        return traces.get(0);
    }

    private void validate(SessionEntity session) {
        jdbc.update("UPDATE session SET is_finished = TRUE WHERE id = ?", session.getId());
    }

    private long activeLinesOn(SessionEntity session) {
        return count("SELECT COUNT(*) FROM payment_detail WHERE active = TRUE AND session_id = " + session.getId());
    }

    private BigDecimal costOf(SessionSeriesEntity series) {
        return costResolver.resolve(student.getId(), series.getId()).monthTotalCost();
    }

    private SessionEntity sessionOn(SessionSeriesEntity series, LocalDate day) {
        return sessionRepository.findAll().stream()
                .filter(s -> s.getSessionSeries() != null && s.getSessionSeries().getId().equals(series.getId()))
                .filter(s -> LocalDate.ofInstant(s.getSessionTimeStart().toInstant(), ZoneId.systemDefault()).equals(day))
                .findFirst().orElseThrow();
    }
}
