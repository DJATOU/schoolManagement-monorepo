package com.school.management.service;

import com.school.management.dto.RefundRequestDTO;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.exception.CustomServiceException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour {@link RefundService}.
 *
 * <p>Les repositories sont mockés (Mockito) afin que les 100+ itérations restent
 * rapides.</p>
 */
class RefundServicePropertyTest {

    private static final long PAYMENT_ID = 1L;
    private static final long STUDENT_ID = 2L;

    // ------------------------------------------------------------------
    // Property 11 — Refund cannot exceed the related paid amount
    // ------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 11: For any refund whose amount is greater than the related payment's paid amount, creation is rejected with a validation error; for any refund whose amount is less than or equal to it, creation succeeds.
    @Property(tries = 100)
    void property11_refundCannotExceedPaidAmount(
            @ForAll("paidAmount") BigDecimal paid,
            @ForAll("refundAmount") BigDecimal refund) {

        RefundRepository refundRepository = mock(RefundRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(refundRepository.save(any(RefundEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        StudentEntity student = StudentEntity.builder().id(STUDENT_ID).build();
        PaymentEntity payment = PaymentEntity.builder()
                .id(PAYMENT_ID)
                .student(student)
                .amountPaid(paid.doubleValue())
                .build();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        when(refundRepository.findMaxRankForPrefix(any())).thenReturn(0);
        when(refundRepository.sumActiveRefundsForPayment(PAYMENT_ID)).thenReturn(BigDecimal.ZERO);
        when(refundRepository.saveAndFlush(any(RefundEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        RefundService service = new RefundService(refundRepository, paymentRepository,
                new RefundNumberService(refundRepository));

        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, refund, null, "motif");

        // Le montant versé est normalisé à l'échelle 2 côté service ; comparer sur la même base.
        BigDecimal normalizedPaid = paid.setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedRefund = refund.setScale(2, RoundingMode.HALF_UP);

        // Aucun remboursement préexistant dans ce scénario : le plafond cumulé égale le montant
        // versé, et cette propriété reste donc valable telle qu'elle avait été écrite. Seule la
        // borne basse a changé : un montant nul, autrefois accepté, est désormais refusé car il
        // produirait une pièce de caisse sans objet.
        boolean rejected = normalizedRefund.compareTo(normalizedPaid) > 0
                || normalizedRefund.compareTo(new BigDecimal("0.01")) < 0;

        if (rejected) {
            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        } else {
            RefundEntity saved = service.create(dto);
            assertThat(saved).isNotNull();
            assertThat(saved.getAmount()).isEqualByComparingTo(normalizedRefund);
        }
    }

    /** Montant payé P dans [0.00, 100000.00] à l'échelle 2. */
    @Provide
    Arbitrary<BigDecimal> paidAmount() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.00"), new BigDecimal("100000.00"))
                .ofScale(2);
    }

    /** Montant de remboursement R dans [0.00, 200000.00] à l'échelle 2. */
    @Provide
    Arbitrary<BigDecimal> refundAmount() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.00"), new BigDecimal("200000.00"))
                .ofScale(2);
    }
}
