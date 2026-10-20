package com.school.management.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.GroupTypeRepository;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.RoomRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.SubjectRepository;
import com.school.management.repository.TeacherRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le jeu d'essai livré ({@code sample-csv/algerie/}) s'importe tel quel, de bout en bout.
 *
 * <p>Les fichiers sont enregistrés en UTF-8 <strong>avec BOM</strong> : sans lui, Excel sous
 * Windows les ouvre en Windows-1252 et affiche « LÃ©a » au lieu de « Léa ». Mais un BOM non retiré
 * colle à la première colonne de l'en-tête : « firstName » devient « \uFEFFfirstName », la colonne
 * n'est plus reconnue, et chaque ligne est refusée. Ce test envoie les vrais octets des fichiers
 * par le point d'entrée HTTP, dans l'ordre d'import, puis relit chaque ligne en base et par l'API :
 * toutes les colonnes renseignées, accents intacts.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:csv-sample-import;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        // Année courante du jeu d'essai, fixée : dérivée de l'horloge système, elle changerait en
        // septembre 2027 et les dates d'arrivée de 9-inscriptions.csv sortiraient de l'année.
        "school.year.initial-label=2026-2027"
})
@DisplayName("Jeu d'essai CSV (UTF-8 avec BOM) importé de bout en bout")
class SampleCsvImportIntegrationTest {

    /** Répertoire du jeu d'essai, relatif au module (répertoire de travail de Maven). */
    private static final Path SAMPLES = Path.of("sample-csv", "algerie");

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LevelRepository levels;
    @Autowired private SubjectRepository subjects;
    @Autowired private RoomRepository rooms;
    @Autowired private GroupTypeRepository groupTypes;
    @Autowired private PricingRepository prices;
    @Autowired private TeacherRepository teachers;
    @Autowired private StudentRepository students;
    @Autowired private GroupRepository groups;
    @Autowired private StudentGroupRepository enrolments;
    @Autowired private AttendanceRepository attendances;
    @Autowired private PaymentRepository payments;

    @Test
    @DisplayName("les neuf fichiers, dans l'ordre : tout importé, chaque colonne de chaque ligne retrouvée")
    void wholeSampleImports() throws Exception {
        importFile("levels", "1-niveaux.csv", 4);
        importFile("subjects", "2-matieres.csv", 8);
        importFile("rooms", "3-salles.csv", 6);
        importFile("group-types", "4-types-de-groupe.csv", 4);
        importFile("pricing", "5-tarifs.csv", 5);
        importFile("teachers", "6-enseignants.csv", 9);
        importFile("students", "7-eleves.csv", 40);
        importFile("groups", "8-groupes.csv", 16);
        importFile("enrolments", "9-inscriptions.csv", 69);

        // Première colonne de chaque fichier, celle qu'un BOM corromprait : relue telle quelle.
        assertThat(levels.findAll()).extracting("name").containsExactlyInAnyOrderElementsOf(column("1-niveaux.csv", "name"));
        assertThat(subjects.findAll()).extracting("name").containsExactlyInAnyOrderElementsOf(column("2-matieres.csv", "name"));
        assertThat(rooms.findAll()).extracting("name").containsExactlyInAnyOrderElementsOf(column("3-salles.csv", "name"));
        assertThat(groupTypes.findAll()).extracting("name").containsExactlyInAnyOrderElementsOf(column("4-types-de-groupe.csv", "name"));
        assertThat(prices.findAll()).extracting(p -> String.valueOf(p.getPrice().intValue()))
                .containsExactlyInAnyOrderElementsOf(column("5-tarifs.csv", "price"));
        assertThat(levels.findByName("1 AS").orElseThrow())
                .satisfies(level -> {
                    assertThat(level.getLevelCode()).isEqualTo("1AS");
                    assertThat(level.getLevelSequence()).isEqualTo(2);
                });

        Map<String, TeacherEntity> teacherByName = teachers.findAll().stream()
                .collect(Collectors.toMap(t -> t.getFirstName() + " " + t.getLastName(), Function.identity()));
        for (Map<String, String> row : rows("6-enseignants.csv")) {
            TeacherEntity teacher = teacherByName.get(row.get("firstName") + " " + row.get("lastName"));
            assertThat(teacher).as("enseignant %s %s", row.get("firstName"), row.get("lastName")).isNotNull();
            assertThat(teacher.getSpecialization()).isEqualTo(row.get("specialization"));
            assertThat(teacher.getPhoneNumber()).isEqualTo(row.get("phoneNumber"));
            assertThat(teacher.getEmail()).isEqualTo(row.get("email"));
        }

        Map<String, StudentEntity> studentByName = students.findAll().stream()
                .collect(Collectors.toMap(s -> s.getFirstName() + " " + s.getLastName(), Function.identity()));
        for (Map<String, String> row : rows("7-eleves.csv")) {
            StudentEntity student = studentByName.get(row.get("firstName") + " " + row.get("lastName"));
            assertThat(student).as("élève %s %s", row.get("firstName"), row.get("lastName")).isNotNull();
            assertThat(student.getGender()).isEqualTo(row.get("gender"));
            assertThat(student.getLevel().getName()).isEqualTo(row.get("level"));
            assertThat(student.getEstablishment()).isEqualTo(emptyToNull(row.get("establishment")));
            assertThat(student.getPhoneNumber()).isEqualTo(emptyToNull(row.get("phoneNumber")));
            assertThat(student.getDateOfBirth()).isEqualTo(emptyToNull(row.get("dateOfBirth")) == null
                    ? null : LocalDate.parse(row.get("dateOfBirth")));
        }

        Map<String, GroupEntity> groupByName = groups.findAll().stream()
                .collect(Collectors.toMap(GroupEntity::getName, Function.identity()));
        for (Map<String, String> row : rows("8-groupes.csv")) {
            GroupEntity group = groupByName.get(row.get("name"));
            assertThat(group).as("groupe %s", row.get("name")).isNotNull();
            assertThat(group.getGroupType().getName()).isEqualTo(row.get("groupType"));
            assertThat(group.getLevel().getName()).isEqualTo(row.get("level"));
            assertThat(group.getSubject().getName()).isEqualTo(row.get("subject"));
            assertThat(group.getTeacher().getFirstName()).isEqualTo(row.get("teacherFirstName"));
            assertThat(group.getTeacher().getLastName()).isEqualTo(row.get("teacherLastName"));
            assertThat(group.getSessionNumberPerSerie()).isEqualTo(Integer.parseInt(row.get("sessionNumberPerSerie")));
            assertThat(group.getPrice().getPrice().intValue()).isEqualTo(Integer.parseInt(row.get("price")));
        }

        // Chaque inscription du fichier : ouverte, à la date d'arrivée du fichier.
        List<Map<String, String>> enrolmentRows = rows("9-inscriptions.csv");
        for (Map<String, String> row : enrolmentRows) {
            String who = row.get("firstName") + " " + row.get("lastName");
            StudentGroupEntity enrolment = enrolments.findByGroupIdAndStudentIdAndActiveTrue(
                            groupByName.get(row.get("group")).getId(), studentByName.get(who).getId())
                    .orElseThrow(() -> new AssertionError("inscription de " + who + " à " + row.get("group")));
            assertThat(EnrolmentWindow.dayOf(enrolment.getDateAssigned()))
                    .as("arrivée de %s à %s", who, row.get("group"))
                    .isEqualTo(LocalDate.parse(row.get("arrivalDate")));
            assertThat(enrolment.getDateLeft()).as("départ de %s à %s", who, row.get("group")).isNull();
        }
        assertThat(enrolments.count()).isEqualTo(enrolmentRows.size()).isEqualTo(69);
        // L'import crée des inscriptions, et rien d'autre.
        assertThat(attendances.count()).isZero();
        assertThat(payments.count()).isZero();

        // Aucune trace d'un BOM ni d'un caractère de remplacement (accent mal décodé) dans les noms.
        List<String> names = new ArrayList<>();
        students.findAll().forEach(s -> names.addAll(Arrays.asList(s.getFirstName(), s.getLastName(), s.getEstablishment())));
        teachers.findAll().forEach(t -> names.addAll(Arrays.asList(t.getFirstName(), t.getLastName(), t.getSpecialization())));
        subjects.findAll().forEach(s -> names.add(s.getName()));
        assertThat(names).filteredOn(name -> name != null && (name.indexOf('\uFEFF') >= 0 || name.indexOf('\uFFFD') >= 0))
                .isEmpty();
    }

    @Test
    @DisplayName("relus par l'API, comme l'écran les reçoit : accents intacts")
    void accentsReachTheApi() throws Exception {
        importFile("levels", "1-niveaux.csv", 4);
        importFile("students", "7-eleves.csv", 40);

        byte[] body = mockMvc.perform(get("/api/students").with(user("directrice").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        // Le navigateur décode une réponse JSON en UTF-8 : on fait de même.
        JsonNode list = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
        Map<String, JsonNode> byName = new java.util.HashMap<>();
        list.forEach(node -> byName.put(node.get("firstName").asText() + " " + node.get("lastName").asText(), node));

        assertThat(byName).containsKeys("Léa Haddad", "Chloé Brahimi", "Théo Lounis", "Raphaël Lahlou",
                "Chaïma Nedjar", "Inès Rezki");
        assertThat(byName.get("Théo Lounis").get("establishment").asText()).isEqualTo("Lycée Émir Abdelkader");
        assertThat(byName.get("Léa Haddad").get("gender").asText()).isEqualTo("F");
        assertThat(byName.get("Léa Haddad").get("phoneNumber").asText()).isEqualTo("0550000102");
        // Jour du calendrier, en chaîne yyyy-MM-dd : ni nombre de millisecondes, ni tableau.
        String leaBirth = rows("7-eleves.csv").stream()
                .filter(row -> "Léa".equals(row.get("firstName")) && "Haddad".equals(row.get("lastName")))
                .findFirst().orElseThrow().get("dateOfBirth");
        assertThat(byName.get("Léa Haddad").get("dateOfBirth").asText()).isEqualTo(leaBirth);
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /** Envoie le fichier tel qu'il est sur le disque, et exige un import complet, sans erreur ni réserve. */
    private void importFile(String kind, String fileName, int expected) throws Exception {
        byte[] bytes = Files.readAllBytes(SAMPLES.resolve(fileName));
        assertThat(Arrays.copyOf(bytes, 3)).as("%s commence par un BOM UTF-8", fileName).isEqualTo(UTF8_BOM);

        byte[] response = mockMvc.perform(multipart("/api/import/" + kind)
                        .file(new MockMultipartFile("file", fileName, "text/csv", bytes))
                        .with(user("directrice").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        JsonNode result = objectMapper.readTree(new String(response, StandardCharsets.UTF_8));
        assertThat(result.get("errors")).as("%s : erreurs", fileName).isEmpty();
        assertThat(result.get("warnings")).as("%s : avertissements", fileName).isEmpty();
        assertThat(result.get("imported").asInt()).as("%s : lignes importées", fileName).isEqualTo(expected);
    }

    /**
     * Lignes du fichier, colonne par colonne, lues indépendamment du code testé : BOM retiré ici,
     * et décodage UTF-8 strict (un octet invalide fait échouer le test au lieu d'un « � » discret).
     */
    private static List<Map<String, String>> rows(String fileName) throws IOException {
        byte[] bytes = Files.readAllBytes(SAMPLES.resolve(fileName));
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 3, bytes.length - 3)).toString();
        } catch (CharacterCodingException e) {
            throw new AssertionError(fileName + " n'est pas de l'UTF-8 valide", e);
        }
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        String[] header = lines.get(0).split(",", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",", -1);
            Map<String, String> row = new java.util.LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) {
                row.put(header[i], i < cells.length ? cells[i] : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> column(String fileName, String name) throws IOException {
        return rows(fileName).stream().map(row -> row.get(name)).toList();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
