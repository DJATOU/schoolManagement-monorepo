package com.school.management.service;

import com.school.management.dto.RefundRequestDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriété de neutralité du remboursement sur le paiement et sa série (exigence 7.13).
 *
 * <p>Un remboursement est un <strong>mouvement de caisse</strong>, pas une révision de ce que
 * l'étudiant devait. Le montant versé du paiement, son imputation à une série et à un groupe, et son
 * statut ne doivent donc pas bouger. Si l'un d'eux changeait, le devis et le statut de retard se
 * déplaceraient : un étudiant remboursé passerait « en retard » alors qu'il ne doit rien de plus, ou
 * l'inverse.</p>
 *
 * <p>La propriété est vérifiée sur l'état du paiement plutôt que sur la sortie du devis, et ce n'est
 * pas un contournement : c'est l'état du paiement qui alimente le devis. Le figer, c'est figer tout
 * ce qui en dérive.</p>
 */
class RefundDevisNeutralityPropertyTest {

    private static final long PAYMENT_ID = 1L;
    private static final long STUDENT_ID = 2L;
    private static final long SERIES_ID = 3L;
    private static final long GROUP_ID = 4L;

    // Feature: absence-justification-and-refund-receipts, Property 12: For any refund created on a
    // payment attached to a series, the payment's paid amount, its series and group attachment and
    // its status remain unchanged, so the quote and the overdue status do not move.
    @Property(tries = 100)
    void property12_refundDoesNotMoveThePayment(
            @ForAll("paidAmount") BigDecimal verse,
            @ForAll("refundSequence") List<BigDecimal> demandes) {

        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);

        PaymentEntity payment = PaymentEntity.builder()
                .id(PAYMENT_ID)
                .student(StudentEntity.builder().id(STUDENT_ID).build())
                .amountPaid(verse.doubleValue())
                .sessionSeries(series)
                .group(group)
                .status("COMPLETED")
                .build();

        // État de référence, capturé avant toute opération.
        Double verseAvant = payment.getAmountPaid();
        Long serieAvant = payment.getSessionSeries().getId();
        Long groupeAvant = payment.getGroup().getId();
        String statutAvant = payment.getStatus();

        List<RefundEntity> enregistres = new ArrayList<>();
        RefundRepository refundRepository = mock(RefundRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);

        when(refundRepository.saveAndFlush(any(RefundEntity.class))).thenAnswer(inv -> {
            RefundEntity refund = inv.getArgument(0);
            enregistres.add(refund);
            return refund;
        });
        when(refundRepository.findMaxRankForPrefix(any())).thenAnswer(inv -> enregistres.size());
        when(refundRepository.sumActiveRefundsForPayment(anyLong())).thenAnswer(inv ->
                enregistres.stream()
                        .map(RefundEntity::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        RefundService service = new RefundService(refundRepository, paymentRepository,
                new RefundNumberService(refundRepository));

        for (BigDecimal demande : demandes) {
            try {
                service.create(new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, demande, null, "motif"));
            } catch (RuntimeException ignored) {
                // Un refus est un résultat acceptable ici : la propriété porte sur la neutralité,
                // pas sur l'acceptation. Un refus doit lui aussi ne rien déplacer.
            }

            assertThat(payment.getAmountPaid())
                    .as("le montant versé du paiement a bougé").isEqualTo(verseAvant);
            assertThat(payment.getSessionSeries().getId())
                    .as("l'imputation à la série a bougé").isEqualTo(serieAvant);
            assertThat(payment.getGroup().getId())
                    .as("l'imputation au groupe a bougé").isEqualTo(groupeAvant);
            assertThat(payment.getStatus())
                    .as("le statut du paiement a bougé").isEqualTo(statutAvant);
        }
    }

    @Provide
    Arbitrary<BigDecimal> paidAmount() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.00"), new BigDecimal("500.00")).ofScale(2);
    }

    @Provide
    Arbitrary<List<BigDecimal>> refundSequence() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.00"), new BigDecimal("300.00")).ofScale(2)
                .list().ofMinSize(1).ofMaxSize(4);
    }
}
