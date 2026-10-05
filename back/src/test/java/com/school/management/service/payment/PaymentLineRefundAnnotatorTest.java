package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailSearchDTO;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Remboursements déduits des lignes de l'écran « Gestion des paiements ».
 *
 * <p>Exemple de référence : trois séances à 800 DA réglées par un versement, 400 DA rendus. La
 * dernière ligne s'affiche 400 DA (800 barrés), les deux autres restent à 800, et le versement passe
 * de « Soldé » à « En cours » : 2 000 DA nets pour 2 400 DA dus.</p>
 */
class PaymentLineRefundAnnotatorTest {

    private static final long PAYMENT_ID = 50L;
    private static final long STUDENT_ID = 4L;
    private static final long SERIES_ID = 12L;
    private static final BigDecimal PRICE_800 = new BigDecimal("800.00");

    private RefundRepository refundRepository;
    private PaymentDetailRepository paymentDetailRepository;
    private PaymentCostResolver paymentCostResolver;
    private PaymentLineRefundAnnotator annotator;

    @BeforeEach
    void setUp() {
        refundRepository = mock(RefundRepository.class);
        paymentDetailRepository = mock(PaymentDetailRepository.class);
        paymentCostResolver = mock(PaymentCostResolver.class);
        annotator = new PaymentLineRefundAnnotator(refundRepository, paymentDetailRepository, paymentCostResolver);
        when(refundRepository.sumActiveRefundsByPayment(anyCollection())).thenReturn(List.of());
        // Coût de la série : trois séances facturables à 800 DA, soit 2 400 DA.
        costIs(3);
    }

    private void costIs(int billableSessions) {
        when(paymentCostResolver.calculatorFor(STUDENT_ID, SERIES_ID)).thenReturn(
                new PaymentCostCalculator(billableSessions, billableSessions, PRICE_800, BigDecimal.ZERO));
    }

    private static Date day(int day) {
        return new Date(1_790_000_000_000L + day * 86_400_000L);
    }

    private static PaymentDetailSearchDTO row(long lineId, double amount, String status) {
        PaymentDetailSearchDTO row = new PaymentDetailSearchDTO();
        row.setId(lineId);
        row.setAmountPaid(amount);
        row.setPaymentId(PAYMENT_ID);
        row.setPaymentStatus(status);
        row.setStudentId(STUDENT_ID);
        row.setSeriesId(SERIES_ID);
        return row;
    }

    private void refunded(String amount) {
        List<Object[]> sums = new ArrayList<>();
        sums.add(new Object[]{PAYMENT_ID, new BigDecimal(amount)});
        when(refundRepository.sumActiveRefundsByPayment(anyCollection())).thenReturn(sums);
    }

    /** Lignes actives du versement : {@code [identifiant, montant, date]}. */
    private void activeLines(Object[]... lines) {
        List<Object[]> rows = new ArrayList<>();
        for (Object[] line : lines) {
            rows.add(new Object[]{PAYMENT_ID, line[0], line[1], line[2]});
        }
        when(paymentDetailRepository.findActiveLinesOfPayments(anyCollection())).thenReturn(rows);
    }

    /** Les trois séances à 800 DA du même versement, saisies le même jour. */
    private List<PaymentDetailSearchDTO> threeLinesOf800(String status) {
        activeLines(new Object[]{3L, 800.0, day(1)}, new Object[]{4L, 800.0, day(1)},
                new Object[]{5L, 800.0, day(1)});
        return List.of(row(5L, 800.0, status), row(4L, 800.0, status), row(3L, 800.0, status));
    }

    @Test
    @DisplayName("aucun remboursement : montant inchangé, statut stocké conservé, ni lignes ni coût lus")
    void noRefund() {
        List<PaymentDetailSearchDTO> rows = List.of(row(3L, 800.0, "COMPLETED"));

        annotator.annotate(rows);

        PaymentDetailSearchDTO line = rows.get(0);
        assertThat(line.getRefundedAmount()).isEqualByComparingTo("0.00");
        assertThat(line.getNetAmount()).isEqualByComparingTo("800.00");
        assertThat(line.getNetAmount().scale()).isEqualTo(2);
        assertThat(line.getPaymentRefunded()).isEqualByComparingTo("0.00");
        assertThat(line.getPaymentStatus()).isEqualTo("COMPLETED");
        verify(paymentDetailRepository, never()).findActiveLinesOfPayments(anyCollection());
        verifyNoInteractions(paymentCostResolver);
    }

    @Test
    @DisplayName("400 DA rendus sur trois séances à 800 : la dernière ligne passe à 400, le versement « En cours »")
    void refundHitsTheLatestLine() {
        refunded("400.00");
        List<PaymentDetailSearchDTO> rows = threeLinesOf800("COMPLETED");

        annotator.annotate(rows);

        assertThat(rows).extracting(PaymentDetailSearchDTO::getNetAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("400"), new BigDecimal("800"), new BigDecimal("800"));
        assertThat(rows).extracting(PaymentDetailSearchDTO::getRefundedAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("400"), BigDecimal.ZERO, BigDecimal.ZERO);
        // Le total remboursé du versement est porté par chacune de ses lignes.
        assertThat(rows).extracting(PaymentDetailSearchDTO::getPaymentRefunded)
                .usingElementComparator(BigDecimal::compareTo)
                .containsOnly(new BigDecimal("400"));
        assertThat(rows).extracting(PaymentDetailSearchDTO::getPaymentStatus).containsOnly("IN_PROGRESS");
    }

    @Test
    @DisplayName("un remboursement plus grand qu'une ligne déborde sur la précédente, sans passer sous zéro")
    void refundSpillsOverToThePreviousLine() {
        refunded("1000.00");
        List<PaymentDetailSearchDTO> rows = threeLinesOf800("COMPLETED");

        annotator.annotate(rows);

        assertThat(rows).extracting(PaymentDetailSearchDTO::getNetAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("600"), new BigDecimal("800"));
        assertThat(rows.get(0).getRefundedAmount()).isEqualByComparingTo("800.00");
        assertThat(rows.get(1).getRefundedAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("« la plus récente » se lit sur la date de versement, puis l'identifiant ; sans date, la ligne est la plus récente")
    void latestIsByPaymentDateThenId() {
        refunded("100.00");
        // L'identifiant 10 est le plus ancien des deux versements datés, mais il est daté du 5 : il
        // est le plus récent. La ligne 12, sans date, passe encore avant lui.
        activeLines(new Object[]{10L, 800.0, day(5)}, new Object[]{11L, 800.0, day(1)},
                new Object[]{12L, 800.0, null});
        List<PaymentDetailSearchDTO> rows = List.of(row(10L, 800.0, "COMPLETED"),
                row(11L, 800.0, "COMPLETED"), row(12L, 800.0, "COMPLETED"));

        annotator.annotate(rows);

        assertThat(rows.get(2).getRefundedAmount()).isEqualByComparingTo("100.00");
        assertThat(rows.get(0).getRefundedAmount()).isEqualByComparingTo("0.00");
        assertThat(rows.get(1).getRefundedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("même date : départage par l'identifiant le plus élevé")
    void sameDateTieBrokenByHighestId() {
        refunded("100.00");
        activeLines(new Object[]{20L, 800.0, day(1)}, new Object[]{21L, 800.0, day(1)});
        List<PaymentDetailSearchDTO> rows = List.of(row(20L, 800.0, "COMPLETED"), row(21L, 800.0, "COMPLETED"));

        annotator.annotate(rows);

        assertThat(rows.get(1).getRefundedAmount()).isEqualByComparingTo("100.00");
        assertThat(rows.get(0).getRefundedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("une page qui ne montre qu'une ligne ne déplace pas le remboursement sur elle")
    void pageDoesNotMoveTheRefundedShare() {
        refunded("400.00");
        activeLines(new Object[]{3L, 800.0, day(1)}, new Object[]{4L, 800.0, day(1)},
                new Object[]{5L, 800.0, day(1)});
        // Seule la première séance est à l'écran (filtre par séance) : le remboursement porte sur la
        // ligne 5, absente de la page, et la ligne 3 reste entière.
        List<PaymentDetailSearchDTO> rows = List.of(row(3L, 800.0, "COMPLETED"));

        annotator.annotate(rows);

        assertThat(rows.get(0).getRefundedAmount()).isEqualByComparingTo("0.00");
        assertThat(rows.get(0).getNetAmount()).isEqualByComparingTo("800.00");
        assertThat(rows.get(0).getPaymentRefunded()).isEqualByComparingTo("400.00");
        assertThat(rows.get(0).getPaymentStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("trop-perçu rendu : le net couvre encore le coût, le versement reste « Soldé »")
    void refundOfAnOverpaymentKeepsCompleted() {
        // Deux séances facturables seulement (1 600 DA), 2 400 DA versés, 400 DA rendus : 2 000 nets.
        costIs(2);
        refunded("400.00");
        List<PaymentDetailSearchDTO> rows = threeLinesOf800("COMPLETED");

        annotator.annotate(rows);

        assertThat(rows).extracting(PaymentDetailSearchDTO::getPaymentStatus).containsOnly("COMPLETED");
    }

    @Test
    @DisplayName("net égal au coût : « Soldé », borne comprise")
    void netEqualToCostIsCompleted() {
        costIs(2);
        refunded("800.00");
        List<PaymentDetailSearchDTO> rows = threeLinesOf800("COMPLETED");

        annotator.annotate(rows);

        assertThat(rows.get(0).getPaymentStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("tout rendu : « Remboursé », et non « En attente »")
    void fullyRefunded() {
        refunded("2400.00");
        List<PaymentDetailSearchDTO> rows = threeLinesOf800("COMPLETED");

        annotator.annotate(rows);

        assertThat(rows).extracting(PaymentDetailSearchDTO::getPaymentStatus)
                .containsOnly(PaymentLineRefundAnnotator.REFUNDED);
        assertThat(rows).extracting(PaymentDetailSearchDTO::getNetAmount)
                .usingElementComparator(BigDecimal::compareTo).containsOnly(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("remboursé au-delà des lignes encore actives : aucune ligne négative, « Remboursé »")
    void refundAboveActiveLinesNeverGoesNegative() {
        refunded("1000.00");
        activeLines(new Object[]{3L, 800.0, day(1)});
        List<PaymentDetailSearchDTO> rows = List.of(row(3L, 800.0, "IN_PROGRESS"));

        annotator.annotate(rows);

        assertThat(rows.get(0).getNetAmount()).isEqualByComparingTo("0.00");
        assertThat(rows.get(0).getRefundedAmount()).isEqualByComparingTo("800.00");
        assertThat(rows.get(0).getPaymentStatus()).isEqualTo(PaymentLineRefundAnnotator.REFUNDED);
    }

    @Test
    @DisplayName("une ligne inactive n'absorbe aucun remboursement")
    void inactiveLineTakesNoShare() {
        refunded("400.00");
        // La ligne 5 est désactivée : absente des lignes actives, c'est la 4 qui porte le remboursement.
        activeLines(new Object[]{3L, 800.0, day(1)}, new Object[]{4L, 800.0, day(1)});
        PaymentDetailSearchDTO inactive = row(5L, 800.0, "COMPLETED");
        inactive.setActive(false);
        List<PaymentDetailSearchDTO> rows = List.of(inactive, row(4L, 800.0, "COMPLETED"));

        annotator.annotate(rows);

        assertThat(rows.get(0).getRefundedAmount()).isEqualByComparingTo("0.00");
        assertThat(rows.get(1).getRefundedAmount()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("versement annulé : son statut « Annulé » prime sur le net")
    void cancelledStaysCancelled() {
        refunded("400.00");
        List<PaymentDetailSearchDTO> rows = threeLinesOf800("CANCELLED");

        annotator.annotate(rows);

        assertThat(rows).extracting(PaymentDetailSearchDTO::getPaymentStatus).containsOnly("CANCELLED");
        verifyNoInteractions(paymentCostResolver);
    }

    @Test
    @DisplayName("coût introuvable : jamais « Soldé », même si le net semble suffire")
    void unknownCostIsNeverCompleted() {
        when(paymentCostResolver.calculatorFor(STUDENT_ID, SERIES_ID))
                .thenThrow(new CustomServiceException("Série introuvable", HttpStatus.NOT_FOUND));
        refunded("1.00");
        List<PaymentDetailSearchDTO> rows = threeLinesOf800("COMPLETED");

        annotator.annotate(rows);

        assertThat(rows.get(0).getPaymentStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("versement sans série : pas de coût consulté, statut « En cours »")
    void noSeriesNoCost() {
        refunded("1.00");
        List<PaymentDetailSearchDTO> rows = threeLinesOf800("COMPLETED");
        rows.forEach(line -> line.setSeriesId(null));

        annotator.annotate(rows);

        assertThat(rows.get(0).getPaymentStatus()).isEqualTo("IN_PROGRESS");
        // any() et non anyLong() : ce dernier ne reconnaît pas un identifiant de série nul, celui
        // qu'un appel fautif transmettrait.
        verify(paymentCostResolver, never()).calculatorFor(any(), any());
    }

    @Test
    @DisplayName("le coût n'est résolu qu'une fois par versement, pas une fois par ligne")
    void costResolvedOncePerPayment() {
        refunded("400.00");

        annotator.annotate(threeLinesOf800("COMPLETED"));

        verify(paymentCostResolver, org.mockito.Mockito.times(1)).calculatorFor(STUDENT_ID, SERIES_ID);
    }

    @Test
    @DisplayName("ligne sans versement : aucune requête, montant net égal au montant")
    void rowWithoutPayment() {
        PaymentDetailSearchDTO orphan = row(9L, 300.0, null);
        orphan.setPaymentId(null);

        annotator.annotate(List.of(orphan));

        assertThat(orphan.getNetAmount()).isEqualByComparingTo("300.00");
        verifyNoInteractions(refundRepository, paymentDetailRepository, paymentCostResolver);
    }

    @Test
    @DisplayName("une somme nulle remontée par la base ne compte pas comme un remboursement")
    void zeroSumIsNotARefund() {
        refunded("0.00");
        List<PaymentDetailSearchDTO> rows = List.of(row(3L, 800.0, "COMPLETED"));

        annotator.annotate(rows);

        assertThat(rows.get(0).getPaymentStatus()).isEqualTo("COMPLETED");
        verify(paymentDetailRepository, never()).findActiveLinesOfPayments(anyCollection());
    }
}
