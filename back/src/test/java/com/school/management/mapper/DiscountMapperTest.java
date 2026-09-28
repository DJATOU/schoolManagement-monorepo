package com.school.management.mapper;

import com.school.management.dto.DiscountResponseDTO;
import com.school.management.persistance.DiscountEntity;
import com.school.management.persistance.DiscountScope;
import com.school.management.persistance.StudentEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du {@link DiscountMapper} (entité → DTO).
 *
 * <p>Vérifie l'aplatissement de la relation étudiant et le report des identifiants de
 * portée pour chaque scope.</p>
 */
class DiscountMapperTest {

    private final DiscountMapper mapper = new DiscountMapperImpl();

    private static StudentEntity student(long id) {
        StudentEntity s = new StudentEntity();
        s.setId(id);
        return s;
    }

    @Test
    void toDto_groupScope_flattensStudentAndKeepsGroupId() {
        DiscountEntity entity = DiscountEntity.builder()
                .id(5L)
                .student(student(1L))
                .scope(DiscountScope.GROUP)
                .groupId(20L)
                .rate(new BigDecimal("0.25"))
                .build();

        DiscountResponseDTO dto = mapper.toDto(entity);

        assertThat(dto.id()).isEqualTo(5L);
        assertThat(dto.studentId()).isEqualTo(1L);
        assertThat(dto.scope()).isEqualTo(DiscountScope.GROUP);
        assertThat(dto.groupId()).isEqualTo(20L);
        assertThat(dto.seriesId()).isNull();
        assertThat(dto.sessionId()).isNull();
        assertThat(dto.rate()).isEqualByComparingTo("0.25");
    }

    @Test
    void toDto_seriesScope_keepsSeriesId() {
        DiscountEntity entity = DiscountEntity.builder()
                .id(6L)
                .student(student(2L))
                .scope(DiscountScope.SERIES)
                .seriesId(30L)
                .rate(new BigDecimal("0.10"))
                .build();

        DiscountResponseDTO dto = mapper.toDto(entity);

        assertThat(dto.scope()).isEqualTo(DiscountScope.SERIES);
        assertThat(dto.seriesId()).isEqualTo(30L);
        assertThat(dto.groupId()).isNull();
    }

    @Test
    void toDto_exemptionGroupRateOne_isPreserved() {
        DiscountEntity entity = DiscountEntity.builder()
                .id(9L)
                .student(student(3L))
                .scope(DiscountScope.SESSION)
                .sessionId(40L)
                .rate(new BigDecimal("1.00"))
                .build();

        DiscountResponseDTO dto = mapper.toDto(entity);

        assertThat(dto.sessionId()).isEqualTo(40L);
        assertThat(dto.rate()).isEqualByComparingTo("1.00");
    }

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
