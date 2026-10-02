package com.school.management.controller;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aucune absence hors Fenêtre_Inscription, sur chaque point d'entrée (spec admin-corrections,
 * C.4 ; exigences 7.3 à 7.5).
 *
 * <p>Groupe « Math 1ère A » du socle, où Amine est inscrit depuis toujours, et :</p>
 * <ul>
 *   <li>Lina Haddad, arrivée le 14/01/2030 ;</li>
 *   <li>Sami Kaci, parti le 14/01/2030 ;</li>
 *   <li>Nour Zerrouki, partie le 31/12/2029 et revenue le 01/02/2030 ;</li>
 *   <li>Karim Saïdi, jamais inscrit à ce groupe.</li>
 * </ul>
 * <p>Séances du socle : lundis 07/01, 14/01, 04/02 et 11/02/2030 à 10:00.</p>
 */
@AutoConfigureMockMvc
@DisplayName("Absences hors fenêtre d'inscription")
class AbsenceWindowEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    private static final DateTimeFormatter SESSION_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Autowired private MockMvc mockMvc;

    private SessionEntity january7;
    private SessionEntity january14;
    private SessionEntity february4;
    private StudentEntity lina;
    private StudentEntity sami;
    private StudentEntity nour;
    private StudentEntity karim;
    private GroupEntity physique;

    @BeforeEach
    void enrolOthers() {
        january7 = sessionOn(s1, LocalDate.of(2030, 1, 7));
        january14 = sessionOn(s1, LocalDate.of(2030, 1, 14));
        february4 = sessionOn(s2, LocalDate.of(2030, 2, 4));

        lina = newStudent("Lina", "Haddad");
        enrol(lina, group, LocalDate.of(2030, 1, 14), null);
        sami = newStudent("Sami", "Kaci");
        enrol(sami, group, LocalDate.of(2029, 9, 15), LocalDate.of(2030, 1, 14));
        nour = newStudent("Nour", "Zerrouki");
        enrol(nour, group, LocalDate.of(2029, 9, 1), LocalDate.of(2029, 12, 31));
        enrol(nour, group, LocalDate.of(2030, 2, 1), null);

        physique = groupRepository.save(GroupEntity.builder()
                .name("Physique 1ère A").price(group.getPrice()).schoolYear(year).sessionNumberPerSerie(2).build());
        karim = newStudent("Karim", "Saïdi");
        enrol(karim, physique, LocalDate.of(2029, 9, 1), null);
    }

    // ------------------------------------------------------------------
    // Feuille de présence (validation d'une séance)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Feuille de présence")
    class Feuille {

        @Test
        @DisplayName("deux absences hors fenêtre : 409, chaque ligne nommée, rien d'écrit — pas même la ligne valide")
        void wholeSubmissionIsRefusedAndEachLineNamed() throws Exception {
            bulk(january7, line(student, false), line(lina, false), line(karim, false))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ABSENCE_OUTSIDE_WINDOW"))
                    .andExpect(jsonPath("$.message").value(startsWith(
                            "Validation refusée : 2 absences sont notées pour des étudiants que la séance ne "
                                    + "concerne pas. Retirez ces lignes et validez de nouveau.")))
                    .andExpect(jsonPath("$.rejected", hasSize(2)))
                    .andExpect(jsonPath("$.rejected[*].studentId").value(contains(lina.getId().intValue(),
                            karim.getId().intValue())))
                    .andExpect(jsonPath("$.rejected[0].sessionId").value(january7.getId()))
                    .andExpect(jsonPath("$.rejected[0].sessionDay").value("2030-01-07"))
                    .andExpect(jsonPath("$.rejected[0].reason").value("OUTSIDE_WINDOW"))
                    .andExpect(jsonPath("$.rejected[0].windows[0].arrival").value("2030-01-14"))
                    .andExpect(jsonPath("$.rejected[0].message").value("Absence de Lina Haddad le 07/01/2030 : "
                            + "hors de son inscription au groupe « Math 1ère A » (à partir du 14/01/2030)."))
                    .andExpect(jsonPath("$.rejected[1].reason").value("NOT_ENROLLED"))
                    .andExpect(jsonPath("$.rejected[1].windows", hasSize(0)))
                    .andExpect(jsonPath("$.rejected[1].message").value("Absence de Karim Saïdi le 07/01/2030 : "
                            + "aucune inscription au groupe « Math 1ère A »."));

            assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();
        }

        @Test
        @DisplayName("lignes refusées retirées : la même feuille est acceptée")
        void sameSubmissionWithoutRejectedLinesIsAccepted() throws Exception {
            bulk(january7, line(student, false)).andExpect(status().isOk());

            assertThat(count("SELECT COUNT(*) FROM attendance WHERE status = FALSE")).isEqualTo(1);
        }

        @Test
        @DisplayName("présences hors fenêtre ou hors groupe : acceptées, séances consommées (7.4)")
        void presencesOutsideTheWindowAreAccepted() throws Exception {
            bulk(january7, line(lina, true), line(karim, true)).andExpect(status().isOk());

            assertThat(count("SELECT COUNT(*) FROM attendance WHERE status = TRUE")).isEqualTo(2);
        }

        @Test
        @DisplayName("jour d'arrivée et jour de départ : absences acceptées")
        void arrivalAndDepartureDaysAreInside() throws Exception {
            bulk(january14, line(lina, false), line(sami, false)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("lendemain du départ : refusée, la fenêtre close est nommée ; message au singulier")
        void dayAfterDepartureIsRefused() throws Exception {
            bulk(february4, line(sami, false))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(startsWith("Validation refusée : une absence est notée "
                            + "pour un étudiant que la séance ne concerne pas. Retirez cette ligne")))
                    .andExpect(jsonPath("$.message").value(containsString("(du 15/09/2029 au 14/01/2030)")))
                    .andExpect(jsonPath("$.rejected[0].windows[0].departure").value("2030-01-14"));
        }

        @Test
        @DisplayName("étudiant revenu, absent entre ses deux inscriptions : ses deux fenêtres sont nommées")
        void betweenTwoEnrolments() throws Exception {
            bulk(january7, line(nour, false))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.rejected[0].message").value(containsString(
                            "(du 01/09/2029 au 31/12/2029 ; à partir du 01/02/2030)")));
        }

        @Test
        @DisplayName("présence non renseignée : jugée comme une absence")
        void unsetPresenceCountsAsAbsence() throws Exception {
            bulk(january7, line(lina, null)).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("doublon : 409, et non plus 500")
        void duplicateIsAConflict() throws Exception {
            bulk(january7, line(student, true)).andExpect(status().isOk());
            bulk(january7, line(student, true)).andExpect(status().isConflict());
        }
    }

    // ------------------------------------------------------------------
    // Présence unitaire
    // ------------------------------------------------------------------

    @Test
    @DisplayName("présence unitaire : absence hors fenêtre refusée, absence dans la fenêtre acceptée")
    void singleAttendanceEntryPoint() throws Exception {
        send(post("/api/attendances"), json(line(lina, false), january7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.rejected", hasSize(1)));
        assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();

        send(post("/api/attendances"), json(line(lina, false), january14)).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Séance déplacée
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Séance pointée déplacée")
    class Deplacement {

        @BeforeEach
        void linaAbsentOnHerArrivalDay() throws Exception {
            bulk(january14, line(lina, false)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("la veille de son arrivée : 409, la séance garde son jour")
        void moveBeforeArrivalIsRefused() throws Exception {
            send(patch("/api/sessions/" + january14.getId()),
                    "{\"sessionTimeStart\":\"" + utc(LocalDate.of(2030, 1, 13).atTime(10, 0)) + "\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(startsWith("Modification refusée : la séance porte une "
                            + "absence qui sortirait de la période d'inscription.")))
                    .andExpect(jsonPath("$.rejected[0].message").value(
                            "Absence de Lina Haddad le 13/01/2030 : hors de son inscription au groupe "
                                    + "« Math 1ère A » (à partir du 14/01/2030)."));

            assertThat(dayOf(january14)).isEqualTo(LocalDate.of(2030, 1, 14));
        }

        @Test
        @DisplayName("plus tard dans sa fenêtre : accepté")
        void moveInsideTheWindowIsAccepted() throws Exception {
            send(patch("/api/sessions/" + january14.getId()),
                    "{\"sessionTimeStart\":\"" + utc(LocalDate.of(2030, 1, 15).atTime(10, 0)) + "\"}")
                    .andExpect(status().isOk());

            assertThat(dayOf(january14)).isEqualTo(LocalDate.of(2030, 1, 15));
        }

        @Test
        @DisplayName("même jour, autre heure : aucun contrôle nécessaire, accepté")
        void sameDayIsAccepted() throws Exception {
            send(patch("/api/sessions/" + january14.getId()),
                    "{\"sessionTimeStart\":\"" + utc(LocalDate.of(2030, 1, 14).atTime(16, 0)) + "\"}")
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("vers un groupe où l'étudiante n'est pas inscrite : 409, la séance garde son groupe")
        void moveToAnotherGroupIsRefused() throws Exception {
            send(patch("/api/sessions/" + january14.getId()), "{\"groupId\":" + physique.getId() + "}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.rejected[0].reason").value("NOT_ENROLLED"));

            assertThat(jdbc.queryForObject("SELECT group_id FROM session WHERE id = ?", Long.class,
                    january14.getId())).isEqualTo(group.getId());
        }

        @Test
        @DisplayName("titre seul changé : accepté")
        void unrelatedChangeIsAccepted() throws Exception {
            send(patch("/api/sessions/" + january14.getId()), "{\"title\":\"Séance du 14\"}")
                    .andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private record Line(StudentEntity student, Boolean present) {
    }

    private static Line line(StudentEntity who, Boolean present) {
        return new Line(who, present);
    }

    private ResultActions bulk(SessionEntity session, Line... lines) throws Exception {
        String body = List.of(lines).stream().map(l -> json(l, session)).collect(Collectors.joining(",", "[", "]"));
        return send(post("/api/attendances/bulk"), body);
    }

    /** Ligne telle que l'écran de validation l'envoie. */
    private String json(Line line, SessionEntity session) {
        return "{\"studentId\":" + line.student().getId()
                + ",\"sessionId\":" + session.getId()
                + ",\"groupId\":" + group.getId()
                + ",\"sessionSeriesId\":" + session.getSessionSeries().getId()
                + ",\"isPresent\":" + line.present()
                + ",\"isJustified\":false,\"isCatchUp\":false,\"description\":\"\"}";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(user("directrice").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private SessionEntity sessionOn(SessionSeriesEntity series, LocalDate day) {
        return sessionRepository.findAll().stream()
                .filter(s -> s.getSessionSeries().getId().equals(series.getId()))
                .filter(s -> LocalDate.ofInstant(s.getSessionTimeStart().toInstant(), ZoneId.systemDefault()).equals(day))
                .findFirst().orElseThrow();
    }

    private LocalDate dayOf(SessionEntity session) {
        return jdbc.queryForObject("SELECT session_time_start FROM session WHERE id = ?",
                java.sql.Timestamp.class, session.getId()).toLocalDateTime().toLocalDate();
    }

    private StudentEntity newStudent(String firstName, String lastName) {
        return studentRepository.save(StudentEntity.builder().firstName(firstName).lastName(lastName).build());
    }

    private void enrol(StudentEntity who, GroupEntity in, LocalDate arrival, LocalDate departure) {
        StudentGroupEntity enrolment = studentGroupRepository.save(StudentGroupEntity.builder()
                .student(who).group(in).dateAssigned(at(arrival.atStartOfDay())).build());
        if (departure != null) {
            enrolment.setActive(false);
            enrolment.setDateLeft(at(departure.atStartOfDay()));
            studentGroupRepository.save(enrolment);
        }
    }

    private static String utc(LocalDateTime local) {
        return SESSION_TIME.format(local.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static Date at(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
