package com.school.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.LevelRepository;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Import CSV des inscriptions de bout en bout, sur une base réelle et <strong>sans transaction de
 * test</strong> : une ligne refusée n'annule que sa ligne.
 *
 * <p>Cette classe ne doit jamais devenir {@code @Transactional}. Le piège qu'elle surveille ne se
 * voit que sans transaction englobante : un refus qui traverse le proxy transactionnel de la
 * méthode réutilisée (« Ajouter un étudiant ») marquerait une transaction englobante
 * « rollback-only », et son commit final lèverait {@code UnexpectedRollbackException} — toutes les
 * lignes perdues. Dans une transaction de test, ce commit n'a jamais lieu : le test passerait à
 * tort.</p>
 *
 * <p>Données du socle : l'année courante 2029-2030, le groupe « Math 1ère A » (sans niveau) où
 * Amine Belkacem (sans niveau) est inscrit, avec deux séries de deux séances (7 et 14 janvier,
 * 4 et 11 février 2030). Ajoutés ici : niveaux « 4 AM » et « 1 AS », groupes
 * courants « Physique 4 AM » et « Maths 1 AS », une année close 2028-2029 et son groupe
 * « Anglais 4 AM », l'élève Léa Haddad et deux homonymes Yanis Saidi, tous en 4 AM.</p>
 */
@AutoConfigureMockMvc
@DisplayName("Import CSV des inscriptions : lignes valides enregistrées, lignes refusées rapportées")
class EnrolmentCsvImportIntegrationTest extends CorrectionIntegrationTestSupport {

    /**
     * Lignes 3, 4, 6, 7, 8, 10, 11, 12 et 13 refusées ; 2, 5, 9 et 14 inscrites. La ligne 14 inscrit
     * Léa Haddad le 10 janvier 2030 dans « Math 1ère A », le seul groupe qui a des séances : une
     * avant son arrivée (7 janvier), trois après.
     */
    private static final String FILE = """
            firstName,lastName,group,arrivalDate,phoneNumber
            Léa,Haddad,Physique 4 AM,2029-09-15,
            Léa,Haddad,Maths 1 AS,2029-09-15,
            Amine,Belkacem,Math 1ère A,2029-09-15,
            Amine,Belkacem,Physique 4 AM,2029-10-05,
            Inconnu,Personne,Physique 4 AM,2029-09-15,
            Léa,Haddad,Anglais 4 AM,2029-09-15,
            Yanis,Saidi,Physique 4 AM,2029-09-15,
            Yanis,Saidi,Physique 4 AM,2029-09-15,0550000202
            Yanis,Saidi,Math 1ère A,2030-07-01,0550000201
            Léa,Haddad,Math 1ère A,,
            Léa,Haddad,Math 1ère A,15/09/2029,
            Léa,Haddad,Physique 4 AM,2029-09-15,
            Léa,Haddad,Math 1ère A,2030-01-10,
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LevelRepository levelRepository;

    private final List<Long> createdLevels = new ArrayList<>();

    private GroupEntity physique;
    private GroupEntity maths1As;
    private StudentEntity lea;
    private StudentEntity yanis1;
    private StudentEntity yanis2;

    @BeforeEach
    void enrolmentData() {
        LevelEntity am4 = level("4 AM");
        LevelEntity as1 = level("1 AS");
        physique = newGroup("Physique 4 AM", year, am4);
        maths1As = newGroup("Maths 1 AS", year, as1);
        SchoolYearEntity closed = schoolYearRepository.save(SchoolYearEntity.builder()
                .label("2028-2029").startDate(date(2028, 9, 1)).endDate(date(2029, 6, 30))
                .isCurrent(false).build());
        newGroup("Anglais 4 AM", closed, am4);
        lea = newStudent("Léa", "Haddad", "0550000102", am4);
        yanis1 = newStudent("Yanis", "Saidi", "0550000201", am4);
        yanis2 = newStudent("Yanis", "Saidi", "0550000202", am4);
    }

    /** Rien ne reste dans la base partagée : le socle ne supprime pas les niveaux. */
    @AfterEach
    void removeEnrolmentData() {
        deleteDomainData();
        levelRepository.deleteAllById(createdLevels);
        createdLevels.clear();
    }

    @Test
    @DisplayName("fichier mixte : 4 inscriptions validées en base, 9 refus nommés avec leur ligne, rien d'autre créé")
    void mixedFileKeepsValidLines() throws Exception {
        // Sans séances dans un groupe inscrit, « aucune absence créée » serait vrai à vide.
        assertThat(count("SELECT COUNT(*) FROM session WHERE group_id = " + group.getId())).isEqualTo(4);
        Ledger ledgerBefore = ledger();
        long attendancesBefore = attendanceRepository.count();
        long seriesBefore = seriesRepository.count();
        long sessionsBefore = sessionRepository.count();

        JsonNode result = importFile(user("directrice").roles("ADMIN"), 200);

        assertThat(result.get("imported").asInt()).isEqualTo(4);
        assertThat(result.get("warnings")).isEmpty();
        List<Integer> lines = new ArrayList<>();
        result.get("errors").forEach(error -> lines.add(error.get("line").asInt()));
        assertThat(lines).containsExactly(3, 4, 6, 7, 8, 10, 11, 12, 13);
        assertThat(messageAt(result, 3)).startsWith("Inscription refusée : ")
                .contains("ne peut pas être inscrit au groupe « Maths 1 AS »");
        assertThat(messageAt(result, 4)).startsWith("Déjà inscrit").contains("Math 1ère A");
        assertThat(messageAt(result, 6)).contains("Inconnu Personne");
        assertThat(messageAt(result, 7)).contains("Anglais 4 AM").contains("2029-2030");
        assertThat(messageAt(result, 8)).contains("phoneNumber");
        assertThat(messageAt(result, 10)).startsWith("Inscription refusée : ")
                .contains("hors de l'année scolaire 2029-2030");
        assertThat(messageAt(result, 11)).contains("arrivalDate");
        assertThat(messageAt(result, 12)).contains("AAAA-MM-JJ");
        assertThat(messageAt(result, 13)).startsWith("Déjà inscrit").contains("Physique 4 AM");

        // Relu en SQL, hors de toute transaction : ce qui est validé, et seulement cela.
        assertThat(activeEnrolments(lea, physique)).isEqualTo(1);
        assertThat(arrival(lea, physique)).isEqualTo(LocalDate.of(2029, 9, 15).atStartOfDay());
        assertThat(activeEnrolments(student, physique)).isEqualTo(1);
        assertThat(arrival(student, physique)).isEqualTo(LocalDate.of(2029, 10, 5).atStartOfDay());
        assertThat(activeEnrolments(yanis2, physique)).isEqualTo(1);
        assertThat(activeEnrolments(lea, group)).isEqualTo(1);
        assertThat(arrival(lea, group)).isEqualTo(LocalDate.of(2030, 1, 10).atStartOfDay());
        assertThat(count("SELECT COUNT(*) FROM student_groups WHERE student_id = " + yanis1.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM student_groups WHERE student_id = " + lea.getId())).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM student_groups WHERE group_id = " + maths1As.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM student_groups WHERE student_id = " + student.getId()
                + " AND group_id = " + group.getId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM student_groups")).isEqualTo(5);

        // Ni présence, ni absence, ni paiement, ni série. Léa, inscrite dans un groupe qui a des
        // séances, n'a de fiche de présence ni pour la séance d'avant son arrivée (qui ne la concerne
        // pas), ni pour celles d'après.
        assertThat(ledger()).isEqualTo(ledgerBefore);
        assertThat(count("SELECT COUNT(*) FROM attendance WHERE student_id = " + lea.getId())).isZero();
        assertThat(attendanceRepository.count()).isEqualTo(attendancesBefore);
        assertThat(seriesRepository.count()).isEqualTo(seriesBefore);
        assertThat(sessionRepository.count()).isEqualTo(sessionsBefore);
    }

    @Test
    @DisplayName("réservé à l'ADMIN : VIEWER 403, anonyme 401, aucune inscription créée")
    void adminOnly() throws Exception {
        long enrolmentsBefore = count("SELECT COUNT(*) FROM student_groups");

        importFile(user("secretaire").roles("VIEWER"), 403);
        importFile(anonymous(), 401);

        assertThat(count("SELECT COUNT(*) FROM student_groups")).isEqualTo(enrolmentsBefore);
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private JsonNode importFile(RequestPostProcessor who, int expectedStatus) throws Exception {
        byte[] body = mockMvc.perform(multipart("/api/import/enrolments")
                        .file(new MockMultipartFile("file", "9-inscriptions.csv", "text/csv",
                                FILE.getBytes(StandardCharsets.UTF_8)))
                        .with(who))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsByteArray();
        return expectedStatus == 200 ? objectMapper.readTree(new String(body, StandardCharsets.UTF_8)) : null;
    }

    private static String messageAt(JsonNode result, int line) {
        for (JsonNode error : result.get("errors")) {
            if (error.get("line").asInt() == line) {
                return error.get("message").asText();
            }
        }
        throw new AssertionError("aucune erreur en ligne " + line);
    }

    private LevelEntity level(String name) {
        LevelEntity level = LevelEntity.builder().build();
        level.setName(name);
        level = levelRepository.save(level);
        createdLevels.add(level.getId());
        return level;
    }

    private GroupEntity newGroup(String name, SchoolYearEntity schoolYear, LevelEntity level) {
        return groupRepository.save(GroupEntity.builder().name(name).price(group.getPrice())
                .schoolYear(schoolYear).level(level).sessionNumberPerSerie(2).build());
    }

    private StudentEntity newStudent(String firstName, String lastName, String phone, LevelEntity level) {
        return studentRepository.save(StudentEntity.builder().firstName(firstName).lastName(lastName)
                .phoneNumber(phone).level(level).build());
    }

    private long activeEnrolments(StudentEntity who, GroupEntity target) {
        return count("SELECT COUNT(*) FROM student_groups WHERE active = TRUE AND date_left IS NULL"
                + " AND student_id = " + who.getId() + " AND group_id = " + target.getId());
    }

    private java.time.LocalDateTime arrival(StudentEntity who, GroupEntity target) {
        return jdbc.queryForObject("SELECT date_assigned FROM student_groups "
                        + "WHERE student_id = ? AND group_id = ? AND active = TRUE",
                Timestamp.class, who.getId(), target.getId()).toLocalDateTime();
    }
}
