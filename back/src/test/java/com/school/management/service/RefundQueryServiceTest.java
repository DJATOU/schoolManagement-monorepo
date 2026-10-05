package com.school.management.service;

import com.school.management.dto.StudentRefundDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lecture des remboursements, pour l'historique d'un élève et celui d'un versement. */
class RefundQueryServiceTest {

    private static final long STUDENT_ID = 4L;
    private static final long PAYMENT_ID = 9L;

    private RefundRepository refundRepository;
    private StudentRepository studentRepository;
    private PaymentRepository paymentRepository;
    private RefundQueryService service;

    @BeforeEach
    void setUp() {
        refundRepository = mock(RefundRepository.class);
        studentRepository = mock(StudentRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        service = new RefundQueryService(refundRepository, studentRepository, paymentRepository);
        when(studentRepository.existsById(STUDENT_ID)).thenReturn(true);
        when(paymentRepository.existsById(PAYMENT_ID)).thenReturn(true);
    }

    private static RefundEntity refund(PaymentEntity payment) {
        RefundEntity refund = RefundEntity.builder()
                .id(7L)
                .refundNumber("REMB-2026-0007")
                .refundDate(new Date(1_760_000_000_000L))
                .amount(new BigDecimal("400.5"))
                .reason("Trop-perçu d'octobre")
                .payment(payment)
                .build();
        refund.setCreatedBy("directrice");
        return refund;
    }

    private static PaymentEntity payment() {
        GroupEntity group = GroupEntity.builder().id(3L).name("Maths 4 AM A").build();
        SessionSeriesEntity series = SessionSeriesEntity.builder().id(12L).name("Octobre").build();
        return PaymentEntity.builder().id(PAYMENT_ID).group(group).sessionSeries(series).build();
    }

    @Test
    @DisplayName("chaque remboursement porte sa pièce, son motif, sa série, son groupe, son versement et son auteur")
    void mapsEveryField() {
        when(refundRepository.findActiveForStudent(STUDENT_ID)).thenReturn(List.of(refund(payment())));

        List<StudentRefundDTO> refunds = service.forStudent(STUDENT_ID);

        assertThat(refunds).containsExactly(new StudentRefundDTO(7L, "REMB-2026-0007",
                new Date(1_760_000_000_000L), new BigDecimal("400.50"), "Trop-perçu d'octobre",
                12L, "Octobre", 3L, "Maths 4 AM A", PAYMENT_ID, "directrice"));
        // Échelle monétaire : « 400,50 », pas « 400,5 ».
        assertThat(refunds.get(0).amount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("auteur technique ou absent : la mention du reçu, jamais « system »")
    void recordedByFallsBackLikeTheReceipt() {
        RefundEntity bySystem = refund(payment());
        bySystem.setCreatedBy("system");
        RefundEntity anonymous = refund(payment());
        anonymous.setCreatedBy(null);
        when(refundRepository.findActiveForStudent(STUDENT_ID)).thenReturn(List.of(bySystem, anonymous));

        assertThat(service.forStudent(STUDENT_ID)).extracting(StudentRefundDTO::recordedBy)
                .containsOnly("Administrateur non identifié");
    }

    @Test
    @DisplayName("versement sans série ni groupe : le remboursement est restitué, sans rattachement")
    void paymentWithoutSeriesOrGroup() {
        when(refundRepository.findActiveForStudent(STUDENT_ID))
                .thenReturn(List.of(refund(PaymentEntity.builder().id(PAYMENT_ID).build())));

        StudentRefundDTO refund = service.forStudent(STUDENT_ID).get(0);

        assertThat(refund.seriesId()).isNull();
        assertThat(refund.seriesName()).isNull();
        assertThat(refund.groupId()).isNull();
        assertThat(refund.groupName()).isNull();
        assertThat(refund.paymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    @DisplayName("remboursement sans versement ni montant : restitué à zéro plutôt qu'en erreur")
    void orphanRefund() {
        RefundEntity orphan = refund(null);
        orphan.setAmount(null);
        when(refundRepository.findActiveForStudent(STUDENT_ID)).thenReturn(List.of(orphan));

        StudentRefundDTO refund = service.forStudent(STUDENT_ID).get(0);

        assertThat(refund.amount()).isEqualByComparingTo("0.00");
        assertThat(refund.seriesId()).isNull();
        assertThat(refund.paymentId()).isNull();
    }

    @Test
    @DisplayName("étudiant inconnu : 404, sans lecture des remboursements")
    void unknownStudent() {
        when(studentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.forStudent(99L))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        verify(refundRepository, never()).findActiveForStudent(anyLong());
    }

    @Test
    @DisplayName("versement : ses remboursements, dans l'ordre du dépôt")
    void forPayment() {
        RefundEntity first = refund(payment());
        RefundEntity second = refund(payment());
        second.setId(8L);
        when(refundRepository.findActiveForPayment(PAYMENT_ID)).thenReturn(List.of(first, second));

        assertThat(service.forPayment(PAYMENT_ID)).extracting(StudentRefundDTO::refundId).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("versement inconnu : 404, sans lecture des remboursements")
    void unknownPayment() {
        when(paymentRepository.existsById(98L)).thenReturn(false);

        assertThatThrownBy(() -> service.forPayment(98L))
                .isInstanceOf(CustomServiceException.class)
                .hasMessage("Versement introuvable : 98")
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        verify(refundRepository, never()).findActiveForPayment(anyLong());
    }
}
