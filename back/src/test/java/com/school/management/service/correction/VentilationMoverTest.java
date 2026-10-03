package com.school.management.service.correction;

import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.service.payment.PaymentDistributionService;
import com.school.management.service.payment.PaymentDistributionService.Placement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cas limites du déplacement de ventilation, dépôts simulés. Le déplacement réel, montants relus
 * en base, est éprouvé par {@code EnrolmentCorrectionEndpointIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VentilationMover — cas limites")
class VentilationMoverTest {

    private static final long STUDENT_ID = 7L;

    @Mock private PaymentDetailRepository paymentDetailRepository;
    @Mock private PaymentDistributionService distribution;
    @InjectMocks private VentilationMover mover;

    private static SessionSeriesEntity january() {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(20L);
        series.setName("Janvier");
        return series;
    }

    private static SessionEntity session(long id, LocalDate day) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setSessionTimeStart(day == null ? null
                : Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()));
        return session;
    }

    private static PaymentDetailEntity line(long id, SessionEntity session, boolean active) {
        EncashmentEntity encashment = new EncashmentEntity();
        encashment.setReceiptNumber("RECU-2030-0001");
        EncashmentAllocationEntity allocation = new EncashmentAllocationEntity();
        allocation.setEncashment(encashment);
        PaymentDetailEntity line = new PaymentDetailEntity();
        line.setId(id);
        line.setSession(session);
        line.setAmountPaid(1000.0);
        line.setActive(active);
        line.setEncashmentAllocation(allocation);
        return line;
    }

    @Test
    @DisplayName("séance non datée : rangée après les autres, dite « date inconnue » ; ligne inactive ou d'une "
            + "séance restée facturable : non déplacée")
    void undatedSessionAndUntouchedLines() {
        SessionEntity undated = session(1L, null);
        SessionEntity dated = session(2L, LocalDate.of(2030, 1, 7));
        SessionEntity stillBillable = session(3L, LocalDate.of(2030, 1, 14));
        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, 20L))
                .thenReturn(List.of(line(10L, undated, true), line(11L, dated, true), line(12L, dated, false),
                        line(13L, stillBillable, true)));
        // Une séance suivie peut ne pas être datée : elle reçoit sa part, et l'effet le dit.
        PaymentDetailEntity placedOnUndated = line(20L, session(4L, null), true);
        when(distribution.place(any(), any()))
                .thenReturn(new Placement(List.of(placedOnUndated), BigDecimal.ZERO.setScale(2)))
                .thenReturn(new Placement(List.of(), BigDecimal.ZERO.setScale(2)));

        VentilationMover.Move move = mover.move(STUDENT_ID, january(), Set.of(1L, 2L));

        assertThat(move.moved()).isTrue();
        assertThat(move.effects()).extracting(CorrectionEffect::description).containsExactly(
                "Reçu RECU-2030-0001 : 1 000,00 DA ventilés sur la séance du 07/01/2030 (« Janvier »), devenue "
                        + "non facturable, passent sur non datée (1 000,00 DA)",
                "Reçu RECU-2030-0001 : 1 000,00 DA ventilés sur une séance non datée (« Janvier »), devenue "
                        + "non facturable, ne trouvent aucune autre séance");
    }

    @Test
    @DisplayName("aucune ligne sur les séances perdues : rien n'est écrit")
    void nothingToMove() {
        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, 20L))
                .thenReturn(List.of());

        VentilationMover.Move move = mover.move(STUDENT_ID, january(), Set.of(1L));

        assertThat(move.moved()).isFalse();
        assertThat(move.effects()).isEmpty();
        verify(distribution, never()).place(any(), any());
    }
}
