package com.school.management.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.GroupDTO;
import com.school.management.dto.SchoolYearDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.GroupTypeEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.persistance.SubjectEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.GroupTypeRepository;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.SubjectRepository;
import com.school.management.repository.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Calendar;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de bout en bout (Spring Boot Test, H2 en mémoire) du <em>câblage</em>
 * des points d'entrée REST de la fonctionnalité « année scolaire ».
 *
 * <p>Contrairement aux tests {@code @WebMvcTest} (couche web isolée, services simulés) et
 * {@code @DataJpaTest} (couche dépôt isolée), ce test charge le <strong>contexte complet</strong>
 * ({@code @SpringBootTest}) et exerce la chaîne réelle contrôleur → service → dépôt → H2 via
 * {@link MockMvc}. Il vérifie que les points d'entrée sont correctement câblés et renvoient les
 * bons statuts HTTP et champs de réponse :</p>
 *
 * <ul>
 *   <li>CRUD des années scolaires (Exigence 1.5) ;</li>
 *   <li>désignation de l'année courante et invariant « une seule courante » (Exigences 2.1, 2.2) ;</li>
 *   <li>consultation de l'année courante (Exigence 2.5) ;</li>
 *   <li>réponse en l'absence d'année courante — HTTP 404 (Exigence 13.1) ;</li>
 *   <li>point d'entrée du parcours étudiant (Exigence 11.5) ;</li>
 *   <li>rejet d'une modification sur une année passée — HTTP 409 (Exigence 9.2) ;</li>
 *   <li>lecture autorisée sur n'importe quelle année — HTTP 200 (Exigence 9.3).</li>
 * </ul>
 *
 * <p><strong>Déterminisme et {@code SchoolYearMigrationRunner}</strong> : ce runner est un
 * {@code ApplicationRunner} qui, au démarrage du contexte, crée une année scolaire initiale
 * courante. Comme {@code @SpringBootTest} n'enveloppe pas chaque test dans une transaction
 * annulée, cet état persisterait entre les tests. On vide donc explicitement la base avant
 * chaque test ({@link #resetDatabase()}) puis on sème exactement les données nécessaires, afin
 * que chaque scénario soit parfaitement déterministe (en particulier le scénario « aucune année
 * courante »).</p>
 *
 * <p>Le {@code @TestPropertySource} force une source de données H2 et un schéma généré par
 * Hibernate ({@code create-drop}), sans conflit avec la configuration PostgreSQL du module
 * principal. La sécurité autorise toutes les requêtes ({@code SecurityConfig}), aucun filtre
 * n'a donc besoin d'être désactivé.</p>
 */
@SpringBootTest
// Filtres de sécurité désactivés : ce test valide le câblage des endpoints (statuts/champs),
// pas l'autorisation (couverte par les tests dédiés de la feature authentication-authorization).
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:schoolyear-wiring;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class SchoolYearEndpointWiringIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SchoolYearRepository schoolYearRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private StudentGroupRepository studentGroupRepository;
    @Autowired
    private LevelRepository levelRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private PricingRepository pricingRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private GroupTypeRepository groupTypeRepository;

    /**
     * Vide la base avant chaque test pour neutraliser l'année initiale créée au démarrage par
     * {@link com.school.management.service.SchoolYearMigrationRunner} et garantir un état de
     * départ déterministe. L'ordre de suppression respecte les contraintes de clés étrangères
     * (inscriptions → groupes/étudiants → entités de référence → années scolaires).
     */
    @BeforeEach
    void resetDatabase() {
        studentGroupRepository.deleteAll();
        groupRepository.deleteAll();
        studentRepository.deleteAll();
        levelRepository.deleteAll();
        subjectRepository.deleteAll();
        pricingRepository.deleteAll();
        teacherRepository.deleteAll();
        groupTypeRepository.deleteAll();
        schoolYearRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Fabriques et utilitaires
    // ------------------------------------------------------------------

    /** Construit une {@link Date} (année, mois base 1, jour) sans composante horaire. */
    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }

    /** Persiste une année scolaire directement (sans passer par le point d'entrée). */
    private SchoolYearEntity persistSchoolYear(String label, Date start, Date end, boolean current) {
        return schoolYearRepository.save(SchoolYearEntity.builder()
                .label(label)
                .startDate(start)
                .endDate(end)
                .isCurrent(current)
                .build());
    }

    /** Persiste un niveau avec son rang de séquence. */
    private LevelEntity persistLevel(String name, int sequence) {
        return levelRepository.save(LevelEntity.builder()
                .name(name)
                .levelCode(name)
                .levelSequence(sequence)
                .build());
    }

    /** Persiste un groupe rattaché à une année et un niveau donnés. */
    private GroupEntity persistGroup(String name, SchoolYearEntity year, LevelEntity level) {
        return groupRepository.save(GroupEntity.builder()
                .name(name)
                .schoolYear(year)
                .level(level)
                .build());
    }

    /** Persiste un étudiant actif. */
    private StudentEntity persistStudent(String firstName) {
        return studentRepository.save(StudentEntity.builder()
                .firstName(firstName)
                .lastName("Test")
                .build());
    }

    /** Persiste une inscription active (student_groups) reliant un étudiant à un groupe. */
    private void persistEnrollment(StudentEntity student, GroupEntity group) {
        studentGroupRepository.save(StudentGroupEntity.builder()
                .student(student)
                .group(group)
                .build());
    }

    // ==================================================================
    // CRUD des années scolaires (Exigence 1.5)
    // ==================================================================
    @Nested
    @DisplayName("CRUD des années scolaires (Exigence 1.5)")
    class SchoolYearCrud {

        @Test
        @DisplayName("POST crée une année scolaire ; la première créée devient courante (201)")
        void postCreatesSchoolYear() throws Exception {
            SchoolYearDTO body = SchoolYearDTO.builder()
                    .label("2025-2026")
                    .startDate(date(2025, 9, 1))
                    .endDate(date(2026, 6, 30))
                    .build();

            mockMvc.perform(post("/api/school-years")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.label").value("2025-2026"))
                    // Première année créée : elle devient l'année courante (Exigence 2.3).
                    .andExpect(jsonPath("$.isCurrent").value(true));
        }

        @Test
        @DisplayName("GET liste toutes les années, triées par date de début décroissante (1.6)")
        void getListsOrderedByStartDateDesc() throws Exception {
            persistSchoolYear("2024-2025", date(2024, 9, 1), date(2025, 6, 30), true);
            persistSchoolYear("2025-2026", date(2025, 9, 1), date(2026, 6, 30), false);

            mockMvc.perform(get("/api/school-years"))
                    .andExpect(status().isOk())
                    // La plus récente (date de début la plus grande) en premier.
                    .andExpect(jsonPath("$[0].label").value("2025-2026"))
                    .andExpect(jsonPath("$[1].label").value("2024-2025"));
        }

        @Test
        @DisplayName("GET /{id} récupère une année scolaire par son identifiant (200)")
        void getByIdReturnsSchoolYear() throws Exception {
            SchoolYearEntity year =
                    persistSchoolYear("2025-2026", date(2025, 9, 1), date(2026, 6, 30), true);

            mockMvc.perform(get("/api/school-years/{id}", year.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(year.getId().intValue()))
                    .andExpect(jsonPath("$.label").value("2025-2026"));
        }
    }

    // ==================================================================
    // Désignation de l'année courante — invariant « une seule courante »
    // (Exigences 2.1, 2.2)
    // ==================================================================
    @Nested
    @DisplayName("set-current : invariant d'une seule année courante (Exigences 2.1, 2.2)")
    class SetCurrent {

        @Test
        @DisplayName("PATCH set-current bascule la cible en courante et l'ancienne à false")
        void patchSetCurrentEnforcesSingleCurrent() throws Exception {
            SchoolYearEntity previous =
                    persistSchoolYear("2024-2025", date(2024, 9, 1), date(2025, 6, 30), true);
            SchoolYearEntity target =
                    persistSchoolYear("2025-2026", date(2025, 9, 1), date(2026, 6, 30), false);

            // La cible devient courante (Exigence 2.1).
            mockMvc.perform(patch("/api/school-years/{id}/set-current", target.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(target.getId().intValue()))
                    .andExpect(jsonPath("$.isCurrent").value(true));

            // L'année précédemment courante n'est plus courante (Exigence 2.2).
            mockMvc.perform(get("/api/school-years/{id}", previous.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isCurrent").value(false));

            // La cible reste bien la seule année courante.
            mockMvc.perform(get("/api/school-years/{id}", target.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isCurrent").value(true));
        }
    }

    // ==================================================================
    // Consultation de l'année courante (Exigence 2.5) et absence
    // d'année courante (Exigence 13.1)
    // ==================================================================
    @Nested
    @DisplayName("GET /current : année courante (2.5) et absence d'année courante (13.1)")
    class CurrentLookup {

        @Test
        @DisplayName("GET /current renvoie l'année courante lorsqu'elle existe (200)")
        void getCurrentReturnsCurrentYear() throws Exception {
            persistSchoolYear("2024-2025", date(2024, 9, 1), date(2025, 6, 30), false);
            persistSchoolYear("2025-2026", date(2025, 9, 1), date(2026, 6, 30), true);

            mockMvc.perform(get("/api/school-years/current"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.label").value("2025-2026"))
                    .andExpect(jsonPath("$.isCurrent").value(true));
        }

        @Test
        @DisplayName("GET /current sans année courante renvoie 404 « aucune année définie » (13.1)")
        void getCurrentWithoutCurrentYearReturns404() throws Exception {
            // Base vidée par resetDatabase() : aucune année scolaire n'existe.
            mockMvc.perform(get("/api/school-years/current"))
                    // NoCurrentSchoolYearException est mappée en HTTP 404 par le
                    // GlobalExceptionHandler (via CustomServiceException.getStatus()).
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Aucune année scolaire courante définie."));
        }
    }

    // ==================================================================
    // Point d'entrée du parcours étudiant (Exigence 11.5)
    // ==================================================================
    @Nested
    @DisplayName("GET /api/students/{id}/parcours (Exigence 11.5)")
    class ParcoursEndpoint {

        @Test
        @DisplayName("Le parcours expose une entrée par année fréquentée, triée décroissante")
        void parcoursReturnsPerYearStructure() throws Exception {
            // Deux années scolaires, un groupe (donc un niveau) dans chacune.
            SchoolYearEntity year2024 =
                    persistSchoolYear("2024-2025", date(2024, 9, 1), date(2025, 6, 30), false);
            SchoolYearEntity year2025 =
                    persistSchoolYear("2025-2026", date(2025, 9, 1), date(2026, 6, 30), true);

            LevelEntity niveau2 = persistLevel("2ème", 2);
            LevelEntity niveau3 = persistLevel("3ème", 3);

            GroupEntity group2024 = persistGroup("G-2024", year2024, niveau2);
            GroupEntity group2025 = persistGroup("G-2025", year2025, niveau3);

            StudentEntity student = persistStudent("Amina");
            persistEnrollment(student, group2024);
            persistEnrollment(student, group2025);

            mockMvc.perform(get("/api/students/{id}/parcours", student.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.studentId").value(student.getId().intValue()))
                    // Deux années fréquentées, triées par date de début décroissante (11.3).
                    .andExpect(jsonPath("$.years.length()").value(2))
                    .andExpect(jsonPath("$.years[0].schoolYearLabel").value("2025-2026"))
                    .andExpect(jsonPath("$.years[0].levels[0].name").value("3ème"))
                    .andExpect(jsonPath("$.years[1].schoolYearLabel").value("2024-2025"))
                    .andExpect(jsonPath("$.years[1].levels[0].name").value("2ème"));
        }
    }

    // ==================================================================
    // Lecture seule des années passées : rejet des modifications (9.2)
    // et lecture autorisée sur n'importe quelle année (9.3)
    // ==================================================================
    @Nested
    @DisplayName("Années passées : rejet des modifications (9.2) et lecture autorisée (9.3)")
    class ReadOnlyPastYear {

        private SchoolYearEntity pastYear;
        private SchoolYearEntity currentYear;
        private GroupEntity pastGroup;
        private Long groupTypeId;
        private Long subjectId;
        private Long priceId;
        private Long teacherId;
        private Long levelId;

        /** Sème une année passée portant un groupe complet, et une année courante distincte. */
        private void seedPastGroup() {
            pastYear = persistSchoolYear("2024-2025", date(2024, 9, 1), date(2025, 6, 30), false);
            currentYear = persistSchoolYear("2025-2026", date(2025, 9, 1), date(2026, 6, 30), true);

            GroupTypeEntity groupType = groupTypeRepository.save(
                    GroupTypeEntity.builder().name("Petit").size(10).build());
            SubjectEntity subject = subjectRepository.save(
                    SubjectEntity.builder().name("Maths").build());
            PricingEntity price = pricingRepository.save(
                    PricingEntity.builder().price(30.0).build());
            TeacherEntity teacher = teacherRepository.save(
                    TeacherEntity.builder().firstName("Prof").lastName("Test").build());
            LevelEntity level = persistLevel("2ème", 2);

            groupTypeId = groupType.getId();
            subjectId = subject.getId();
            priceId = price.getId();
            teacherId = teacher.getId();
            levelId = level.getId();

            pastGroup = groupRepository.save(GroupEntity.builder()
                    .name("Groupe passé")
                    .schoolYear(pastYear)
                    .level(level)
                    .groupType(groupType)
                    .subject(subject)
                    .price(price)
                    .teacher(teacher)
                    .sessionNumberPerSerie(8)
                    .build());
        }

        @Test
        @DisplayName("PUT sur un groupe d'une année passée est rejeté en HTTP 409 (9.2)")
        void updatingPastYearGroupReturns409() throws Exception {
            seedPastGroup();

            // Corps valide (toutes les références existent) afin d'atteindre le garde
            // lecture seule plutôt qu'une erreur de validation ou de mapping.
            GroupDTO body = GroupDTO.builder()
                    .name("Groupe passé modifié")
                    .groupTypeId(groupTypeId)
                    .levelId(levelId)
                    .subjectId(subjectId)
                    .priceId(priceId)
                    .teacherId(teacherId)
                    .sessionNumberPerSerie(8)
                    .build();

            mockMvc.perform(put("/api/groups/{id}", pastGroup.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    // ReadOnlySchoolYearException est mappée en HTTP 409 par le
                    // GlobalExceptionHandler (Exigence 9.2).
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "Cette année scolaire est en lecture seule : modification interdite."));
        }

        @Test
        @DisplayName("GET des groupes d'une année passée est autorisé en HTTP 200 (9.3)")
        void readingPastYearGroupsReturns200() throws Exception {
            seedPastGroup();

            // La lecture ne consulte jamais le garde : les données d'une année passée
            // restent entièrement consultables (Exigence 9.3).
            mockMvc.perform(get("/api/groups").param("schoolYearId", pastYear.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Groupe passé"))
                    .andExpect(jsonPath("$[0].schoolYearId").value(pastYear.getId().intValue()));
        }
    }
}
