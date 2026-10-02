package com.school.management.controller;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feuille_Appel de bout en bout (spec admin-corrections, C.3 ; exigences 6.2, 7.1, 7.2).
 *
 * <p>Le groupe « Math 1ère A » du socle, où Amine est inscrit depuis toujours, reçoit trois autres
 * élèves aux fenêtres choisies pour encadrer la séance du lundi 14/01/2030 à 10:00 :</p>
 * <ul>
 *   <li>Lina Haddad arrive le 14/01/2030 : concernée ce jour-là, pas la semaine d'avant ;</li>
 *   <li>Sami Kaci est parti le 14/01/2030 : encore concerné ce jour-là, départ inclus ;</li>
 *   <li>Nour Zerrouki est partie le 31/12/2029 et revenue le 01/02/2030 : deux inscriptions.</li>
 * </ul>
 */
@AutoConfigureMockMvc
@DisplayName("GET /api/sessions/{id}/roll-call")
class RollCallEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;

    private SessionEntity january7;
    private SessionEntity january14;
    private SessionEntity february4;

    @BeforeEach
    void enrolOthers() {
        january7 = sessionOn(s1, LocalDate.of(2030, 1, 7));
        january14 = sessionOn(s1, LocalDate.of(2030, 1, 14));
        february4 = sessionOn(s2, LocalDate.of(2030, 2, 4));

        enrol(newStudent("Lina", "Haddad"), LocalDate.of(2030, 1, 14), null);
        enrol(newStudent("Sami", "Kaci"), LocalDate.of(2029, 9, 15), LocalDate.of(2030, 1, 14));
        StudentEntity nour = newStudent("Nour", "Zerrouki");
        enrol(nour, LocalDate.of(2029, 9, 1), LocalDate.of(2029, 12, 31));
        enrol(nour, LocalDate.of(2030, 2, 1), null);
    }

    @Test
    @DisplayName("arrivée et départ le jour même de la séance : les deux élèves sont attendus, par nom")
    void arrivalAndDepartureDaysAreIncluded() throws Exception {
        rollCall(january14)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(january14.getId()))
                .andExpect(jsonPath("$.groupId").value(group.getId()))
                .andExpect(jsonPath("$.sessionDay").value("2030-01-14"))
                .andExpect(jsonPath("$.students[*].lastName").value(contains("Belkacem", "Haddad", "Kaci")))
                .andExpect(jsonPath("$.students[1].arrival").value("2030-01-14"))
                .andExpect(jsonPath("$.students[1].departure").value(nullValue()))
                .andExpect(jsonPath("$.students[2].departure").value("2030-01-14"))
                .andExpect(jsonPath("$.notConcerned[*].lastName").value(contains("Zerrouki")));
    }

    @Test
    @DisplayName("la semaine d'avant : Lina n'est pas encore arrivée, et la feuille dit pourquoi")
    void notYetArrivedIsExplained() throws Exception {
        rollCall(january7)
                .andExpect(jsonPath("$.students[*].lastName").value(contains("Belkacem", "Kaci")))
                .andExpect(jsonPath("$.notConcerned[*].lastName").value(contains("Haddad", "Zerrouki")))
                .andExpect(jsonPath("$.notConcerned[0].windows[0].arrival").value("2030-01-14"))
                .andExpect(jsonPath("$.notConcerned[0].windows[0].departure").value(nullValue()));
    }

    @Test
    @DisplayName("après le départ de Sami et le retour de Nour : Nour une seule fois, Sami expliqué")
    void returnedStudentAppearsOnceAndDepartedIsExplained() throws Exception {
        rollCall(february4)
                .andExpect(jsonPath("$.students[*].lastName").value(contains("Belkacem", "Haddad", "Zerrouki")))
                .andExpect(jsonPath("$.students[2].arrival").value("2030-02-01"))
                .andExpect(jsonPath("$.notConcerned[*].lastName").value(contains("Kaci")))
                .andExpect(jsonPath("$.notConcerned[0].windows[0].departure").value("2030-01-14"));
    }

    @Test
    @DisplayName("entre deux inscriptions : les deux fenêtres, de la plus ancienne à la plus récente")
    void bothWindowsOfAReturnedStudentAreListed() throws Exception {
        rollCall(january14)
                .andExpect(jsonPath("$.notConcerned[0].windows[*].arrival").value(contains("2029-09-01", "2030-02-01")))
                .andExpect(jsonPath("$.notConcerned[0].windows[0].departure").value("2029-12-31"))
                .andExpect(jsonPath("$.notConcerned[0].windows[1].departure").value(nullValue()));
    }

    @Test
    @DisplayName("lendemain du départ, dès 00:30 : Sami n'est plus attendu")
    void dayAfterDepartureEvenJustAfterMidnight() throws Exception {
        SessionEntity earlyNextDay = sessionRepository.save(SessionEntity.builder()
                .title("Séance de nuit").group(group).sessionSeries(s1)
                .sessionTimeStart(at(LocalDate.of(2030, 1, 15).atTime(0, 30))).build());

        rollCall(earlyNextDay)
                .andExpect(jsonPath("$.students[*].lastName").value(contains("Belkacem", "Haddad")));
    }

    @Test
    @DisplayName("personne de concerné : feuille vide, jamais complétée par le groupe (7.2)")
    void emptyRollCallIsNotFilledWithTheGroup() throws Exception {
        // Amine arrive en mars ; la séance de pré-rentrée du 27/08/2029 précède toutes les arrivées.
        jdbc.update("UPDATE student_groups SET date_assigned = ? WHERE student_id = ?",
                java.sql.Timestamp.valueOf(LocalDate.of(2030, 3, 1).atStartOfDay()), student.getId());
        SessionEntity beforeEveryone = sessionRepository.save(SessionEntity.builder()
                .title("Pré-rentrée").group(group).sessionSeries(s1)
                .sessionTimeStart(at(LocalDate.of(2029, 8, 27).atTime(10, 0))).build());

        rollCall(beforeEveryone)
                .andExpect(jsonPath("$.students").value(empty()))
                .andExpect(jsonPath("$.notConcerned[*].lastName")
                        .value(contains("Belkacem", "Haddad", "Kaci", "Zerrouki")));
    }

    @Test
    @DisplayName("groupe sans aucune inscription : feuille vide et rien à expliquer")
    void groupWithoutEnrolments() throws Exception {
        GroupEntity physique = groupRepository.save(GroupEntity.builder()
                .name("Physique 1ère A").price(group.getPrice()).schoolYear(year).sessionNumberPerSerie(2).build());
        SessionEntity session = sessionRepository.save(SessionEntity.builder()
                .title("Physique").group(physique).sessionTimeStart(at(LocalDate.of(2030, 1, 7).atTime(10, 0))).build());

        rollCall(session)
                .andExpect(jsonPath("$.groupId").value(physique.getId()))
                .andExpect(jsonPath("$.students").value(empty()))
                .andExpect(jsonPath("$.notConcerned").value(empty()));
    }

    @Test
    @DisplayName("lecture : un utilisateur VIEWER la consulte")
    void viewerCanRead() throws Exception {
        mockMvc.perform(get("/api/sessions/" + january7.getId() + "/roll-call").with(user("lecteur").roles("VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("séance inconnue : 404")
    void unknownSession() throws Exception {
        mockMvc.perform(get("/api/sessions/999999/roll-call").with(user("directrice").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private ResultActions rollCall(SessionEntity session) throws Exception {
        return mockMvc.perform(get("/api/sessions/" + session.getId() + "/roll-call")
                .with(user("directrice").roles("ADMIN")));
    }

    private SessionEntity sessionOn(com.school.management.persistance.SessionSeriesEntity series, LocalDate day) {
        return sessionRepository.findAll().stream()
                .filter(s -> s.getSessionSeries().getId().equals(series.getId()))
                .filter(s -> LocalDate.ofInstant(s.getSessionTimeStart().toInstant(), ZoneId.systemDefault()).equals(day))
                .findFirst().orElseThrow();
    }

    private StudentEntity newStudent(String firstName, String lastName) {
        return studentRepository.save(StudentEntity.builder().firstName(firstName).lastName(lastName).build());
    }

    private void enrol(StudentEntity who, LocalDate arrival, LocalDate departure) {
        StudentGroupEntity enrolment = studentGroupRepository.save(StudentGroupEntity.builder()
                .student(who).group(group).dateAssigned(at(arrival.atStartOfDay())).build());
        if (departure != null) {
            enrolment.setActive(false);
            enrolment.setDateLeft(at(departure.atStartOfDay()));
            studentGroupRepository.save(enrolment);
        }
    }

    private static Date at(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
