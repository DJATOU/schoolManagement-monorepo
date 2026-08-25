package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailUpdateDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du recalcul de statut de paiement administré.
 *
 * <p>Le coût de référence doit venir du {@link PaymentCostResolver} (donc réduction
 * appliquée, séances planifiées issues de la série), et non d'un calcul local. Ces tests
 * verrouillent les quatre statuts possibles ainsi que le refus d'écriture sur une année
 * scolaire close.</p>
 */
class PaymentDetailAdminServiceTest {

    private static final Long PAYMENT_ID = 500L;
    private static final Long DETAIL_ID = 900L;
    private static final Long STUDENT_ID = 1L;
    private static final Long SERIES_ID = 10L;

    private PaymentDetailRepository paymentDetailRepository;
    private PaymentRepository paymentRepository;
    private PaymentDetailAuditService auditService;
    private ReadOnlyYearGuard readOnlyYearGuard;
    private PaymentCostResolver paymentCostResolver;

    private PaymentDetailAdminService service;

    @BeforeEach
    void setUp() {
        paymentDetailRepository = mock(PaymentDetailRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        auditService = mock(PaymentDetailAuditService.class);
        readOnlyYearGuard = mock(ReadOnlyYearGuard.class);
        paymentCostResolver = mock(PaymentCostResolver.class);

        lenient().when(paymentDetailRepository.save(any(PaymentDetailEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service = new PaymentDetailAdminService(paymentDetailRepository, paymentRepository,
                auditService, readOnlyYearGuard, paymentCostResolver);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PaymentEntity payment(boolean withSeries) {
        StudentEntity student = new StudentEntity();
        student.setId(STUDENT_ID);

        GroupEntity group = new GroupEntity();
        group.setId(100L);

        PaymentEntity payment = PaymentEntity.builder()
                .student(student)
                .group(group)
                .status("PENDING")
                .build();
        payment.setId(PAYMENT_ID);

        if (withSeries) {
            SessionSeriesEntity series = new SessionSeriesEntity();
            series.setId(SERIES_ID);
            series.setGroup(group);
            payment.setSessionSeries(series);
        }
        return payment;
    }

    private PaymentDetailEntity detail(PaymentEntity payment, double amount, boolean active,
            boolean permanentlyDeleted) {
        return PaymentDetailEntity.builder()
                .payment(payment)
                .amountPaid(amount)
                .active(active)
                .permanentlyDeleted(permanentlyDeleted)
                .build();
    }

    private void stubMonthTotalCost(String monthTotalCost) {
        when(paymentCostResolver.resolve(STUDENT_ID, SERIES_ID)).thenReturn(
                new PaymentCostResolver.PaymentStatusResult(
                        new BigDecimal(monthTotalCost),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false,
                        false));
    }

    private void recalculate(PaymentEntity payment, List<PaymentDetailEntity> details) {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentDetailRepository.findByPaymentId(PAYMENT_ID)).thenReturn(details);
        service.recalculatePayment(PAYMENT_ID);
    }

    // ------------------------------------------------------------------
    // recalculatePayment — statuts
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versé >= coût du mois résolu → COMPLETED")
    void completedWhenPaidReachesResolvedMonthCost() {
        PaymentEntity payment = payment(true);
        stubMonthTotalCost("240.00");

        recalculate(payment, List.of(detail(payment, 120.0, true, false), detail(payment, 120.0, true, false)));

        assertThat(payment.getStatus()).isEqualTo("COMPLETED");
        assertThat(payment.getAmountPaid()).isEqualTo(240.0);
    }

    @Test
    @DisplayName("Versé partiel → IN_PROGRESS (le coût vient de la série, pas d'un repli à une séance)")
    void inProgressWhenPartiallyPaid() {
        PaymentEntity payment = payment(true);
        stubMonthTotalCost("240.00");

        recalculate(payment, List.of(detail(payment, 30.0, true, false)));

        assertThat(payment.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Détails inactifs seulement → versé nul → PENDING")
    void pendingWhenNothingActivePaid() {
        PaymentEntity payment = payment(true);
        stubMonthTotalCost("240.00");

        recalculate(payment, List.of(detail(payment, 120.0, false, false)));

        assertThat(payment.getStatus()).isEqualTo("PENDING");
        assertThat(payment.getAmountPaid()).isZero();
    }

    @Test
    @DisplayName("Tous les détails définitivement supprimés → CANCELLED")
    void cancelledWhenAllPermanentlyDeleted() {
        PaymentEntity payment = payment(true);
        stubMonthTotalCost("240.00");

        recalculate(payment, List.of(detail(payment, 120.0, false, true)));

        assertThat(payment.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Étudiant exempté (coût du mois nul) → COMPLETED, plus de statut bloqué")
    void completedWhenExempted() {
        PaymentEntity payment = payment(true);
        stubMonthTotalCost("0.00");

        recalculate(payment, List.of());

        assertThat(payment.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Coût inconnu (paiement sans série) → jamais COMPLETED")
    void neverCompletedWhenCostUnknown() {
        PaymentEntity payment = payment(false);

        recalculate(payment, List.of(detail(payment, 5000.0, true, false)));

        assertThat(payment.getStatus()).isEqualTo("IN_PROGRESS");
        verify(paymentCostResolver, never()).resolve(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Résolution du coût en échec → jamais COMPLETED")
    void neverCompletedWhenResolutionFails() {
        PaymentEntity payment = payment(true);
        when(paymentCostResolver.resolve(STUDENT_ID, SERIES_ID))
                .thenThrow(new IllegalStateException("série introuvable"));

        recalculate(payment, List.of(detail(payment, 5000.0, true, false)));

        assertThat(payment.getStatus()).isEqualTo("IN_PROGRESS");
    }

    // ------------------------------------------------------------------
    // Garde année scolaire en lecture seule
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Modification sur une année close → refusée, rien n'est enregistré")
    void updateRejectedOnClosedYear() {
        PaymentEntity payment = payment(true);
        PaymentDetailEntity detail = detail(payment, 120.0, true, false);
        when(paymentDetailRepository.findById(DETAIL_ID)).thenReturn(Optional.of(detail));
        doThrow(new ReadOnlySchoolYearException()).when(readOnlyYearGuard).assertGroupMutable(any());

        PaymentDetailUpdateDTO dto = new PaymentDetailUpdateDTO();
        dto.setReason("correction");
        dto.setAmount(60.0);

        assertThatThrownBy(() -> service.updatePaymentDetail(DETAIL_ID, dto, "admin"))
                .isInstanceOf(ReadOnlySchoolYearException.class);

        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
    }

    @Test
    @DisplayName("Suppression sur une année close → refusée, rien n'est enregistré")
    void deleteRejectedOnClosedYear() {
        PaymentEntity payment = payment(true);
        PaymentDetailEntity detail = detail(payment, 120.0, true, false);
        when(paymentDetailRepository.findById(DETAIL_ID)).thenReturn(Optional.of(detail));
        doThrow(new ReadOnlySchoolYearException()).when(readOnlyYearGuard).assertGroupMutable(any());

        assertThatThrownBy(() -> service.deletePaymentDetail(DETAIL_ID, "erreur de saisie", "admin"))
                .isInstanceOf(ReadOnlySchoolYearException.class);

        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
    }

    @Test
    @DisplayName("Motif absent → refus de la modification (traçabilité d'audit)")
    void reasonIsMandatory() {
        PaymentDetailUpdateDTO dto = new PaymentDetailUpdateDTO();
        dto.setAmount(60.0);

        assertThatThrownBy(() -> service.updatePaymentDetail(DETAIL_ID, dto, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
