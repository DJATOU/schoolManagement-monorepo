package com.school.management.mapper;

import com.school.management.dto.StudentDTO;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.TutorRepository;
import com.school.management.shared.mapper.MappingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests unitaires du {@link StudentMapper} centrés sur le statut d'inscription
 * (ACTIVE / INACTIVE) — Exigence 7.1.
 *
 * <p>Vérifie que le champ {@code status} est transporté dans les deux directions
 * de mapping (entité → DTO et DTO → entité).</p>
 */
class StudentMapperTest {

    private final StudentMapper mapper = new StudentMapperImpl();

    private LevelRepository levelRepository;
    private TutorRepository tutorRepository;
    private MappingContext context;

    @BeforeEach
    void setUp() {
        levelRepository = mock(LevelRepository.class);
        tutorRepository = mock(TutorRepository.class);
        context = MappingContext.forStudent(levelRepository, tutorRepository);
    }

    // ------------------------------------------------------------------
    // Entité → DTO : le statut est transporté
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(StudentStatus.class)
    void toDto_carriesStatus(StudentStatus status) {
        StudentEntity entity = StudentEntity.builder()
                .status(status)
                .build();
        entity.setId(1L);

        StudentDTO dto = mapper.studentToStudentDTO(entity);

        assertThat(dto.getStatus()).isEqualTo(status);
    }

    // ------------------------------------------------------------------
    // DTO → entité : le statut est transporté
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(StudentStatus.class)
    void toEntity_carriesStatus(StudentStatus status) {
        StudentDTO dto = StudentDTO.builder()
                .firstName("Amina")
                .lastName("Benali")
                .status(status)
                .build();

        StudentEntity entity = mapper.studentDTOToStudent(dto, context);

        assertThat(entity.getStatus()).isEqualTo(status);
    }

    // ------------------------------------------------------------------
    // Aller-retour : le statut est préservé
    // ------------------------------------------------------------------

    @Test
    void roundTrip_preservesStatus() {
        StudentEntity original = StudentEntity.builder()
                .status(StudentStatus.INACTIVE)
                .build();
        original.setId(1L);

        StudentDTO dto = mapper.studentToStudentDTO(original);
        StudentEntity roundTripped = mapper.studentDTOToStudent(dto, context);

        assertThat(dto.getStatus()).isEqualTo(StudentStatus.INACTIVE);
        assertThat(roundTripped.getStatus()).isEqualTo(StudentStatus.INACTIVE);
    }
}
