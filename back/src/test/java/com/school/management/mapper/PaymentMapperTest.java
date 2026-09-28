package com.school.management.mapper;

import com.school.management.dto.PaymentDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.shared.exception.ResourceNotFoundException;
import com.school.management.shared.mapper.MappingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du {@link PaymentMapper}.
 *
 * <p>Couvre :</p>
 * <ul>
 *   <li>l'aplatissement entité → DTO et la présence / absence de la note ;</li>
 *   <li>la résolution des références DTO → entité via {@link MappingContext} (idToStudent,
 *       idToSession, idToSessionSeries, idToGroup), y compris le rejet d'un identifiant
 *       inconnu par une {@link ResourceNotFoundException}.</li>
 * </ul>
 */
class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapperImpl();

    private StudentRepository studentRepository;
    private SessionRepository sessionRepository;
    private SessionSeriesRepository sessionSeriesRepository;
    private GroupRepository groupRepository;
    private MappingContext context;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        sessionRepository = mock(SessionRepository.class);
        sessionSeriesRepository = mock(SessionSeriesRepository.class);
        groupRepository = mock(GroupRepository.class);
        context = MappingContext.of(
                null, null, null, null, null, null, null, null,
                groupRepository, sessionSeriesRepository, studentRepository, sessionRepository);
    }

    // ------------------------------------------------------------------
    // Entité → DTO : note présente / absente
    // ------------------------------------------------------------------

    @Test
    void toDto_withNote_returnsNote() {
        StudentEntity student = new StudentEntity();
        student.setId(1L);
        PaymentEntity entity = PaymentEntity.builder()
                .id(5L)
                .student(student)
                .amountPaid(100.0)
                .notes("versement partiel")
                .build();

        PaymentDTO dto = mapper.toDto(entity);

        assertThat(dto.getStudentId()).isEqualTo(1L);
        assertThat(dto.getNotes()).isEqualTo("versement partiel");
    }

    @Test
    void toDto_withoutNote_returnsNullNote() {
        PaymentEntity entity = PaymentEntity.builder()
                .id(6L)
                .amountPaid(50.0)
                .notes(null)
                .build();

        PaymentDTO dto = mapper.toDto(entity);

        assertThat(dto.getNotes()).isNull();
    }

    // ------------------------------------------------------------------
    // DTO → entité : résolution des références via MappingContext + note
    // ------------------------------------------------------------------

    @Test
    void toEntity_resolvesReferencesAndMapsNote() {
        StudentEntity student = new StudentEntity();
        student.setId(1L);
        SessionEntity session = new SessionEntity();
        session.setId(10L);
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(20L);
        GroupEntity group = new GroupEntity();
        group.setId(30L);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionSeriesRepository.findById(20L)).thenReturn(Optional.of(series));
        when(groupRepository.findById(30L)).thenReturn(Optional.of(group));

        PaymentDTO dto = PaymentDTO.builder()
                .studentId(1L)
                .sessionId(10L)
                .sessionSeriesId(20L)
                .groupId(30L)
                .amountPaid(200.0)
                .notes("réglé en espèces")
                .build();

        PaymentEntity entity = mapper.toEntity(dto, context);

        assertThat(entity.getStudent()).isSameAs(student);
        assertThat(entity.getSession()).isSameAs(session);
        assertThat(entity.getSessionSeries()).isSameAs(series);
        assertThat(entity.getGroup()).isSameAs(group);
        assertThat(entity.getNotes()).isEqualTo("réglé en espèces");
    }

    @Test
    void toEntity_unknownStudent_throwsResourceNotFound() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        PaymentDTO dto = PaymentDTO.builder()
                .studentId(99L)
                .amountPaid(10.0)
                .build();

        assertThatThrownBy(() -> mapper.toEntity(dto, context))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void toEntity_nullReferenceIds_resolveToNull() {
        PaymentDTO dto = PaymentDTO.builder()
                .amountPaid(10.0)
                .notes(null)
                .build();

        PaymentEntity entity = mapper.toEntity(dto, context);

        assertThat(entity.getStudent()).isNull();
        assertThat(entity.getSession()).isNull();
        assertThat(entity.getSessionSeries()).isNull();
        assertThat(entity.getGroup()).isNull();
        assertThat(entity.getNotes()).isNull();
    }
}
