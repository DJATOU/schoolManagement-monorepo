package com.school.management.controller.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.AttendanceJustificationAuditEntity;
import com.school.management.persistance.CatchUpBillingAuditEntity;
import com.school.management.persistance.CatchUpBillingAuditField;
import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionAuditEntity;
import com.school.management.persistance.CorrectionDomain;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.AttendanceJustificationAuditRepository;
import com.school.management.repository.CatchUpBillingAuditRepository;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Journal d'un élève : ce qui a été corrigé à son sujet, en clair, du plus récent au plus ancien
 * (spec admin-corrections, D.4 ; exigences 12.1 à 12.3, 12.5 ; D8).
 *
 * <p>Socle : groupe « Math 1ère A », séances à 2 000 DA ; Amine Belkacem inscrit depuis le 01/09/2029 ;
 * séance du 07/01/2030 validée.</p>
 */
@AutoConfigureMockMvc
@DisplayName("GET /api/students/{id}/journal")
class CorrectionJournalEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AttendanceJustificationAuditRepository justificationAuditRepository;
    @Autowired private CatchUpBillingAuditRepository catchUpAuditRepository;

    private SessionEntity jan7;

    @BeforeEach
    void januarySeventhValidated() {
        clearOtherAudits();
        jdbc.update("UPDATE student_groups SET date_assigned = ? WHERE student_id = ?",
                Timestamp.valueOf(LocalDate.of(2029, 9, 1).atStartOfDay()), student.getId());
        jan7 = s1First;
        jdbc.update("UPDATE session SET is_finished = TRUE WHERE id = ?", jan7.getId());
    }

    @AfterEach
    void clearOtherAudits() {
        justificationAuditRepository.deleteAll();
        catchUpAuditRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Sources
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Sources")
    class Sources {

        @Test
        @DisplayName("justification, correction de présence, versement annulé : écrits par leurs services, lus en "
                + "clair, le plus récent d'abord")
        void realWriters() throws Exception {
            Long absence = mark(student, jan7, false);
            send(patch("/api/attendances/" + absence + "/justification"),
                    "{\"justified\":true,\"comment\":\"Certificat médical\"}").andExpect(status().isOk());
            String token = token(send(post("/api/attendances/" + absence + "/correct/preview"),
                    "{\"present\":true,\"reasonType\":\"DATA_ENTRY_ERROR\"}"));
            send(post("/api/attendances/" + absence + "/correct/confirm"),
                    "{\"present\":true,\"reasonType\":\"DATA_ENTRY_ERROR\",\"previewToken\":\"" + token + "\"}")
                    .andExpect(status().isOk());
            Long encashment = pay(s1, 2000).encashment().getId();
            String receipt = receiptOf(encashment);
            token = token(send(post("/api/encashments/" + encashment + "/cancel/preview"),
                    "{\"reasonType\":\"OTHER\",\"reasonText\":\"Versé sur le mauvais mois\"}"));
            send(post("/api/encashments/" + encashment + "/cancel/confirm"), "{\"reasonType\":\"OTHER\","
                    + "\"reasonText\":\"Versé sur le mauvais mois\",\"previewToken\":\"" + token + "\"}")
                    .andExpect(status().isOk());

            journal(student, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.studentId").value(student.getId()))
                    .andExpect(jsonPath("$.studentName").value("Amine Belkacem"))
                    .andExpect(jsonPath("$.from").value(nullValue()))
                    .andExpect(jsonPath("$.to").value(nullValue()))
                    .andExpect(jsonPath("$.entries.length()").value(3))
                    .andExpect(jsonPath("$.entries[0].category").value("ENCASHMENT"))
                    .andExpect(jsonPath("$.entries[0].description").value(
                            "Reçu " + receipt + " de 2 000,00 DA annulé (Janvier, Math 1ère A)"))
                    .andExpect(jsonPath("$.entries[0].amountEffect").value(containsString(
                            "Janvier (Math 1ère A) : versé 2 000,00 → 0,00 DA")))
                    .andExpect(jsonPath("$.entries[0].reasonType").value("OTHER"))
                    .andExpect(jsonPath("$.entries[0].reasonText").value("Versé sur le mauvais mois"))
                    .andExpect(jsonPath("$.entries[0].performedBy").value("directrice"))
                    .andExpect(jsonPath("$.entries[1].category").value("ATTENDANCE"))
                    .andExpect(jsonPath("$.entries[1].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : Amine Belkacem absent (justifié) → présent"))
                    .andExpect(jsonPath("$.entries[1].amountEffect").value(containsString(
                            "Janvier (Math 1ère A) : dû à ce jour 0,00 → 2 000,00 DA")))
                    .andExpect(jsonPath("$.entries[1].reasonType").value("DATA_ENTRY_ERROR"))
                    .andExpect(jsonPath("$.entries[2].category").value("JUSTIFICATION"))
                    .andExpect(jsonPath("$.entries[2].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : absence non justifiée → justifiée"))
                    .andExpect(jsonPath("$.entries[2].amountEffect").value(nullValue()))
                    .andExpect(jsonPath("$.entries[2].reasonType").value(nullValue()))
                    .andExpect(jsonPath("$.entries[2].reasonText").value("Certificat médical"))
                    .andExpect(jsonPath("$.entries[2].performedBy").value("directrice"));
        }

        @Test
        @DisplayName("décisions de rattrapage : séance manquée nommée par son jour et son groupe, « supprimée » si "
                + "elle n'existe plus ; « déjà payée » en clair")
        void catchUpDecisions() throws Exception {
            GroupEntity groupB = groupRepository.save(GroupEntity.builder().name("Math 1ère B").price(group.getPrice())
                    .schoolYear(year).sessionNumberPerSerie(2).build());
            SessionSeriesEntity januaryB = persistSeries(groupB, "Janvier B", date(2030, 1, 9));
            SessionEntity jan9InB = sessionOn(januaryB);
            Long catchUp = attendanceRepository.save(AttendanceEntity.builder().student(student).session(jan9InB)
                    .sessionSeries(januaryB).group(groupB).isPresent(true).isCatchUp(true).build()).getId();
            catchUpDecision(catchUp, CatchUpBillingAuditField.MISSED_SESSION, null, String.valueOf(jan7.getId()),
                    at(2030, 1, 10, 9), 1, "Rattrapage accordé par la direction");
            catchUpDecision(catchUp, CatchUpBillingAuditField.ALREADY_PAID, null, "true", at(2030, 1, 10, 10), 2, null);
            catchUpDecision(catchUp, CatchUpBillingAuditField.MISSED_SESSION, String.valueOf(jan7.getId()), "999999",
                    at(2030, 1, 11, 9), 3, null);
            catchUpDecision(catchUp, CatchUpBillingAuditField.ALREADY_PAID, "true", "false", at(2030, 1, 12, 9), 4, null);

            journal(student, "")
                    .andExpect(jsonPath("$.entries.length()").value(4))
                    .andExpect(jsonPath("$.entries[0].category").value("CATCH_UP"))
                    .andExpect(jsonPath("$.entries[0].description").value(
                            "Rattrapage du 09/01/2030 (Math 1ère B) : déjà payée oui → non"))
                    .andExpect(jsonPath("$.entries[0].performedAt").value("2030-01-12T09:00:00"))
                    .andExpect(jsonPath("$.entries[1].description").value(
                            "Rattrapage du 09/01/2030 (Math 1ère B) : séance manquée 07/01/2030 (Math 1ère A) → "
                                    + "supprimée"))
                    .andExpect(jsonPath("$.entries[2].description").value(
                            "Rattrapage du 09/01/2030 (Math 1ère B) : déjà payée non tranché → oui"))
                    .andExpect(jsonPath("$.entries[3].description").value(
                            "Rattrapage du 09/01/2030 (Math 1ère B) : séance manquée aucune → 07/01/2030 (Math 1ère A)"))
                    .andExpect(jsonPath("$.entries[3].reasonText").value("Rattrapage accordé par la direction"))
                    .andExpect(jsonPath("$.entries[3].reasonType").value(nullValue()))
                    .andExpect(jsonPath("$.entries[3].amountEffect").value(nullValue()))
                    .andExpect(jsonPath("$.entries[3].performedBy").value("directrice"));
        }

        @Test
        @DisplayName("justification retirée ou jamais renseignée : « non justifiée » ; présence retirée depuis : "
                + "toujours nommée")
        void justificationValues() throws Exception {
            Long absence = mark(student, jan7, false);
            justification(absence, true, false, at(2030, 1, 8, 9), 1);
            justification(absence, null, true, at(2030, 1, 9, 9), 2);
            jdbc.update("UPDATE attendance SET active = FALSE WHERE id = ?", absence);

            journal(student, "")
                    .andExpect(jsonPath("$.entries.length()").value(2))
                    .andExpect(jsonPath("$.entries[0].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : absence non justifiée → justifiée"))
                    .andExpect(jsonPath("$.entries[1].description").value(
                            "Séance du 07/01/2030 (Math 1ère A) : absence justifiée → non justifiée"));
        }

        @Test
        @DisplayName("catégories : versement, inscription, présence, rattrapage retiré ; la Trace d'une séance "
                + "dévalidée n'y est pas, la ligne retirée de l'élève si")
        void categories() throws Exception {
            trace(CorrectionDomain.ENCASHMENT, CorrectionAction.ENCASHMENT_REPLACED, student, "Reçu remplacé",
                    at(2030, 1, 5, 9));
            trace(CorrectionDomain.ENROLMENT, CorrectionAction.ARRIVAL_DATE_CORRECTED, student, "Arrivée corrigée",
                    at(2030, 1, 6, 9));
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.ATTENDANCE_REMOVED, student, "Présence retirée",
                    at(2030, 1, 7, 9));
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.CATCH_UP_REMOVED, student, "Rattrapage retiré",
                    at(2030, 1, 8, 9));
            trace(CorrectionDomain.SESSION, CorrectionAction.SESSION_UNVALIDATED, null, "Séance dévalidée",
                    at(2030, 1, 9, 9));

            journal(student, "")
                    .andExpect(jsonPath("$.entries.length()").value(4))
                    .andExpect(jsonPath("$.entries[0].category").value("CATCH_UP"))
                    .andExpect(jsonPath("$.entries[1].category").value("ATTENDANCE"))
                    .andExpect(jsonPath("$.entries[2].category").value("ENROLMENT"))
                    .andExpect(jsonPath("$.entries[3].category").value("ENCASHMENT"))
                    .andExpect(jsonPath("$.entries[3].description").value("Reçu remplacé"));
        }

        @Test
        @DisplayName("un autre élève : rien de lui, d'aucune source")
        void otherStudent() throws Exception {
            StudentEntity lina = studentRepository.save(StudentEntity.builder().firstName("Lina").lastName("Haddad")
                    .build());
            Long linaAbsence = mark(lina, jan7, false);
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.PRESENCE_CHANGED, lina, "Lina", at(2030, 1, 8, 9));
            justification(linaAbsence, false, true, at(2030, 1, 8, 10), 1);
            catchUpDecision(linaAbsence, CatchUpBillingAuditField.ALREADY_PAID, null, "true", at(2030, 1, 8, 11), 1,
                    null);
            mark(student, jan7, true);

            journal(student, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.entries.length()").value(0));
            journal(lina, "").andExpect(jsonPath("$.entries.length()").value(3));
        }

        @Test
        @DisplayName("élève sans présence ni trace : Journal vide")
        void empty() throws Exception {
            journal(student, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.studentName").value("Amine Belkacem"))
                    .andExpect(jsonPath("$.entries.length()").value(0));
        }
    }

    // ------------------------------------------------------------------
    // Ordre et période
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Ordre et période")
    class OrdreEtPeriode {

        @Test
        @DisplayName("même horodatage : la dernière écrite d'abord")
        void sameInstant() throws Exception {
            LocalDateTime instant = at(2030, 1, 14, 9);
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.ATTENDANCE_REMOVED, student, "première", instant);
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.ATTENDANCE_REMOVED, student, "seconde", instant);
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.ATTENDANCE_REMOVED, student, "plus ancienne",
                    instant.minusSeconds(1));

            journal(student, "")
                    .andExpect(jsonPath("$.entries[0].description").value("seconde"))
                    .andExpect(jsonPath("$.entries[1].description").value("première"))
                    .andExpect(jsonPath("$.entries[2].description").value("plus ancienne"));
        }

        @Test
        @DisplayName("même horodatage, justification et rattrapage : la dernière écrite d'abord aussi")
        void sameInstantInOtherSources() throws Exception {
            LocalDateTime justified = at(2030, 1, 15, 9);
            LocalDateTime decided = at(2030, 1, 16, 9);
            Long absence = mark(student, jan7, false);
            justification(absence, false, true, justified, 1);
            justification(absence, true, false, justified, 2);
            catchUpDecision(absence, CatchUpBillingAuditField.ALREADY_PAID, null, "true", decided, 1, null);
            catchUpDecision(absence, CatchUpBillingAuditField.ALREADY_PAID, "true", "false", decided, 2, null);

            journal(student, "")
                    .andExpect(jsonPath("$.entries[0].description").value(containsString("déjà payée oui → non")))
                    .andExpect(jsonPath("$.entries[1].description").value(containsString("non tranché → oui")))
                    .andExpect(jsonPath("$.entries[2].description").value(containsString("justifiée → non justifiée")))
                    .andExpect(jsonPath("$.entries[3].description").value(containsString(
                            "absence non justifiée → justifiée")));
        }

        @Test
        @DisplayName("période : bornes incluses, à la journée ; chacune facultative")
        void period() throws Exception {
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.ATTENDANCE_REMOVED, student, "09/01", at(2030, 1, 9, 23));
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.ATTENDANCE_REMOVED, student, "10/01 à minuit",
                    LocalDateTime.of(2030, 1, 10, 0, 0));
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.ATTENDANCE_REMOVED, student, "31/01 au soir",
                    LocalDateTime.of(2030, 1, 31, 23, 59, 59));
            trace(CorrectionDomain.ATTENDANCE, CorrectionAction.ATTENDANCE_REMOVED, student, "01/02",
                    LocalDateTime.of(2030, 2, 1, 0, 0));

            journal(student, "?from=2030-01-10&to=2030-01-31")
                    .andExpect(jsonPath("$.from").value("2030-01-10"))
                    .andExpect(jsonPath("$.to").value("2030-01-31"))
                    .andExpect(jsonPath("$.entries.length()").value(2))
                    .andExpect(jsonPath("$.entries[0].description").value("31/01 au soir"))
                    .andExpect(jsonPath("$.entries[1].description").value("10/01 à minuit"));
            journal(student, "?from=2030-01-31").andExpect(jsonPath("$.entries.length()").value(2));
            journal(student, "?to=2030-01-09")
                    .andExpect(jsonPath("$.entries.length()").value(1))
                    .andExpect(jsonPath("$.entries[0].description").value("09/01"));
            journal(student, "?from=2030-01-10&to=2030-01-10")
                    .andExpect(jsonPath("$.entries.length()").value(1))
                    .andExpect(jsonPath("$.entries[0].description").value("10/01 à minuit"));
        }

        @Test
        @DisplayName("année close : consultable, ses traces comprises")
        void closedYear() throws Exception {
            SchoolYearEntity past = schoolYearRepository.save(SchoolYearEntity.builder().label("2028-2029")
                    .startDate(date(2028, 9, 1)).endDate(date(2029, 6, 30)).isCurrent(false).build());
            GroupEntity old = groupRepository.save(GroupEntity.builder().name("Math 2028").price(group.getPrice())
                    .schoolYear(past).sessionNumberPerSerie(2).build());
            SessionSeriesEntity october = persistSeries(old, "Octobre 2028", date(2028, 10, 2));
            Long absence = attendanceRepository.save(AttendanceEntity.builder().student(student)
                    .session(sessionOn(october)).sessionSeries(october).group(old).isPresent(false).build()).getId();
            justification(absence, false, true, at(2028, 10, 3, 9), 1);

            journal(student, "?from=2028-09-01&to=2029-06-30")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.entries[0].description").value(
                            "Séance du 02/10/2028 (Math 2028) : absence non justifiée → justifiée"));
        }
    }

    // ------------------------------------------------------------------
    // Refus
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("période à l'envers, date mal formée : 400 ; élève inconnu : 404")
        void invalid() throws Exception {
            journal(student, "?from=2030-01-31&to=2030-01-10")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Période invalide : du 31/01/2030 au 10/01/2030, la fin précède le début."));
            journal(student, "?from=2030-13-40")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Paramètre « from » invalide : 2030-13-40"));
            mockMvc.perform(get("/api/students/999999/journal").with(user("directrice").roles("ADMIN")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Étudiant introuvable : 999999"));
        }

        @Test
        @DisplayName("VIEWER : 403, le Journal nomme les reçus et leurs montants")
        void viewer() throws Exception {
            mockMvc.perform(get("/api/students/" + student.getId() + "/journal").with(user("lecteur").roles("VIEWER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("sans connexion : 401")
        void anonymous() throws Exception {
            // Le socle authentifie l'administrateur sur ce fil ; MockMvc reprendrait ce contexte.
            SecurityContextHolder.clearContext();
            mockMvc.perform(get("/api/students/" + student.getId() + "/journal"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private ResultActions journal(StudentEntity who, String query) throws Exception {
        return mockMvc.perform(get("/api/students/" + who.getId() + "/journal" + query)
                .with(user("directrice").roles("ADMIN")));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(user("directrice").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
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

    private void trace(CorrectionDomain domain, CorrectionAction action, StudentEntity who, String summary,
                       LocalDateTime performedAt) {
        auditRepository.save(CorrectionAuditEntity.builder().domain(domain).action(action).entityId(1L)
                .studentId(who == null ? null : who.getId()).summary(summary)
                .reasonType(CorrectionReasonType.DATA_ENTRY_ERROR).performedBy("directrice")
                .performedAt(performedAt).build());
    }

    private void justification(Long attendanceId, Boolean before, boolean after, LocalDateTime performedAt,
                               long rank) {
        justificationAuditRepository.save(AttendanceJustificationAuditEntity.builder().attendanceId(attendanceId)
                .oldValue(before).newValue(after).performedBy("directrice").performedAt(performedAt)
                .sequenceRank(rank).build());
    }

    private void catchUpDecision(Long attendanceId, CatchUpBillingAuditField field, String before, String after,
                                 LocalDateTime performedAt, long rank, String comment) {
        catchUpAuditRepository.save(CatchUpBillingAuditEntity.builder().attendanceId(attendanceId).field(field)
                .oldValue(before).newValue(after).performedBy("directrice").performedAt(performedAt)
                .sequenceRank(rank).comment(comment).build());
    }

    private SessionEntity sessionOn(SessionSeriesEntity series) {
        return sessionRepository.findAll().stream()
                .filter(s -> s.getSessionSeries() != null && s.getSessionSeries().getId().equals(series.getId()))
                .findFirst().orElseThrow();
    }

    private static LocalDateTime at(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0);
    }
}
