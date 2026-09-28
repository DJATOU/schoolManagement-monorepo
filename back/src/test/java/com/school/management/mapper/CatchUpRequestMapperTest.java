package com.school.management.mapper;

import com.school.management.dto.CatchUpResponseDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.StudentEntity;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du {@link CatchUpRequestMapper} (entité → DTO).
 *
 * <p>Vérifie l'aplatissement des relations vers leurs identifiants et la robustesse
 * aux relations nulles (demande PENDING sans séance de rattrapage).</p>
 */
class CatchUpRequestMapperTest {

    private final CatchUpRequestMapper mapper = new CatchUpRequestMapperImpl();

    private static StudentEntity student(long id) {
        StudentEntity s = new StudentEntity();
        s.setId(id);
        return s;
    }

    private static SessionEntity session(long id) {
        SessionEntity s = new SessionEntity();
        s.setId(id);
        return s;
    }

    private static GroupEntity group(long id) {
        GroupEntity g = new GroupEntity();
        g.setId(id);
        return g;
    }

    private static AttendanceEntity attendance(long id) {
        AttendanceEntity a = new AttendanceEntity();
        a.setId(id);
        return a;
    }

    @Test
    void toDto_fullyScheduledEntity_flattensAllReferences() {
        Date requestDate = new Date(1_000L);
        Date scheduledDate = new Date(2_000L);
        Date completedDate = new Date(3_000L);

        CatchUpRequestEntity entity = CatchUpRequestEntity.builder()
                .id(42L)
                .student(student(1L))
                .originalSession(session(10L))
                .originalGroup(group(20L))
                .originalAttendance(attendance(30L))
                .catchUpSession(session(11L))
                .catchUpGroup(group(21L))
                .status(CatchUpStatus.COMPLETED)
                .requestDate(requestDate)
                .scheduledDate(scheduledDate)
                .completedDate(completedDate)
                .cancellationReason(null)
                .notes("rattrapage effectué")
                .build();

        CatchUpResponseDTO dto = mapper.toDto(entity);

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.studentId()).isEqualTo(1L);
        assertThat(dto.originalSessionId()).isEqualTo(10L);
        assertThat(dto.originalGroupId()).isEqualTo(20L);
        assertThat(dto.originalAttendanceId()).isEqualTo(30L);
        assertThat(dto.catchUpSessionId()).isEqualTo(11L);
        assertThat(dto.catchUpGroupId()).isEqualTo(21L);
        assertThat(dto.status()).isEqualTo(CatchUpStatus.COMPLETED);
        assertThat(dto.requestDate()).isEqualTo(requestDate);
        assertThat(dto.scheduledDate()).isEqualTo(scheduledDate);
        assertThat(dto.completedDate()).isEqualTo(completedDate);
        assertThat(dto.notes()).isEqualTo("rattrapage effectué");
        assertThat(dto.cancellationReason()).isNull();
    }

    @Test
    void toDto_pendingEntityWithNullCatchUpReferences_leavesThoseIdsNull() {
        CatchUpRequestEntity entity = CatchUpRequestEntity.builder()
                .id(7L)
                .student(student(1L))
                .originalSession(session(10L))
                .originalGroup(group(20L))
                .originalAttendance(attendance(30L))
                .status(CatchUpStatus.PENDING)
                .requestDate(new Date(1_000L))
                .build();

        CatchUpResponseDTO dto = mapper.toDto(entity);

        assertThat(dto.catchUpSessionId()).isNull();
        assertThat(dto.catchUpGroupId()).isNull();
        assertThat(dto.scheduledDate()).isNull();
        assertThat(dto.completedDate()).isNull();
        assertThat(dto.status()).isEqualTo(CatchUpStatus.PENDING);
    }

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
