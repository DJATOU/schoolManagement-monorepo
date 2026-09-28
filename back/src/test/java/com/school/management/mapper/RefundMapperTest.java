package com.school.management.mapper;

import com.school.management.dto.RefundResponseDTO;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.StudentEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du {@link RefundMapper} (entité → DTO).
 *
 * <p>Vérifie l'aplatissement des relations paiement et étudiant vers leurs identifiants.</p>
 */
class RefundMapperTest {

    private final RefundMapper mapper = new RefundMapperImpl();

    @Test
    void toDto_flattensPaymentAndStudent() {
        PaymentEntity payment = PaymentEntity.builder().id(100L).build();
        StudentEntity student = new StudentEntity();
        student.setId(1L);
        Date refundDate = new Date(5_000L);

        RefundEntity entity = RefundEntity.builder()
                .id(50L)
                .payment(payment)
                .student(student)
                .amount(new BigDecimal("120.00"))
                .refundDate(refundDate)
                .build();

        RefundResponseDTO dto = mapper.toDto(entity);

        assertThat(dto.id()).isEqualTo(50L);
        assertThat(dto.paymentId()).isEqualTo(100L);
        assertThat(dto.studentId()).isEqualTo(1L);
        assertThat(dto.amount()).isEqualByComparingTo("120.00");
        assertThat(dto.refundDate()).isEqualTo(refundDate);
    }

    @Test
    void toDto_nullRelations_leaveIdsNull() {
        RefundEntity entity = RefundEntity.builder()
                .id(51L)
                .amount(new BigDecimal("10.00"))
                .build();

        RefundResponseDTO dto = mapper.toDto(entity);

        assertThat(dto.paymentId()).isNull();
        assertThat(dto.studentId()).isNull();
        assertThat(dto.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
