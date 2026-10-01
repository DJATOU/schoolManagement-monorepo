package com.school.management.service.payment;

import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Une ligne de ventilation ne se corrige plus à l'unité (spec admin-corrections, A.6).
 *
 * <p>Modifier son montant, la désactiver, la supprimer ou la réactiver ne corrigerait pas le
 * versement — le reçu, le cumul et le dû sont ceux de l'Encaissement — mais ferait diverger les
 * recettes, qui somment la ventilation, de l'argent au registre. La correction est refusée, et le
 * message nomme le reçu à annuler ou corriger.</p>
 */
class PaymentDetailAdminServiceTest {

    private static final Long DETAIL_ID = 900L;

    private PaymentDetailRepository paymentDetailRepository;
    private PaymentDetailAdminService service;

    @BeforeEach
    void setUp() {
        paymentDetailRepository = mock(PaymentDetailRepository.class);
        service = new PaymentDetailAdminService(paymentDetailRepository);
    }

    private PaymentDetailEntity detailOfReceipt(String receiptNumber) {
        PaymentDetailEntity detail = PaymentDetailEntity.builder()
                .id(DETAIL_ID)
                .amountPaid(2000.0)
                .encashmentAllocation(EncashmentAllocationEntity.builder()
                        .id(70L)
                        .encashment(EncashmentEntity.builder().id(7L).receiptNumber(receiptNumber).build())
                        .active(true)
                        .build())
                .build();
        detail.setActive(true);
        when(paymentDetailRepository.findById(DETAIL_ID)).thenReturn(Optional.of(detail));
        return detail;
    }

    @Test
    @DisplayName("correction d'une ligne : 409, le message nomme le reçu à annuler ou corriger")
    void lineCorrectionIsRefusedWithTheReceiptToCorrect() {
        PaymentDetailEntity detail = detailOfReceipt("RECU-2026-0001");

        assertThatThrownBy(() -> service.refuseLineCorrection(DETAIL_ID))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("RECU-2026-0001")
                .hasMessageContaining("annulez ou corrigez le reçu")
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        // Rien n'est écrit : ni la ligne, ni une trace.
        assertThat(detail.getActive()).isTrue();
        assertThat(detail.getAmountPaid()).isEqualTo(2000.0);
        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
    }

    @Test
    @DisplayName("ligne introuvable : 404, et non plus une erreur 500")
    void unknownLineIsNotFound() {
        when(paymentDetailRepository.findById(DETAIL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refuseLineCorrection(DETAIL_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.getPaymentDetail(DETAIL_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
