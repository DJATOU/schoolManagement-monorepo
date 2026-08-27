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
import net.jqwik.api.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriété du plafond cumulé de remboursement (exigences 7.1, 7.3, 7.7).
 *
 * <p><strong>Ce que cette propriété reproduit.</strong> Le contrôle d'origine comparait chaque
 * demande au seul montant versé du paiement, sans déduire les remboursements déjà accordés. Deux
 * remboursements du montant total d'un même versement étaient donc tous deux acceptés, et la caisse
 * sortait deux fois l'argent entré une fois. Deux exemples choisis à la main passaient à côté :
 * chacun, pris seul, respectait la règle. Il fallait une <em>suite</em> de demandes pour que le
 * défaut apparaisse — c'est exactement ce qu'une propriété sait produire.</p>
 *
 * <p>Les repositories sont mockés, mais l'agrégation est <strong>simulée fidèlement</strong> : le
 * mock de {@code save} accumule les remboursements créés, et la somme des remboursements actifs est
 * calculée sur cette accumulation. Sans cela, la propriété testerait un plafond qui ne bouge jamais
 * et ne prouverait rien.</p>
 */
class RefundCapPropertyTest {

    private static final long PAYMENT_ID = 1L;
    private static final long STUDENT_ID = 2L;
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    // Feature: absence-justification-and-refund-receipts, Property 7: For any sequence of refund
    // requests on the same payment, the sum of accepted amounts never exceeds the payment's paid
    // amount, and any request exceeding the remaining refundable cap is rejected.
    @Property(tries = 100)
    void property7_cumulativeRefundsNeverExceedPaidAmount(
            @ForAll("paidAmount") BigDecimal paid,
            @ForAll("refundSequence") @Size(min = 1, max = 6) List<BigDecimal> demandes) {

        BigDecimal normalizedPaid = paid.setScale(MONEY_SCALE, MONEY_ROUNDING);

        // Magasin en mémoire : c'est lui qui rend l'agrégation crédible d'une demande à l'autre.
        List<RefundEntity> enregistres = new ArrayList<>();

        RefundRepository refundRepository = mock(RefundRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);

        when(refundRepository.save(any(RefundEntity.class))).thenAnswer(inv -> {
            RefundEntity refund = inv.getArgument(0);
            enregistres.add(refund);
            return refund;
        });
        when(refundRepository.sumActiveRefundsForPayment(anyLong())).thenAnswer(inv ->
                enregistres.stream()
                        .map(RefundEntity::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(MONEY_SCALE, MONEY_ROUNDING));

        StudentEntity student = StudentEntity.builder().id(STUDENT_ID).build();
        PaymentEntity payment = PaymentEntity.builder()
                .id(PAYMENT_ID)
                .student(student)
                .amountPaid(normalizedPaid.doubleValue())
                .build();
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        RefundService service = new RefundService(refundRepository, paymentRepository,
                new RefundNumberService(refundRepository));

        for (BigDecimal demande : demandes) {
            BigDecimal dejaRembourse = enregistres.stream()
                    .map(RefundEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(MONEY_SCALE, MONEY_ROUNDING);
            BigDecimal plafond = normalizedPaid.subtract(dejaRembourse);
            BigDecimal normalisee = demande.setScale(MONEY_SCALE, MONEY_ROUNDING);

            boolean acceptable = normalisee.compareTo(new BigDecimal("0.01")) >= 0
                    && normalisee.compareTo(plafond) <= 0;

            try {
                service.create(new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, demande, null, "motif"));
                assertThat(acceptable)
                        .as("demande %s acceptée alors que le plafond restant est %s",
                                normalisee, plafond)
                        .isTrue();
            } catch (CustomServiceException e) {
                assertThat(acceptable)
                        .as("demande %s refusée alors qu'elle tient dans le plafond %s (%s)",
                                normalisee, plafond, e.getMessage())
                        .isFalse();
            }

            // L'invariant 7.7 doit tenir après CHAQUE demande, pas seulement à la fin.
            BigDecimal total = enregistres.stream()
                    .map(RefundEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(total)
                    .as("somme des remboursements actifs (%s) au-delà du montant versé (%s)",
                            total, normalizedPaid)
                    .isLessThanOrEqualTo(normalizedPaid);
        }
    }

    /** Montant versé dans [0.00 ; 1000.00], à l'échelle monétaire. */
    @Provide
    Arbitrary<BigDecimal> paidAmount() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.00"), new BigDecimal("1000.00"))
                .ofScale(MONEY_SCALE);
    }

    /**
     * Suite de demandes dans [0.00 ; 600.00]. La borne haute dépasse volontairement la moitié du
     * versement maximal : c'est ce qui permet à deux demandes successives de franchir le plafond
     * cumulé alors que chacune, seule, tiendrait dans le montant versé.
     */
    @Provide
    Arbitrary<List<BigDecimal>> refundSequence() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.00"), new BigDecimal("600.00"))
                .ofScale(MONEY_SCALE)
                .list().ofMinSize(1).ofMaxSize(6);
    }
}
