package com.school.management.controller;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inscriptions de bout en bout : date d'arrivée réelle, bornée à l'année scolaire, et départ daté
 * (spec admin-corrections, C.1 et C.2 ; exigences 5.1 à 5.4, D1, D5).
 *
 * <p>Données du socle : l'année 2029-2030 (du 01/09/2029 au 30/06/2030), le groupe « Math 1ère A »
 * où Amine Belkacem est inscrit, et ici un second groupe « Physique 1ère A » de la même année. Les
 * dates sont relues en SQL : c'est ce qui est stocké qui compte, pas ce que l'entité affiche.</p>
 */
@AutoConfigureMockMvc
@DisplayName("Inscriptions : date d'arrivée, bornes de l'année, départ daté")
class StudentGroupEnrolmentEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;

    private GroupEntity physique;

    @BeforeEach
    void secondGroup() {
        physique = newGroup("Physique 1ère A", year);
    }

    // ------------------------------------------------------------------
    // Arrivée
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Date d'arrivée")
    class Arrivee {

        @Test
        @DisplayName("fournie : conservée, à 00:00 du jour — et non écrasée par l'instant de l'écriture (5.1)")
        void providedDateIsKept() throws Exception {
            addGroups(physique, "\"2029-10-15\"").andExpect(status().isOk());

            assertThat(arrivalOf(physique)).isEqualTo(LocalDate.of(2029, 10, 15).atStartOfDay());
        }

        @Test
        @DisplayName("par l'ajout au groupe aussi : même règle sur les deux chemins")
        void providedDateIsKeptWhenAddingToTheGroup() throws Exception {
            addStudents(physique, "\"2029-11-04\"").andExpect(status().isOk());

            assertThat(arrivalOf(physique)).isEqualTo(LocalDate.of(2029, 11, 4).atStartOfDay());
        }

        @Test
        @DisplayName("premier et dernier jour de l'année acceptés : les bornes sont incluses (5.2)")
        void yearBoundsAreIncluded() throws Exception {
            GroupEntity arabe = newGroup("Arabe 1ère A", year);

            addGroups(physique, "\"2029-09-01\"").andExpect(status().isOk());
            addGroups(arabe, "\"2030-06-30\"").andExpect(status().isOk());

            assertThat(arrivalOf(physique)).isEqualTo(LocalDate.of(2029, 9, 1).atStartOfDay());
            assertThat(arrivalOf(arabe)).isEqualTo(LocalDate.of(2030, 6, 30).atStartOfDay());
        }

        @Test
        @DisplayName("hors de l'année : 400 nommant l'année et ses bornes, rien d'inscrit (5.3)")
        void outsideTheYearIsRefused() throws Exception {
            for (String outside : List.of("\"2029-08-31\"", "\"2030-07-01\"")) {
                addGroups(physique, outside)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value(allOf(
                                containsString("hors de l'année scolaire 2029-2030"),
                                containsString("« Physique 1ère A »"),
                                containsString("du 01/09/2029 au 30/06/2030"))));
            }
            addStudents(physique, "\"2030-07-01\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("La date d'arrivée du 01/07/2030")));

            assertThat(enrolmentsIn(physique)).isZero();
        }

        @Test
        @DisplayName("absente : le jour même, à 00:00 (5.1)")
        void missingDateMeansToday() throws Exception {
            LocalDate today = LocalDate.now();
            moveYear(today.minusDays(10), today.plusDays(300));

            addGroups(physique, null).andExpect(status().isOk());

            assertThat(arrivalOf(physique)).isEqualTo(today.atStartOfDay());
        }

        @Test
        @DisplayName("absente alors que l'année n'a pas commencé : 400, le jour même est hors de l'année")
        void missingDateOutsideTheYearIsRefused() throws Exception {
            LocalDate today = LocalDate.now();
            moveYear(today.plusDays(1), today.plusDays(300));

            addGroups(physique, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("hors de l'année scolaire")));
            assertThat(enrolmentsIn(physique)).isZero();
        }

        @Test
        @DisplayName("groupe d'une année close : 409, et non plus 500")
        void closedYearGroupIsAConflict() throws Exception {
            SchoolYearEntity past = schoolYearRepository.save(SchoolYearEntity.builder()
                    .label("2028-2029").startDate(date(2028, 9, 1)).endDate(date(2029, 6, 30))
                    .isCurrent(false).build());
            GroupEntity old = newGroup("Math 1ère A 2028", past);

            addGroups(old, "\"2028-10-01\"").andExpect(status().isConflict());
            assertThat(enrolmentsIn(old)).isZero();
        }

        @Test
        @DisplayName("requête sans groupe ni étudiant : 400, et non plus 500")
        void emptyRequestIsABadRequest() throws Exception {
            send(post("/api/student-groups/" + student.getId() + "/addGroups"), "{\"groupIds\":[]}")
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("enregistrée avec une heure par n'importe quel chemin : stockée au jour")
        void storageKeepsOnlyTheDay() {
            StudentGroupEntity saved = studentGroupRepository.save(StudentGroupEntity.builder()
                    .student(student).group(physique).dateAssigned(at(LocalDate.of(2029, 10, 15), 17, 45)).build());
            assertThat(arrivalOf(physique)).isEqualTo(LocalDate.of(2029, 10, 15).atStartOfDay());

            saved.setDateAssigned(at(LocalDate.of(2029, 10, 20), 9, 10));
            studentGroupRepository.save(saved);
            assertThat(arrivalOf(physique)).isEqualTo(LocalDate.of(2029, 10, 20).atStartOfDay());
        }
    }

    // ------------------------------------------------------------------
    // Retour après un départ
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Retour dans un groupe quitté")
    class Retour {

        @BeforeEach
        void amineLeftOnNovember30() {
            jdbc.update("UPDATE student_groups SET active = FALSE, date_left = ? WHERE group_id = ?",
                    Timestamp.valueOf(LocalDate.of(2029, 11, 30).atStartOfDay()), group.getId());
        }

        @Test
        @DisplayName("arrivée le jour du départ ou avant : 409, les deux fenêtres se recouvriraient")
        void overlappingReturnIsRefused() throws Exception {
            addStudents(group, "\"2029-11-30\"")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(allOf(
                            containsString("Amine Belkacem a déjà été inscrit au groupe « Math 1ère A »"),
                            containsString("postérieure au 30/11/2029"),
                            containsString("rouvrez cette inscription"))));

            assertThat(enrolmentsIn(group)).isEqualTo(1);
        }

        @Test
        @DisplayName("arrivée le lendemain : nouvelle inscription, l'ancienne garde sa fenêtre")
        void laterReturnCreatesANewEnrolment() throws Exception {
            addStudents(group, "\"2029-12-01\"").andExpect(status().isOk());

            assertThat(enrolmentsIn(group)).isEqualTo(2);
            assertThat(arrivalOf(group)).isEqualTo(LocalDate.of(2029, 12, 1).atStartOfDay());
            assertThat(count("SELECT COUNT(*) FROM student_groups WHERE group_id = " + group.getId()
                    + " AND active = FALSE AND date_left IS NOT NULL")).isEqualTo(1);
        }

        @Test
        @DisplayName("un départ dans un autre groupe ne gêne pas l'arrivée")
        void departureFromAnotherGroupDoesNotBlock() throws Exception {
            addGroups(physique, "\"2029-11-15\"").andExpect(status().isOk());
            assertThat(enrolmentsIn(physique)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("membre actif ajouté de nouveau au groupe : rien n'est créé")
    void activeMemberIsNotEnrolledTwice() throws Exception {
        addStudents(group, "\"2029-12-01\"").andExpect(status().isOk());
        assertThat(enrolmentsIn(group)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Départ
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Départ")
    class Depart {

        @Test
        @DisplayName("clôture datée du jour même, à 00:00 (D5)")
        void closureIsDatedToday() throws Exception {
            removeFrom(group).andExpect(status().isOk());

            assertThat(count("SELECT COUNT(*) FROM student_groups WHERE active = TRUE AND group_id = "
                    + group.getId())).isZero();
            Timestamp left = jdbc.queryForObject("SELECT date_left FROM student_groups WHERE group_id = ?",
                    Timestamp.class, group.getId());
            assertThat(left.toLocalDateTime()).isEqualTo(LocalDate.now().atStartOfDay());
        }

        @Test
        @DisplayName("inscription qui n'a pas commencé : 409, elle reste ouverte")
        void notStartedEnrolmentCannotEnd() throws Exception {
            LocalDate arrival = LocalDate.now().plusDays(5);
            jdbc.update("UPDATE student_groups SET date_assigned = ? WHERE group_id = ?",
                    Timestamp.valueOf(arrival.atStartOfDay()), group.getId());

            removeFrom(group)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(allOf(
                            containsString("commence le " + arrival.format(java.time.format.DateTimeFormatter
                                    .ofPattern("dd/MM/yyyy"))),
                            containsString("Corrigez la date d'arrivée"))));

            assertThat(count("SELECT COUNT(*) FROM student_groups WHERE active = TRUE AND date_left IS NULL "
                    + "AND group_id = " + group.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("départ le jour de l'arrivée : accepté, fenêtre d'un jour")
        void departureOnArrivalDay() throws Exception {
            jdbc.update("UPDATE student_groups SET date_assigned = ? WHERE group_id = ?",
                    Timestamp.valueOf(LocalDate.now().atStartOfDay()), group.getId());

            removeFrom(group).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private GroupEntity newGroup(String name, SchoolYearEntity schoolYear) {
        return groupRepository.save(GroupEntity.builder()
                .name(name).price(group.getPrice()).schoolYear(schoolYear).sessionNumberPerSerie(2).build());
    }

    private void moveYear(LocalDate start, LocalDate end) {
        year.setStartDate(Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        year.setEndDate(Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        year = schoolYearRepository.save(year);
    }

    private ResultActions addGroups(GroupEntity target, String dateJson) throws Exception {
        String date = dateJson == null ? "" : ",\"dateAssigned\":" + dateJson;
        return send(post("/api/student-groups/" + student.getId() + "/addGroups"),
                "{\"groupIds\":[" + target.getId() + "]" + date + "}");
    }

    private ResultActions addStudents(GroupEntity target, String dateJson) throws Exception {
        String date = dateJson == null ? "" : ",\"dateAssigned\":" + dateJson;
        return send(post("/api/student-groups/" + target.getId() + "/addStudents"),
                "{\"studentIds\":[" + student.getId() + "]" + date + "}");
    }

    private ResultActions removeFrom(GroupEntity target) throws Exception {
        return mockMvc.perform(delete("/api/student-groups/" + target.getId() + "/students/" + student.getId())
                .with(user("directrice").roles("ADMIN")));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(user("directrice").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Arrivée stockée de l'inscription active d'Amine dans ce groupe. */
    private LocalDateTime arrivalOf(GroupEntity target) {
        return jdbc.queryForObject("SELECT date_assigned FROM student_groups "
                        + "WHERE student_id = ? AND group_id = ? AND active = TRUE",
                Timestamp.class, student.getId(), target.getId()).toLocalDateTime();
    }

    private long enrolmentsIn(GroupEntity target) {
        return count("SELECT COUNT(*) FROM student_groups WHERE group_id = " + target.getId());
    }

    private static Date at(LocalDate day, int hour, int minute) {
        return Date.from(day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant());
    }
}
