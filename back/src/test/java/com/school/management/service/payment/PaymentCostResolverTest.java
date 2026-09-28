package com.school.management.service.payment;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.DiscountService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (exemples, dépendances simulées) pour {@link PaymentCostResolver}.
 *
 * <p>Couvre les conversions null/zéro à la frontière (prix null → 0, sommes null → 0),
 * les ensembles vides de paiements/remboursements, l'application d'une réduction,
 * la série introuvable (404) et la traduction d'une {@link IllegalArgumentException}
 * du calculateur en erreur de validation 400.</p>
 *
 * <p>Depuis le passage au prorata (exigences 2.1, 2.2), les deux décomptes passés au
 * calculateur viennent du {@link BillableSessionsResolver} et non plus de
 * {@code series.getTotalSessions()} ni de
 * {@code AttendanceRepository.countPresentForStudentAndSeries}. Les séries construites ici
 * conservent un {@code total_sessions} pour montrer qu'il n'est plus la source du coût : la
 * section « Prorata » l'exploite explicitement.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentCostResolverTest {

    private static final Long STUDENT_ID = 1L;
    private static final Long SERIES_ID = 10L;

    @Mock private SessionSeriesRepository sessionSeriesRepository;
    @Mock private BillableSessionsResolver billableSessionsResolver;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private DiscountService discountService;

    @InjectMocks private PaymentCostResolver resolver;

    private SessionSeriesEntity series(int totalSessions, Double priceValue) {
        GroupEntity group = new GroupEntity();
        if (priceValue != null) {
            PricingEntity pricing = new PricingEntity();
            pricing.setPrice(priceValue);
            group.setPrice(pricing);
        }
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setGroup(group);
        s.setTotalSessions(totalSessions);
        return s;
    }

    /**
     * Décompte facturable simulé : {@code billableCount} séances retenues dont
     * {@code attendedCount} suivies, {@code excludedCount} écartées.
     */
    private void givenBillable(int billableCount, int attendedCount, int excludedCount) {
        when(billableSessionsResolver.resolve(STUDENT_ID, SERIES_ID)).thenReturn(
                new BillableSessions(sessions(1L, billableCount), sessions(100L, excludedCount),
                        attendedCount, true, date("2025-01-01")));
    }

    private void givenBillable(int billableCount, int attendedCount) {
        givenBillable(billableCount, attendedCount, 0);
    }

    /** Séances fictives : seul leur nombre importe pour le calcul du coût. */
    private List<SessionEntity> sessions(long firstId, int count) {
        List<SessionEntity> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SessionEntity session = new SessionEntity();
            session.setId(firstId + i);
            list.add(session);
        }
        return List.copyOf(list);
    }

    private static Date date(String isoDate) {
        return Date.from(LocalDate.parse(isoDate).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @BeforeEach
    void defaultDiscount() {
        lenient().when(discountService.resolveRate(anyLong(), anyLong()))
                .thenReturn(new BigDecimal("0.00"));
    }

    // ------------------------------------------------------------------
    // calculatorFor
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Série introuvable → CustomServiceException 404")
    void seriesNotFound() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.calculatorFor(STUDENT_ID, SERIES_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(billableSessionsResolver, never()).resolve(STUDENT_ID, SERIES_ID);
    }

    @Test
    @DisplayName("Prix résolu depuis le groupe : due = suivies × prix × (1-taux)")
    void resolvesPriceFromGroup() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(8, 3);

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        assertThat(calc.monthTotalCost()).isEqualByComparingTo(new BigDecimal("240.00"));
        assertThat(calc.amountDueSoFar()).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    @DisplayName("Groupe null → prix traité comme 0")
    void groupNullPriceIsZero() {
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setGroup(null);
        s.setTotalSessions(8);
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));
        givenBillable(8, 5);

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        assertThat(calc.monthTotalCost()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(calc.amountDueSoFar()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("PricingEntity null sur le groupe → prix traité comme 0")
    void pricingNullIsZero() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, null)));
        givenBillable(8, 4);

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        assertThat(calc.monthTotalCost()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Valeur de prix null (PricingEntity présent mais price==null) → 0")
    void priceValueNullIsZero() {
        GroupEntity group = new GroupEntity();
        group.setPrice(new PricingEntity()); // price reste null
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setGroup(group);
        s.setTotalSessions(8);
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));
        givenBillable(8, 2);

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        assertThat(calc.amountDueSoFar()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Réduction appliquée : 50% sur due et total")
    void discountApplied() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(8, 4);
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("0.50"));

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        assertThat(calc.monthTotalCost()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(calc.amountDueSoFar()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("Entrées invalides du calculateur (prix négatif) → 400")
    void calculatorIllegalArgumentTranslatedToBadRequest() {
        // Le décompte facturable ne peut plus être négatif (c'est une taille de liste) : la seule
        // entrée invalide restante est un prix négatif en base.
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, -30.0)));
        givenBillable(8, 0);

        assertThatThrownBy(() -> resolver.calculatorFor(STUDENT_ID, SERIES_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ------------------------------------------------------------------
    // Prorata (exigences 2.1, 2.2, 2.4 à 2.7)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Prorata : inscrit avant toutes les séances → coût nominal, comportement inchangé")
    void prorataEnrolledBeforeAllSessionsKeepsNominalCost() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        // Aucune séance écartée : les 8 séances planifiées sont facturables.
        givenBillable(8, 3, 0);

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        assertThat(calc.monthTotalCost()).isEqualByComparingTo(new BigDecimal("240.00"));
        assertThat(calc.amountDueSoFar()).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    @DisplayName("Prorata : inscrit après deux séances non suivies → coût réduit de deux séances")
    void prorataEnrolledAfterTwoUnattendedSessionsReducesCost() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        // 8 séances planifiées, 2 tenues avant l'inscription et non suivies → 6 facturables.
        givenBillable(6, 4, 2);

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        // 6 × 30 = 180, et non 8 × 30 = 240 : les deux séances écartées ne sont pas une dette.
        assertThat(calc.monthTotalCost()).isEqualByComparingTo(new BigDecimal("180.00"));
        assertThat(calc.amountDueSoFar()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("Prorata : étudiant exempté (taux 1.00) → coût et montant dû nuls (exigence 2.6)")
    void prorataFullExemptionGivesZeroCost() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(6, 4, 2);
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("1.00"));

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        assertThat(calc.monthTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(calc.amountDueSoFar()).isEqualByComparingTo(BigDecimal.ZERO);
        // Un exempté n'est jamais en retard, même sans avoir rien versé.
        assertThat(calc.isLate(new BigDecimal("0.00"))).isFalse();
    }

    @Test
    @DisplayName("Prorata : aucune séance facturable → coût et montant dû nuls (exigence 2.7)")
    void prorataNoBillableSessionGivesZeroCost() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        // Étudiant inscrit après la fin de la série : les 8 séances sont écartées.
        givenBillable(0, 0, 8);

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        assertThat(calc.monthTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(calc.amountDueSoFar()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Prorata : soldé au coût au prorata bien qu'inférieur au coût nominal (exigence 2.5)")
    void prorataFullyPaidBelowNominalCost() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(4, 30.0)));
        // Arrivé à la dernière séance d'une série de quatre, et il y était présent.
        givenBillable(1, 1, 3);
        when(paymentRepository.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("30.00"));
        when(refundRepository.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("0.00"));

        PaymentCostResolver.PaymentStatusResult result = resolver.resolve(STUDENT_ID, SERIES_ID);

        // Évalué contre le coût nominal (120.00) il apparaîtrait indéfiniment en retard.
        assertThat(result.monthTotalCost()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(result.late()).isFalse();
        assertThat(result.monthFullyPaid()).isTrue();
    }

    // ------------------------------------------------------------------
    // resolve
    // ------------------------------------------------------------------

    @Test
    @DisplayName("resolve : montant versé = paiements − remboursements ; statuts corrects")
    void resolveNominal() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(8, 4);
        when(paymentRepository.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("150.00"));
        when(refundRepository.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("30.00"));

        PaymentCostResolver.PaymentStatusResult result = resolver.resolve(STUDENT_ID, SERIES_ID);

        // due = 4 × 30 = 120, total = 8 × 30 = 240, payé effectif = 150 − 30 = 120
        assertThat(result.amountDueSoFar()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(result.monthTotalCost()).isEqualByComparingTo(new BigDecimal("240.00"));
        assertThat(result.amountPaid()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(result.late()).isFalse();          // 120 >= 120
        assertThat(result.monthFullyPaid()).isFalse(); // 120 < 240
    }

    @Test
    @DisplayName("resolve : étudiant en retard quand payé < dû")
    void resolveLate() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(8, 4);
        when(paymentRepository.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("50.00"));
        when(refundRepository.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("0.00"));

        PaymentCostResolver.PaymentStatusResult result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.late()).isTrue(); // 50 < 120
    }

    @Test
    @DisplayName("resolve : mois soldé quand payé >= total")
    void resolveFullyPaid() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(8, 8);
        when(paymentRepository.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("240.00"));
        when(refundRepository.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("0.00"));

        PaymentCostResolver.PaymentStatusResult result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.monthFullyPaid()).isTrue();
        assertThat(result.late()).isFalse();
    }

    @Test
    @DisplayName("resolve : sommes null → traitées comme 0 (aucun paiement/remboursement)")
    void resolveNullSumsTreatedAsZero() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(8, 1);
        when(paymentRepository.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID)).thenReturn(null);
        when(refundRepository.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID)).thenReturn(null);

        PaymentCostResolver.PaymentStatusResult result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.amountPaid()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(result.late()).isTrue(); // 0 < 30
    }

    @Test
    @DisplayName("resolve : ensembles vides (sommes à 0) → payé effectif 0")
    void resolveEmptySets() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(8, 0);
        when(paymentRepository.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("0.00"));
        when(refundRepository.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("0.00"));

        PaymentCostResolver.PaymentStatusResult result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.amountPaid()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(result.amountDueSoFar()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(result.late()).isFalse(); // 0 >= 0
    }

    @Test
    @DisplayName("resolve : remboursements > paiements → payé effectif ramené à 0 (garde-fou)")
    void resolveRefundExceedsPaymentsClampedToZero() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(8, 0);
        when(paymentRepository.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("50.00"));
        when(refundRepository.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("80.00"));

        PaymentCostResolver.PaymentStatusResult result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.amountPaid()).isEqualByComparingTo(new BigDecimal("0.00"));
    }
}
