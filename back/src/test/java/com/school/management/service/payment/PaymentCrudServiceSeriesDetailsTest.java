package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Lignes de ventilation d'une série, telles que les reçoit l'écran de confirmation : le reste à
 * régler de chaque ligne est celui de <b>sa séance</b>, au prix net, toutes lignes confondues
 * (spec admin-corrections, inventaire A.1, lecteur 5).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Détails de paiement d'une série : reste à régler par séance")
class PaymentCrudServiceSeriesDetailsTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long SERIES_ID = 10L;

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentDetailRepository paymentDetailRepository;
    @Mock private PaymentQuoteService paymentQuoteService;
    @InjectMocks private PaymentCrudService service;

    private static SessionEntity session(long id) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setTitle("Séance " + id);
        return session;
    }

    private static PaymentDetailEntity line(long id, SessionEntity session, double amount, boolean active,
                                            String paymentStatus) {
        PaymentEntity payment = new PaymentEntity();
        payment.setStatus(paymentStatus);
        PaymentDetailEntity line = PaymentDetailEntity.builder()
                .id(id).payment(payment).session(session).amountPaid(amount).build();
        line.setActive(active);
        return line;
    }

    @Test
    @DisplayName("deux lignes sur une séance : chacune annonce le reste de la séance, au prix net")
    void remainingBalanceIsTheSessionsNotTheLines() {
        SessionEntity first = session(1L);
        SessionEntity second = session(2L);
        // Prix net 1 600 DA (réduction de 20 % sur 2 000) : l'ancien calcul prenait le tarif
        // catalogue moins la seule ligne affichée, soit 1 000 et 1 400 DA « restant dus ».
        when(paymentQuoteService.netPricePerSession(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("1600.00"));
        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(
                        line(1L, first, 1000.0, true, "IN_PROGRESS"),
                        line(2L, first, 600.0, true, "IN_PROGRESS"),
                        line(3L, second, 500.0, true, "IN_PROGRESS"),
                        line(4L, second, 900.0, false, "IN_PROGRESS")));

        List<PaymentDetailDTO> details = service.getPaymentDetailsForSeries(STUDENT_ID, SERIES_ID);

        assertThat(details).extracting(PaymentDetailDTO::getPaymentDetailId).containsExactly(1L, 2L, 3L);
        assertThat(details).extracting(PaymentDetailDTO::getRemainingBalance).containsExactly(0.0, 0.0, 1100.0);
    }

    @Test
    @DisplayName("au-delà du prix net : le reste est nul, jamais négatif")
    void remainingBalanceIsNeverNegative() {
        SessionEntity first = session(1L);
        when(paymentQuoteService.netPricePerSession(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("1000.00"));
        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(line(1L, first, 1200.0, true, "COMPLETED")));

        assertThat(service.getPaymentDetailsForSeries(STUDENT_ID, SERIES_ID))
                .singleElement()
                .satisfies(detail -> assertThat(detail.getRemainingBalance()).isZero());
    }
}
