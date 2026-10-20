package com.school.management.controller;

import com.jayway.jsonpath.JsonPath;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.StudentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La date de naissance est un jour du calendrier, pas un instant.
 *
 * <p>Elle transitait en {@code java.util.Date} : sérialisée en millisecondes depuis 1970 (à cause
 * de {@code @EnableWebMvc}), reconstruite en date SQL dans le fuseau de la JVM, elle pouvait
 * reculer d'un jour selon le fuseau du lecteur. Et l'édition d'un élève sans date de naissance
 * (tout élève importé par CSV) était refusée en 400 par un {@code @NotNull} du DTO. Ce test fixe le
 * contrat : la chaîne {@code "yyyy-MM-dd"} entre et sort telle quelle, la colonne contient exactement
 * le jour saisi, une date absente reste absente, une date non envoyée est conservée.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:birth-date;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "school.year.initial-label=2026-2027"
})
@DisplayName("Date de naissance : jour du calendrier, de bout en bout")
class StudentBirthDateIntegrationTest {

    private static final LocalDate STUDENT_BIRTH = LocalDate.of(2010, 5, 15);
    private static final LocalDate TEACHER_BIRTH = LocalDate.of(1985, 2, 20);

    @Autowired private MockMvc mockMvc;
    @Autowired private LevelRepository levels;
    @Autowired private StudentRepository students;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private LevelEntity level;

    @BeforeEach
    void niveau() {
        LevelEntity entity = LevelEntity.builder().build();
        entity.setName("4 AM");
        level = levels.save(entity);
    }

    /** Écrit en base ce que la session a en mémoire, puis l'oublie : la relecture vient de la base. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private LocalDate studentColumn(long id) {
        flushAndClear();
        return jdbc.queryForObject("select date_of_birth from student where id = ?", LocalDate.class, id);
    }

    private LocalDate teacherColumn(long id) {
        flushAndClear();
        return jdbc.queryForObject("select date_of_birth from teacher where id = ?", LocalDate.class, id);
    }

    private long savedStudent(LocalDate dateOfBirth) {
        StudentEntity student = StudentEntity.builder().build();
        student.setFirstName("Léa");
        student.setLastName("Haddad");
        student.setGender("F");
        student.setLevel(level);
        student.setActive(true);
        long id = students.save(student).getId();
        flushAndClear();
        // Écrite directement dans la colonne : l'état de départ ne dépend pas du type Java du champ.
        jdbc.update("update student set date_of_birth = ? where id = ?", dateOfBirth, id);
        return id;
    }

    private String studentJson(String firstName, String dateOfBirthProperty) {
        return "{\"firstName\":\"" + firstName + "\",\"lastName\":\"Haddad\",\"gender\":\"F\","
                + "\"levelId\":" + level.getId()
                + (dateOfBirthProperty == null ? "" : ",\"dateOfBirth\":" + dateOfBirthProperty)
                + "}";
    }

    @Test
    @DisplayName("création multipart : réponse, relecture et colonne = 2010-05-15")
    void createStudent() throws Exception {
        String body = mockMvc.perform(multipart("/api/students/createStudent")
                        .param("firstName", "Léa")
                        .param("lastName", "Haddad")
                        .param("gender", "F")
                        .param("levelId", String.valueOf(level.getId()))
                        .param("dateOfBirth", "2010-05-15")
                        .with(user("directrice").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").value("2010-05-15"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(get("/api/students/id/{id}", id).with(user("directrice").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").value("2010-05-15"));
        assertThat(studentColumn(id)).isEqualTo(STUDENT_BIRTH);
    }

    @Test
    @DisplayName("édition JSON avec \"2010-05-15\" : 200, relecture et colonne = 2010-05-15")
    void updateStudentWithDate() throws Exception {
        long id = savedStudent(null);

        mockMvc.perform(put("/api/students/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentJson("Léa", "\"2010-05-15\""))
                        .with(user("directrice").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").value("2010-05-15"));

        mockMvc.perform(get("/api/students/id/{id}", id).with(user("directrice").roles("ADMIN")))
                .andExpect(jsonPath("$.dateOfBirth").value("2010-05-15"));
        assertThat(studentColumn(id)).isEqualTo(STUDENT_BIRTH);
    }

    @Test
    @DisplayName("élève sans date de naissance (import CSV) : éditable (200), reste sans date")
    void updateStudentWithoutDate() throws Exception {
        long id = savedStudent(null);

        mockMvc.perform(put("/api/students/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentJson("Lina", null))
                        .with(user("directrice").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Lina"));

        assertThat(studentColumn(id)).isNull();
    }

    @Test
    @DisplayName("édition sans la propriété dateOfBirth : la date enregistrée est conservée")
    void updateStudentKeepsUntouchedDate() throws Exception {
        long id = savedStudent(STUDENT_BIRTH);

        mockMvc.perform(put("/api/students/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentJson("Lina", null))
                        .with(user("directrice").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").value("2010-05-15"));

        assertThat(studentColumn(id)).isEqualTo(STUDENT_BIRTH);
    }

    @Test
    @DisplayName("enseignant : création multipart et édition JSON gardent 1985-02-20")
    void teacher() throws Exception {
        String body = mockMvc.perform(multipart("/api/teachers/createTeacher")
                        .param("firstName", "Karim")
                        .param("lastName", "Benali")
                        .param("gender", "M")
                        .param("phoneNumber", "0550123456")
                        .param("dateOfBirth", "1985-02-20")
                        .with(user("directrice").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").value("1985-02-20"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(teacherColumn(id)).isEqualTo(TEACHER_BIRTH);

        mockMvc.perform(put("/api/teachers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Karim\",\"lastName\":\"Benali\",\"gender\":\"M\","
                                + "\"phoneNumber\":\"0550123456\",\"dateOfBirth\":\"1985-02-20\"}")
                        .with(user("directrice").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateOfBirth").value("1985-02-20"));

        mockMvc.perform(get("/api/teachers/id/{id}", id).with(user("directrice").roles("ADMIN")))
                .andExpect(jsonPath("$.dateOfBirth").value("1985-02-20"));
        assertThat(teacherColumn(id)).isEqualTo(TEACHER_BIRTH);
    }
}
