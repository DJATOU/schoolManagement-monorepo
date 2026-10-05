package com.school.management.service.payment;

import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentDetailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dévalidation et revalidation d'une séance : ses lignes de ventilation suivent.
 *
 * <p>La revalidation ne doit pas faire revenir de l'argent qui n'existe plus : une ligne d'un
 * Encaissement annulé, ou supprimée définitivement, reste inactive (spec admin-corrections,
 * inventaire A.1).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Lignes de ventilation d'une séance dévalidée puis revalidée")
class PaymentDetailDeactivationServiceTest {

    private static final Long SESSION_ID = 40L;

    @Mock private PaymentDetailRepository repository;
    @InjectMocks private PaymentDetailDeactivationService service;

    private static PaymentDetailEntity line(long id, boolean active, Boolean allocationActive, boolean deleted) {
        StudentEntity student = new StudentEntity();
        student.setId(7L);
        PaymentEntity payment = new PaymentEntity();
        payment.setStudent(student);
        PaymentDetailEntity line = PaymentDetailEntity.builder()
                .id(id)
                .payment(payment)
                .amountPaid(1000.0)
                .permanentlyDeleted(deleted)
                .encashmentAllocation(allocationActive == null ? null
                        : EncashmentAllocationEntity.builder().id(900L + id).active(allocationActive).build())
                .build();
        line.setActive(active);
        return line;
    }

    @Test
    @DisplayName("revalidation : seules les lignes d'un Encaissement encore actif reviennent")
    void reactivationSkipsCancelledEncashmentsAndPermanentDeletions() {
        PaymentDetailEntity live = line(1L, false, true, false);
        PaymentDetailEntity cancelled = line(2L, false, false, false);
        PaymentDetailEntity deleted = line(3L, false, true, true);
        when(repository.findBySessionId(SESSION_ID)).thenReturn(List.of(live, cancelled, deleted));

        assertThat(service.reactivatePaymentDetailsBySessionId(SESSION_ID)).isEqualTo(1);

        assertThat(live.getActive()).isTrue();
        assertThat(cancelled.getActive()).as("argent d'un Encaissement annulé").isFalse();
        assertThat(deleted.getActive()).as("suppression définitive").isFalse();
        verify(repository).save(live);
        verify(repository, never()).save(cancelled);
        verify(repository, never()).save(deleted);
    }

    @Test
    @DisplayName("dévalidation : toutes les lignes actives de la séance sont désactivées")
    void deactivationDeactivatesActiveLines() {
        PaymentDetailEntity first = line(1L, true, true, false);
        PaymentDetailEntity second = line(2L, true, true, false);
        when(repository.findBySessionId(SESSION_ID)).thenReturn(List.of(first, second));

        assertThat(service.deactivatePaymentDetailsBySessionId(SESSION_ID)).isEqualTo(2);
        assertThat(first.getActive()).isFalse();
        assertThat(second.getActive()).isFalse();
    }
}
