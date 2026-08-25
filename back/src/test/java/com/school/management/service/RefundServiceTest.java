package com.school.management.service;

import com.school.management.dto.RefundRequestDTO;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link RefundService}.
 *
 * <p>Couvre l'acceptation (montant == versé, montant &lt; versé), le rejet (montant
 * &gt; versé, montant négatif, montant nul), le paiement introuvable (404), la date de
 * remboursement par défaut, et les champs persistés (paiement, étudiant issu du
 * paiement, montant à l'échelle 2).</p>
 */
class RefundServiceTest {

    private static final long PAYMENT_ID = 1L;
    private static final long STUDENT_ID = 2L;

    private RefundRepository refundRepository;
    private PaymentRepository paymentRepository;
    private RefundService service;

    private StudentEntity student;

    @BeforeEach
    void setUp() {
        refundRepository = mock(RefundRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        when(refundRepository.save(any(RefundEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new RefundService(refundRepository, paymentRepository);

        student = StudentEntity.builder().id(STUDENT_ID).build();
    }

    private PaymentEntity paymentWithPaid(Double paid) {
        return PaymentEntity.builder()
                .id(PAYMENT_ID)
                .student(student)
                .amountPaid(paid)
                .build();
    }

    private void stubPayment(Double paid) {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(paymentWithPaid(paid)));
    }

    // ------------------------------------------------------------------
    // Acceptations
    // ------------------------------------------------------------------

    @Test
    void create_amountEqualToPaid_accepted() {
        stubPayment(100.0);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("100.00"), null);

        RefundEntity refund = service.create(dto);

        assertThat(refund.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void create_amountLessThanPaid_accepted() {
        stubPayment(100.0);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("40.00"), null);

        RefundEntity refund = service.create(dto);

        assertThat(refund.getAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void create_zeroAmount_accepted() {
        stubPayment(100.0);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("0.00"), null);

        RefundEntity refund = service.create(dto);

        assertThat(refund.getAmount()).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------
    // Rejets
    // ------------------------------------------------------------------

    @Test
    void create_amountGreaterThanPaid_rejectedBadRequest() {
        stubPayment(100.0);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("100.01"), null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_negativeAmount_rejectedBadRequest() {
        stubPayment(100.0);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("-0.01"), null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_nullAmount_rejectedBadRequest() {
        stubPayment(100.0);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, null, null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_paymentNotFound_rejectedNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void create_nullDto_throwsNpe() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------
    // Champs persistés
    // ------------------------------------------------------------------

    @Test
    void create_defaultsRefundDateToNow_whenNull() {
        stubPayment(100.0);
        Date before = new Date();
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), null);

        RefundEntity refund = service.create(dto);
        Date after = new Date();

        assertThat(refund.getRefundDate()).isNotNull();
        // Bornes inclusives : la date par défaut peut tomber exactement sur la même milliseconde.
        assertThat(refund.getRefundDate()).isBetween(before, after, true, true);
    }

    @Test
    void create_usesProvidedRefundDate_whenPresent() {
        stubPayment(100.0);
        Date provided = new Date(1_000_000_000_000L);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), provided);

        RefundEntity refund = service.create(dto);

        assertThat(refund.getRefundDate()).isEqualTo(provided);
    }

    @Test
    void create_persistsPaymentAndStudentFromPayment() {
        stubPayment(100.0);
        // studentId du DTO différent : l'étudiant enregistré doit provenir du paiement.
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, 999L, new BigDecimal("10.00"), null);

        RefundEntity refund = service.create(dto);

        assertThat(refund.getPayment()).isNotNull();
        assertThat(refund.getPayment().getId()).isEqualTo(PAYMENT_ID);
        assertThat(refund.getStudent()).isSameAs(student);
        assertThat(refund.getStudent().getId()).isEqualTo(STUDENT_ID);
    }

    @Test
    void create_normalizesAmountToScaleTwo() {
        stubPayment(100.0);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("10.1"), null);

        RefundEntity refund = service.create(dto);

        assertThat(refund.getAmount().scale()).isEqualTo(2);
        assertThat(refund.getAmount()).isEqualByComparingTo("10.10");
    }

    @Test
    void create_nullPaidAmountTreatedAsZero_rejectsPositiveRefund() {
        stubPayment(null);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("0.01"), null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_nullPaidAmountTreatedAsZero_acceptsZeroRefund() {
        stubPayment(null);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("0.00"), null);

        RefundEntity refund = service.create(dto);

        assertThat(refund.getAmount()).isEqualByComparingTo("0.00");
    }
}
