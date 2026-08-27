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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriété de validation du montant et du motif d'un remboursement (exigences 6.2, 7.4, 7.9).
 *
 * <p>Le cas que cent tirages trouvent et que deux exemples manquent : le montant qui <em>devient</em>
 * nul par arrondi. {@code 0.004} est strictement positif avant arrondi et vaut {@code 0.00} après.
 * Un contrôle appliqué avant l'arrondi le laisserait passer et produirait une pièce de caisse sans
 * objet, avec un numéro de pièce consommé pour rien. D'où l'ordre imposé par l'exigence 7.5 :
 * arrondir, puis valider.</p>
 */
class RefundValidationPropertyTest {

    private static final long PAYMENT_ID = 1L;
    private static final long STUDENT_ID = 2L;
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    private RefundService serviceWithPaid(BigDecimal paid) {
        RefundRepository refundRepository = mock(RefundRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);

        when(refundRepository.saveAndFlush(any(RefundEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(refundRepository.findMaxRankForPrefix(any())).thenReturn(0);
        when(refundRepository.sumActiveRefundsForPayment(anyLong())).thenReturn(BigDecimal.ZERO);

        PaymentEntity payment = PaymentEntity.builder()
                .id(PAYMENT_ID)
                .student(StudentEntity.builder().id(STUDENT_ID).build())
                .amountPaid(paid.doubleValue())
                .build();
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        return new RefundService(refundRepository, paymentRepository,
                new RefundNumberService(refundRepository));
    }

    // Feature: absence-justification-and-refund-receipts, Property 8: For any requested amount below
    // 0.01 after rounding, or any blank reason, creation is rejected; every accepted amount is
    // expressed at the monetary scale.
    @Property(tries = 100)
    void property8_invalidAmountOrReasonIsRejected(
            @ForAll("anyAmount") BigDecimal montant,
            @ForAll("anyReason") String motif) {

        RefundService service = serviceWithPaid(MAX_AMOUNT);
        RefundRequestDTO dto = new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, montant, null, motif);

        BigDecimal arrondi = montant == null
                ? null
                : montant.setScale(2, RoundingMode.HALF_UP);

        boolean montantValide = arrondi != null
                && arrondi.compareTo(MIN_AMOUNT) >= 0
                && arrondi.compareTo(MAX_AMOUNT) <= 0;
        boolean motifValide = motif != null && !motif.isBlank() && motif.strip().length() <= 500;

        if (montantValide && motifValide) {
            RefundEntity refund = service.create(dto);
            assertThat(refund.getAmount()).isEqualByComparingTo(arrondi);
            // Tout montant accepté est à l'échelle monétaire, sans exception (exigence 7.9).
            assertThat(refund.getAmount().scale()).isEqualTo(2);
            assertThat(refund.getReason()).isNotBlank();
        } else {
            try {
                service.create(dto);
                assertThat(false)
                        .as("montant %s / motif %s accepté alors qu'il est invalide", arrondi, motif)
                        .isTrue();
            } catch (CustomServiceException e) {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            } catch (NullPointerException e) {
                // Une requête nulle n'est pas produite par ce générateur.
                assertThat(false).as("NPE inattendu").isTrue();
            }
        }
    }

    /**
     * Montants couvrant les deux bornes et la zone d'arrondi. Les valeurs à trois décimales sont
     * essentielles : ce sont elles qui produisent le montant nul après arrondi.
     */
    @Provide
    Arbitrary<BigDecimal> anyAmount() {
        return Arbitraries.oneOf(
                Arbitraries.bigDecimals()
                        .between(new BigDecimal("-10.000"), new BigDecimal("10.000")).ofScale(3),
                Arbitraries.bigDecimals()
                        .between(new BigDecimal("0.000"), new BigDecimal("0.020")).ofScale(3),
                Arbitraries.bigDecimals()
                        .between(MAX_AMOUNT, new BigDecimal("1000000001.00")).ofScale(2),
                Arbitraries.just(null));
    }

    /** Motifs valides, vides, blancs, absents et trop longs. */
    @Provide
    Arbitrary<String> anyReason() {
        return Arbitraries.oneOf(
                Arbitraries.just("Trop-perçu"),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just(null),
                Arbitraries.just("x".repeat(501)),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(60));
    }
}
