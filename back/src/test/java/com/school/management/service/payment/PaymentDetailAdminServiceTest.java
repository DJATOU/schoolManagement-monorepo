package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailUpdateDTO;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Corrections administrées d'une ligne de ventilation (spec admin-corrections, A.6, défaut 2).
 *
 * <p><b>Ce que ces tests verrouillent.</b> Corriger, supprimer ou réactiver une ligne de
 * ventilation ne change pas l'argent reçu : le cumul d'une série est la somme de ses Imputations
 * actives, recalculée par {@link EncashmentService}, et n'est plus jamais réécrit depuis les
 * lignes. Avant A.6, {@code recalculatePayment} remplaçait le cumul par la somme des lignes
 * actives : toute part non ventilée disparaissait du registre, et le cumul pouvait passer sous le
 * total remboursé.</p>
 *
 * <p>Le calcul du statut lui-même est éprouvé sur une vraie base par
 * {@code EncashmentServiceIntegrationTest} et par {@link PaymentLineStatusTest}.</p>
 */
class PaymentDetailAdminServiceTest {

    private static final Long PAYMENT_ID = 500L;
    private static final Long DETAIL_ID = 900L;

    private PaymentDetailRepository paymentDetailRepository;
    private PaymentRepository paymentRepository;
    private PaymentDetailAuditService auditService;
    private ReadOnlyYearGuard readOnlyYearGuard;
    private EncashmentService encashmentService;

    private PaymentDetailAdminService service;
    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        paymentDetailRepository = mock(PaymentDetailRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        auditService = mock(PaymentDetailAuditService.class);
        readOnlyYearGuard = mock(ReadOnlyYearGuard.class);
        encashmentService = mock(EncashmentService.class);

        lenient().when(paymentDetailRepository.save(any(PaymentDetailEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        payment = payment();
        lenient().when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        service = new PaymentDetailAdminService(paymentDetailRepository, paymentRepository,
                auditService, readOnlyYearGuard, encashmentService);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PaymentEntity payment() {
        StudentEntity student = new StudentEntity();
        student.setId(1L);
        GroupEntity group = new GroupEntity();
        group.setId(100L);
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(10L);
        series.setGroup(group);

        PaymentEntity payment = PaymentEntity.builder()
                .student(student).group(group).sessionSeries(series)
                .amountPaid(4000.0).status("COMPLETED")
                .build();
        payment.setId(PAYMENT_ID);
        return payment;
    }

    /** Ligne de 2 000 DA, part d'une Imputation active ou neutralisée. */
    private PaymentDetailEntity detail(boolean active, boolean imputationActive) {
        PaymentDetailEntity detail = PaymentDetailEntity.builder()
                .id(DETAIL_ID)
                .payment(payment)
                .amountPaid(2000.0)
                .permanentlyDeleted(false)
                .encashmentAllocation(EncashmentAllocationEntity.builder().id(70L).active(imputationActive).build())
                .build();
        detail.setActive(active);
        when(paymentDetailRepository.findById(DETAIL_ID)).thenReturn(Optional.of(detail));
        return detail;
    }

    private static PaymentDetailUpdateDTO update(Double amount, Boolean active) {
        PaymentDetailUpdateDTO dto = new PaymentDetailUpdateDTO();
        dto.setReason("correction");
        dto.setAmount(amount);
        dto.setActive(active);
        return dto;
    }

    // ------------------------------------------------------------------
    // Le cumul ne vient plus de la ventilation (défaut 2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("recalcul : la ligne de paiement est verrouillée, puis cumul et statut viennent des Imputations")
    void recalculationDelegatesToTheImputations() {
        service.recalculatePayment(PAYMENT_ID);

        verify(paymentRepository).findByIdForUpdate(PAYMENT_ID);
        verify(encashmentService).refreshSeriesCumul(payment);
        // Les lignes ne sont plus lues : elles ne définissent plus le cumul.
        verify(paymentDetailRepository, never()).findByPaymentId(anyLong());
    }

    @Test
    @DisplayName("montant d'une ligne corrigé : la ventilation change, le cumul reste celui des Imputations")
    void correctingALineAmountLeavesTheCumulToTheImputations() {
        PaymentDetailEntity detail = detail(true, true);

        service.updatePaymentDetail(DETAIL_ID, update(500.0, null), "admin");

        assertThat(detail.getAmountPaid()).isEqualTo(500.0);
        // Avant A.6 : 4 000 DA reçus devenaient 2 500 DA, la somme des lignes restantes.
        assertThat(payment.getAmountPaid()).isEqualTo(4000.0);
        verify(encashmentService).refreshSeriesCumul(payment);
    }

    @Test
    @DisplayName("ligne supprimée définitivement : le cumul reste, et la ligne de paiement n'est pas annulée")
    void deletingALineNeitherErasesMoneyNorCancelsTheLine() {
        detail(true, true);

        service.deletePaymentDetail(DETAIL_ID, "erreur de saisie", "admin");

        // Avant A.6 : toutes les lignes supprimées rendaient la série CANCELLED, sortie des devis
        // avec son argent, et le versement suivant ouvrait une seconde ligne pour la même série.
        assertThat(payment.getStatus()).isEqualTo("COMPLETED");
        assertThat(payment.getAmountPaid()).isEqualTo(4000.0);
        verify(encashmentService).refreshSeriesCumul(payment);
    }

    // ------------------------------------------------------------------
    // Une ligne d'un Encaissement annulé ne revient pas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("réactivation d'une ligne d'un Encaissement annulé : 409, rien n'est écrit")
    void reactivatingALineOfACancelledEncashmentIsRefused() {
        PaymentDetailEntity detail = detail(false, false);

        assertThatThrownBy(() -> service.reactivatePaymentDetail(DETAIL_ID, "erreur", "admin"))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("encaissement annulé")
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(detail.getActive()).isFalse();
        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
        verifyNoInteractions(auditService, encashmentService);
    }

    @Test
    @DisplayName("même refus par la modification « active = vrai »")
    void activatingThroughTheUpdateIsRefusedToo() {
        PaymentDetailEntity detail = detail(false, false);

        assertThatThrownBy(() -> service.updatePaymentDetail(DETAIL_ID, update(null, true), "admin"))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("encaissement annulé");

        assertThat(detail.getActive()).isFalse();
        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
    }

    @Test
    @DisplayName("réactivation d'une ligne d'un Encaissement actif : acceptée")
    void reactivatingALineOfALiveEncashmentIsAccepted() {
        PaymentDetailEntity detail = detail(false, true);

        service.reactivatePaymentDetail(DETAIL_ID, "dévalidation annulée", "admin");

        assertThat(detail.getActive()).isTrue();
        verify(encashmentService).refreshSeriesCumul(payment);
    }

    // ------------------------------------------------------------------
    // Garde année scolaire en lecture seule
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Modification sur une année close → refusée, rien n'est enregistré")
    void updateRejectedOnClosedYear() {
        detail(true, true);
        doThrow(new ReadOnlySchoolYearException()).when(readOnlyYearGuard).assertGroupMutable(any());

        assertThatThrownBy(() -> service.updatePaymentDetail(DETAIL_ID, update(60.0, null), "admin"))
                .isInstanceOf(ReadOnlySchoolYearException.class);

        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
        verifyNoInteractions(encashmentService);
    }

    @Test
    @DisplayName("Suppression sur une année close → refusée, rien n'est enregistré")
    void deleteRejectedOnClosedYear() {
        detail(true, true);
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
