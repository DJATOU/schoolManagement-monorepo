package com.school.management.controller.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import com.school.management.service.payment.PaymentCostResolver;
import com.school.management.service.payment.PaymentLineStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corriger les dates d'une inscription, de bout en bout (spec admin-corrections, C.6 ; exigences
 * 5.5 à 5.9, 6.1, 6.3 à 6.5, D6).
 *
 * <p>Amine Belkacem dans « Math 1ère A », 2 000 DA la séance. « Janvier » : 07/01 et 14/01/2030 ;
 * « Février » : 04/02 et 11/02/2030. Sauf mention, il est arrivé le 01/09/2029 et n'est pas parti.
 * Les états sont relus en SQL : c'est ce qui reste en base qui compte.</p>
 */
@AutoConfigureMockMvc
@DisplayName("POST /api/enrolments/{id}/arrival|departure|reopen/preview|confirm")
class EnrolmentCorrectionEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    private static final String AMINE_ARRIVES = "2029-09-01";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentCostResolver costResolver;

    private Long enrolmentId;
    private SessionEntity jan7;
    private SessionEntity jan14;
    private SessionEntity feb4;

    @BeforeEach
    void amineSinceSeptember() {
        enrolmentId = studentGroupRepository.findByGroupIdAndStudentId(group.getId(), student.getId()).get(0).getId();
        window(LocalDate.parse(AMINE_ARRIVES), null);
        jan7 = sessionOn(s1, LocalDate.of(2030, 1, 7));
        jan14 = sessionOn(s1, LocalDate.of(2030, 1, 14));
        feb4 = sessionOn(s2, LocalDate.of(2030, 2, 4));
    }

    // ------------------------------------------------------------------
    // Arrivée
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Arrivée reculée")
    class ArriveePlusTard {

        @Test
        @DisplayName("Aperçu : l'absence du 07/01 sortira de la période et sera retirée ; janvier passe à "
                + "2 000 DA ; rien n'est écrit")
        void previewListsTheAbsenceToRemove() throws Exception {
            Long absence = mark(jan7, false);

            arrival("preview", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value(nullValue()))
                    .andExpect(jsonPath("$.preview.effects[0].type").value("ENROLMENT_WINDOW_CHANGED"))
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Arrivée de Amine Belkacem dans « Math 1ère A » : 01/09/2029 → 10/01/2030"))
                    .andExpect(jsonPath("$.preview.effects[0].sessionId").value(nullValue()))
                    .andExpect(jsonPath("$.preview.effects[1].type").value("ABSENCE_REMOVED"))
                    .andExpect(jsonPath("$.preview.effects[1].description").value(
                            "Absence de Amine Belkacem le 07/01/2030 (« Janvier ») retirée : hors de la nouvelle période"))
                    // Rien à noter sur une absence retirée : l'écran n'y propose aucun choix.
                    .andExpect(jsonPath("$.preview.effects[1].sessionId").value(nullValue()))
                    .andExpect(jsonPath("$.preview.series[0].seriesName").value("Janvier"))
                    .andExpect(jsonPath("$.preview.series[0].before.cost").value(4000.0))
                    .andExpect(jsonPath("$.preview.series[0].after.cost").value(2000.0));

            assertThat(active(absence)).isTrue();
            assertThat(arrivalInDb()).isEqualTo(LocalDate.parse(AMINE_ARRIVES));
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }

        @Test
        @DisplayName("Confirmation : arrivée et absence écrites ensemble, une trace pour chacune")
        void confirmWritesArrivalAndRemovesAbsence() throws Exception {
            Long absence = mark(jan7, false);
            String token = token(arrival("preview", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", null));

            arrival("confirm", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.arrival").value("2030-01-10"))
                    .andExpect(jsonPath("$.result.active").value(true));

            assertThat(arrivalInDb()).isEqualTo(LocalDate.of(2030, 1, 10));
            assertThat(active(absence)).isFalse();
            assertThat(jdbc.queryForList("SELECT action FROM correction_audit ORDER BY id", String.class))
                    .containsExactly("ARRIVAL_DATE_CORRECTED", "ATTENDANCE_REMOVED");
            assertThat(count("SELECT COUNT(*) FROM attendance WHERE active = TRUE AND status = FALSE")).isZero();
        }

        @Test
        @DisplayName("présence du 07/01 hors de la nouvelle période : maintenue, facturée comme séance consommée")
        void ordinaryPresenceIsKept() throws Exception {
            Long presence = mark(jan7, true);
            String token = token(arrival("preview", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", null));

            arrival("confirm", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preview.effects[1].type").value("PRESENCE_KEPT"))
                    .andExpect(jsonPath("$.preview.effects[1].description").value(containsString(
                            "maintenue, facturée comme séance consommée")));

            assertThat(active(presence)).isTrue();
            assertThat(costOf(s1)).isEqualByComparingTo("4000.00");
        }

        @Test
        @DisplayName("ventilation du 07/01 déplacée sur le 14/01, sans changer le versé (5.9, D6)")
        void ventilationMovesToABillableSession() throws Exception {
            pay(s1, 2000);
            assertThat(activeLinesOn(jan7)).isEqualTo(1);
            String token = token(arrival("preview", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", null));

            arrival("confirm", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preview.effects[*].type").value(hasItem("VENTILATION_MOVED")))
                    .andExpect(jsonPath("$.preview.effects[*].type").value(not(hasItem("EXCESS_LEFT"))))
                    .andExpect(jsonPath("$.preview.effects[1].description").value(containsString(
                            "2 000,00 DA ventilés sur la séance du 07/01/2030 (« Janvier »), devenue non facturable, "
                                    + "passent sur 14/01/2030 (2 000,00 DA)")));

            assertThat(activeLinesOn(jan7)).isZero();
            assertThat(activeLinesOn(jan14)).isEqualTo(1);
            assertThat(cumulOf(s1)).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("plus de place sur la série : reliquat non ventilé et trop-perçu annoncés, jamais reportés")
        void excessIsAnnouncedNotCarried() throws Exception {
            pay(s1, 4000);
            String token = token(arrival("preview", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", null));

            arrival("confirm", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preview.effects[*].description").value(hasItem(containsString(
                            "ne trouvent aucune autre séance"))))
                    .andExpect(jsonPath("$.preview.effects[*].description").value(hasItem(
                            "Reçu RECU-" + LocalDate.now().getYear() + "-0001 : 2 000,00 DA restent non ventilés sur « Janvier » : "
                                    + "ni reportés ni remboursés par cette correction")))
                    .andExpect(jsonPath("$.preview.effects[*].description").value(hasItem(
                            "Trop-perçu de 2 000,00 DA sur « Janvier » : ni reporté ni remboursé par cette correction")));

            assertThat(cumulOf(s1)).isEqualByComparingTo("4000.00");
            assertThat(cumulOf(s2)).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Arrivée avancée")
    class ArriveePlusTot {

        @BeforeEach
        void amineArrivedOnJanuary10() {
            window(LocalDate.of(2030, 1, 10), null);
            validate(jan7);
        }

        @Test
        @DisplayName("séance validée du 07/01 sans présence : listée, facturable, place réservée")
        void validatedSessionWithoutAttendanceBecomesBillable() throws Exception {
            String token = token(arrival("preview", "\"2030-01-05\"", null, "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[1].type").value("SESSION_BECAME_BILLABLE"))
                    .andExpect(jsonPath("$.preview.effects[1].description").value(
                            "Séance du 07/01/2030 (« Janvier ») validée sans présence de Amine Belkacem : "
                                    + "facturable, sa place était réservée"))
                    // La séance est désignée : l'écran y propose « présent » ou « absent » (5.7).
                    .andExpect(jsonPath("$.preview.effects[1].sessionId").value(jan7.getId()))
                    .andExpect(jsonPath("$.preview.series[0].after.cost").value(4000.0)));

            arrival("confirm", "\"2030-01-05\"", null, "DATA_ENTRY_ERROR", token).andExpect(status().isOk());
            assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();
        }

        @Test
        @DisplayName("absence notée dans la même opération : enregistrée, tracée")
        void absenceRecordedInTheSameOperation() throws Exception {
            String marks = "[{\"sessionId\":" + jan7.getId() + ",\"present\":false}]";
            String token = token(arrival("preview", "\"2030-01-05\"", marks, "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[1].type").value("ATTENDANCE_RECORDED"))
                    .andExpect(jsonPath("$.preview.effects[1].description").value(
                            "Absence de Amine Belkacem le 07/01/2030 (« Janvier ») enregistrée"))
                    // Toujours désignée : l'administratrice peut revenir sur ce qu'elle a noté.
                    .andExpect(jsonPath("$.preview.effects[1].sessionId").value(jan7.getId())));

            arrival("confirm", "\"2030-01-05\"", marks, "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(count("SELECT COUNT(*) FROM attendance WHERE active = TRUE AND status = FALSE AND session_id = "
                    + jan7.getId())).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT action FROM correction_audit ORDER BY id", String.class))
                    .containsExactly("ARRIVAL_DATE_CORRECTED", "ATTENDANCE_ADDED");
        }

        @Test
        @DisplayName("présence notée sur une séance hors de la correction : 400, rien d'écrit")
        void markOnAnotherSessionIsRefused() throws Exception {
            String marks = "[{\"sessionId\":" + jan14.getId() + ",\"present\":true}]";

            arrival("preview", "\"2030-01-05\"", marks, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(startsWith("Séance(s) [" + jan14.getId() + "]")));
        }

        @Test
        @DisplayName("séance notée deux fois, ou sans valeur : 400")
        void malformedMarksAreRefused() throws Exception {
            arrival("preview", "\"2030-01-05\"", "[{\"sessionId\":" + jan7.getId() + ",\"present\":true},"
                    + "{\"sessionId\":" + jan7.getId() + ",\"present\":false}]", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("notée deux fois")));
            arrival("preview", "\"2030-01-05\"", "[{\"sessionId\":" + jan7.getId() + "}]", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Arrivée : refus")
    class ArriveeRefus {

        @Test
        @DisplayName("même date : 400 ; hors de l'année : 400 nommant ses bornes ; date absente : 400")
        void sameOrOutsideOrMissing() throws Exception {
            arrival("preview", "\"" + AMINE_ARRIVES + "\"", null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("est déjà le 01/09/2029")));
            arrival("preview", "\"2030-07-01\"", null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("du 01/09/2029 au 30/06/2030")));
            arrival("preview", null, null, "DATA_ENTRY_ERROR", null).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("après le départ : 409, corriger d'abord le départ")
        void afterDeparture() throws Exception {
            window(LocalDate.parse(AMINE_ARRIVES), LocalDate.of(2030, 1, 7));

            arrival("preview", "\"2030-01-10\"", null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("corrigez d'abord le départ")));
        }

        @Test
        @DisplayName("chevauchant une autre inscription au groupe : 409")
        void overlapWithAnotherEnrolment() throws Exception {
            window(LocalDate.of(2030, 2, 1), null);
            closedEnrolment(LocalDate.parse(AMINE_ARRIVES), LocalDate.of(2029, 12, 31));

            arrival("preview", "\"2029-12-15\"", null, "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("chevaucherait l'autre inscription")));
        }

        @Test
        @DisplayName("motif sans rapport, motif inconnu, sans motif : 400")
        void wrongReasons() throws Exception {
            arrival("preview", "\"2030-01-10\"", null, "WRONG_AMOUNT", null).andExpect(status().isBadRequest());
            arrival("preview", "\"2030-01-10\"", null, "PARCE_QUE", null).andExpect(status().isBadRequest());
            arrival("preview", "\"2030-01-10\"", null, null, null).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("inscription inconnue : 404 ; année close : 409")
        void unknownOrClosedYear() throws Exception {
            send(post("/api/enrolments/999999/arrival/preview"),
                    "{\"arrival\":\"2030-01-10\",\"reasonType\":\"DATA_ENTRY_ERROR\"}")
                    .andExpect(status().isNotFound());

            SchoolYearEntity past = schoolYearRepository.save(SchoolYearEntity.builder().label("2028-2029")
                    .startDate(date(2028, 9, 1)).endDate(date(2029, 6, 30)).isCurrent(false).build());
            GroupEntity old = groupRepository.save(GroupEntity.builder().name("Math 2028")
                    .price(group.getPrice()).schoolYear(past).sessionNumberPerSerie(2).build());
            StudentGroupEntity oldEnrolment = studentGroupRepository.save(StudentGroupEntity.builder()
                    .student(student).group(old).dateAssigned(at(LocalDate.of(2028, 9, 1))).build());
            send(post("/api/enrolments/" + oldEnrolment.getId() + "/arrival/preview"),
                    "{\"arrival\":\"2028-10-01\",\"reasonType\":\"DATA_ENTRY_ERROR\"}")
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Aperçu périmé : une absence apparue depuis change la liste, la confirmation est refusée (5.8)")
        void stalePreview() throws Exception {
            String token = token(arrival("preview", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", null));
            Long absence = mark(jan7, false);

            arrival("confirm", "\"2030-01-10\"", null, "ARRIVAL_DATE_CORRECTED", token)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("STALE_PREVIEW"))
                    .andExpect(jsonPath("$.preview.effects[1].type").value("ABSENCE_REMOVED"));

            assertThat(active(absence)).isTrue();
            assertThat(arrivalInDb()).isEqualTo(LocalDate.parse(AMINE_ARRIVES));
        }
    }

    // ------------------------------------------------------------------
    // Départ
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Départ")
    class Depart {

        @Test
        @DisplayName("enregistré au 07/01 : l'absence du 14/01 retirée, la présence du 04/02 maintenue ; "
                + "février ne coûte plus que la séance suivie")
        void departureRemovesAbsencesAndKeepsPresences() throws Exception {
            Long absence = mark(jan14, false);
            Long presence = mark(feb4, true);
            String token = token(departure("preview", "\"2030-01-07\"", false, "STUDENT_LEFT", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Départ de Amine Belkacem de « Math 1ère A » enregistré au 07/01/2030")));

            departure("confirm", "\"2030-01-07\"", false, "STUDENT_LEFT", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.departure").value("2030-01-07"))
                    .andExpect(jsonPath("$.result.active").value(false))
                    .andExpect(jsonPath("$.preview.effects[*].type").value(hasItem("ABSENCE_REMOVED")))
                    .andExpect(jsonPath("$.preview.effects[*].type").value(hasItem("PRESENCE_KEPT")));

            assertThat(active(absence)).isFalse();
            assertThat(active(presence)).isTrue();
            assertThat(jdbc.queryForObject("SELECT active FROM student_groups WHERE id = ?", Boolean.class,
                    enrolmentId)).isFalse();
            assertThat(costOf(s1)).isEqualByComparingTo("2000.00");
            assertThat(costOf(s2)).isEqualByComparingTo("2000.00");
            assertThat(jdbc.queryForList("SELECT action FROM correction_audit ORDER BY id", String.class))
                    .containsExactly("DEPARTURE_RECORDED", "ATTENDANCE_REMOVED");
        }

        @Test
        @DisplayName("statut stocké de la ligne de paiement recalculé : janvier, réglé à moitié, devient soldé")
        void storedPaymentStatusFollowsTheNewCost() throws Exception {
            pay(s1, 2000);
            assertThat(storedStatus(s1)).isEqualTo(PaymentLineStatus.of(new BigDecimal("2000.00"),
                    java.util.Optional.of(new BigDecimal("4000.00"))));
            String token = token(departure("preview", "\"2030-01-07\"", false, "STUDENT_LEFT", null));

            departure("confirm", "\"2030-01-07\"", false, "STUDENT_LEFT", token).andExpect(status().isOk());

            assertThat(storedStatus(s1)).isEqualTo(PaymentLineStatus.COMPLETED);
        }

        @Test
        @DisplayName("présences postérieures retirées sur demande (6.3) ; un rattrapage n'est jamais touché (6.4)")
        void presencesAfterDepartureRemovedOnRequest() throws Exception {
            Long presence = mark(feb4, true);
            Long catchUp = mark(sessionOn(s2, LocalDate.of(2030, 2, 11)), true);
            jdbc.update("UPDATE attendance SET is_catch_up = TRUE WHERE id = ?", catchUp);
            String token = token(departure("preview", "\"2030-01-14\"", true, "STUDENT_LEFT", null));

            departure("confirm", "\"2030-01-14\"", true, "STUDENT_LEFT", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preview.effects[*].type").value(hasItem("PRESENCE_REMOVED")));

            assertThat(active(presence)).isFalse();
            assertThat(active(catchUp)).isTrue();
        }

        @Test
        @DisplayName("corrigé plus tard, du 07/01 au 14/01 : la séance validée du 14/01 redevient due")
        void departureCorrectedLater() throws Exception {
            window(LocalDate.parse(AMINE_ARRIVES), LocalDate.of(2030, 1, 7));
            validate(jan14);
            String token = token(departure("preview", "\"2030-01-14\"", false, "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Départ de Amine Belkacem de « Math 1ère A » : 07/01/2030 → 14/01/2030"))
                    .andExpect(jsonPath("$.preview.effects[1].type").value("SESSION_BECAME_BILLABLE")));

            departure("confirm", "\"2030-01-14\"", false, "DATA_ENTRY_ERROR", token).andExpect(status().isOk());

            assertThat(costOf(s1)).isEqualByComparingTo("4000.00");
            assertThat(jdbc.queryForObject("SELECT action FROM correction_audit", String.class))
                    .isEqualTo("DEPARTURE_CORRECTED");
        }

        @Test
        @DisplayName("avant l'arrivée : 409 ; hors de l'année : 400 ; même date : 400 ; sans date : 400")
        void refusals() throws Exception {
            departure("preview", "\"2029-08-01\"", false, "STUDENT_LEFT", null).andExpect(status().isBadRequest());
            window(LocalDate.of(2029, 10, 1), null);
            departure("preview", "\"2029-09-15\"", false, "STUDENT_LEFT", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("corrigez d'abord l'arrivée")));
            window(LocalDate.of(2029, 10, 1), LocalDate.of(2030, 1, 7));
            departure("preview", "\"2030-01-07\"", false, "STUDENT_LEFT", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("est déjà le 07/01/2030")));
            departure("preview", null, false, "STUDENT_LEFT", null).andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------
    // Réouverture
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Réouverture")
    class Reouverture {

        @Test
        @DisplayName("départ saisi à tort : l'inscription rouvre, février redevient dû")
        void reopenRestoresTheWindow() throws Exception {
            window(LocalDate.parse(AMINE_ARRIVES), LocalDate.of(2030, 1, 14));
            assertThat(costOf(s2)).isEqualByComparingTo("0.00");
            String token = token(reopen("preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(jsonPath("$.preview.effects[0].description").value(
                            "Inscription de Amine Belkacem à « Math 1ère A » rouverte : départ du 14/01/2030 annulé")));

            reopen("confirm", "DATA_ENTRY_ERROR", token)
                    .andExpect(status().isOk())
                    // Les séances de février ne sont pas validées : rien à y noter, rien à lister.
                    .andExpect(jsonPath("$.preview.effects[*].type").value(not(hasItem("SESSION_BECAME_BILLABLE"))))
                    .andExpect(jsonPath("$.result.active").value(true))
                    .andExpect(jsonPath("$.result.departure").value(nullValue()));

            assertThat(costOf(s2)).isEqualByComparingTo("4000.00");
            assertThat(jdbc.queryForObject("SELECT date_left FROM student_groups WHERE id = ?", Timestamp.class,
                    enrolmentId)).isNull();
            assertThat(jdbc.queryForObject("SELECT action FROM correction_audit", String.class))
                    .isEqualTo("ENROLMENT_REOPENED");
        }

        @Test
        @DisplayName("déjà ouverte : 409 ; une inscription plus récente au groupe : 409")
        void refusals() throws Exception {
            reopen("preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("déjà ouverte")));

            window(LocalDate.parse(AMINE_ARRIVES), LocalDate.of(2029, 12, 31));
            studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(group)
                    .dateAssigned(at(LocalDate.of(2030, 2, 1))).build());
            reopen("preview", "DATA_ENTRY_ERROR", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("chevaucherait")));
        }
    }

    // ------------------------------------------------------------------
    // Accès et motifs
    // ------------------------------------------------------------------

    @Test
    @DisplayName("VIEWER : 403 sur chaque Aperçu et confirmation, rien d'écrit ; les Motifs lui sont lisibles")
    void viewerCannotCorrect() throws Exception {
        for (String url : List.of("arrival/preview", "arrival/confirm", "departure/preview", "departure/confirm",
                "reopen/preview", "reopen/confirm")) {
            mockMvc.perform(post("/api/enrolments/" + enrolmentId + "/" + url).with(user("lecteur").roles("VIEWER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reasonType\":\"DATA_ENTRY_ERROR\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/enrolments/correction-reasons").with(user("lecteur").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ARRIVAL[0]").value("ARRIVAL_DATE_CORRECTED"))
                .andExpect(jsonPath("$.DEPARTURE[0]").value("STUDENT_LEFT"))
                .andExpect(jsonPath("$.REOPEN[0]").value("DATA_ENTRY_ERROR"));
        assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
    }

    @Test
    @DisplayName("sans corps : 400, et non 500")
    void emptyBody() throws Exception {
        mockMvc.perform(post("/api/enrolments/" + enrolmentId + "/reopen/preview").with(user("directrice").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private ResultActions arrival(String step, String date, String marks, String reason, String token) throws Exception {
        return send(post("/api/enrolments/" + enrolmentId + "/arrival/" + step), body("arrival", date, null, marks, reason, token));
    }

    private ResultActions departure(String step, String date, boolean removePresences, String reason, String token)
            throws Exception {
        return send(post("/api/enrolments/" + enrolmentId + "/departure/" + step),
                body("departure", date, removePresences, null, reason, token));
    }

    private ResultActions reopen(String step, String reason, String token) throws Exception {
        return send(post("/api/enrolments/" + enrolmentId + "/reopen/" + step), body(null, null, null, null, reason, token));
    }

    private static String body(String dateField, String date, Boolean removePresences, String marks, String reason,
                               String token) {
        List<String> fields = new ArrayList<>();
        if (dateField != null && date != null) {
            fields.add("\"" + dateField + "\":" + date);
        }
        if (removePresences != null) {
            fields.add("\"removePresencesAfter\":" + removePresences);
        }
        if (marks != null) {
            fields.add("\"attendances\":" + marks);
        }
        if (reason != null) {
            fields.add("\"reasonType\":\"" + reason + "\"");
        }
        if (token != null) {
            fields.add("\"previewToken\":\"" + token + "\"");
        }
        return "{" + String.join(",", fields) + "}";
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String json) throws Exception {
        return mockMvc.perform(request.with(user("directrice").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
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

    private void closedEnrolment(LocalDate arrival, LocalDate departure) {
        StudentGroupEntity other = studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(group)
                .dateAssigned(at(arrival)).build());
        jdbc.update("UPDATE student_groups SET active = FALSE, date_left = ? WHERE id = ?",
                Timestamp.valueOf(departure.atStartOfDay()), other.getId());
    }

    private LocalDate arrivalInDb() {
        return jdbc.queryForObject("SELECT date_assigned FROM student_groups WHERE id = ?", Timestamp.class, enrolmentId)
                .toLocalDateTime().toLocalDate();
    }

    private Long mark(SessionEntity session, boolean present) {
        return attendanceRepository.save(AttendanceEntity.builder().student(student).session(session)
                .sessionSeries(session.getSessionSeries()).group(group).isPresent(present).isCatchUp(false)
                .build()).getId();
    }

    private boolean active(Long attendanceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT active FROM attendance WHERE id = ?", Boolean.class,
                attendanceId));
    }

    private void validate(SessionEntity session) {
        jdbc.update("UPDATE session SET is_finished = TRUE WHERE id = ?", session.getId());
    }

    private long activeLinesOn(SessionEntity session) {
        return count("SELECT COUNT(*) FROM payment_detail WHERE active = TRUE AND session_id = " + session.getId());
    }

    private String storedStatus(SessionSeriesEntity series) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE student_id = ? AND session_series_id = ?",
                String.class, student.getId(), series.getId());
    }

    /** Coût au prorata de la série, recalculé depuis la base. */
    private BigDecimal costOf(SessionSeriesEntity series) {
        return costResolver.resolve(student.getId(), series.getId()).monthTotalCost();
    }

    private SessionEntity sessionOn(SessionSeriesEntity series, LocalDate day) {
        return sessionRepository.findAll().stream()
                .filter(s -> s.getSessionSeries().getId().equals(series.getId()))
                .filter(s -> LocalDate.ofInstant(s.getSessionTimeStart().toInstant(), ZoneId.systemDefault()).equals(day))
                .findFirst().orElseThrow();
    }

    private static Date at(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
