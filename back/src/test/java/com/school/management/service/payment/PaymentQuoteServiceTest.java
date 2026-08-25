package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.DiscountService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests du devis de paiement : montant proposé à la saisie et plafond encaissable.
 *
 * <p>Régression couverte à l'origine : le formulaire et le garde-fou serveur calculaient chacun
 * leur coût à partir du tarif catalogue, sans appliquer la réduction. Un étudiant à 65 % de
 * réduction se voyait proposer le plein tarif et pouvait verser près du triple de son dû.</p>
 *
 * <p>Régression couverte depuis le prorata : le plafond s'appuyait sur
 * {@code series.total_sessions}, donc sur des séances tenues avant l'arrivée de l'étudiant dans
 * le groupe. Il autorisait l'encaissement de séances que la facturation ne reconnaissait pas, et
 * l'écart devenait un trop-perçu intégral. Le plafond dérive désormais du coût au prorata, et le
 * décompte des séances suivies vient du même résolveur que le coût.</p>
 */
class PaymentQuoteServiceTest {

    private static final long STUDENT_ID = 7L;
    private static final long SERIES_ID = 1L;

    private SessionSeriesRepository sessionSeriesRepository;
    private AttendanceRepository attendanceRepository;
    private DiscountService discountService;
    private PaymentCostResolver paymentCostResolver;
    private BillableSessionsResolver billableSessionsResolver;
    private PaymentQuoteService service;

    @BeforeEach
    void setUp() {
        sessionSeriesRepository = mock(SessionSeriesRepository.class);
        attendanceRepository = mock(AttendanceRepository.class);
        discountService = mock(DiscountService.class);
        paymentCostResolver = mock(PaymentCostResolver.class);
        billableSessionsResolver = mock(BillableSessionsResolver.class);

        service = new PaymentQuoteService(sessionSeriesRepository, attendanceRepository,
                discountService, paymentCostResolver, billableSessionsResolver);

        GroupEntity group = new GroupEntity();
        group.setId(1L);
        group.setName("Math 1ère A");
        PricingEntity pricing = new PricingEntity();
        pricing.setPrice(2000.0);
        group.setPrice(pricing);

        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        series.setName("Septembre 2025");
        series.setTotalSessions(4);
        series.setGroup(group);

        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(anyLong(), anyLong()))
                .thenReturn(List.of());
        // Par défaut : les 4 séances de la série sont facturables, 3 sont suivies.
        givenBillable(4, 3, 0);
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("0.65"));
    }

    private void givenStatus(String monthTotalCost, String amountDueSoFar, String amountPaid) {
        when(paymentCostResolver.resolve(STUDENT_ID, SERIES_ID)).thenReturn(
                new PaymentCostResolver.PaymentStatusResult(
                        new BigDecimal(monthTotalCost),
                        new BigDecimal(amountDueSoFar),
                        new BigDecimal(amountPaid),
                        false, false));
    }

    /** Décompte facturable simulé, tel que le produirait le résolveur partagé. */
    private void givenBillable(int billableCount, int attendedCount, int excludedCount) {
        when(billableSessionsResolver.resolve(STUDENT_ID, SERIES_ID)).thenReturn(
                new BillableSessions(sessions(1L, billableCount), sessions(1000L, excludedCount),
                        attendedCount, true, null));
    }

    /** Séances fictives : seul leur nombre est exposé par le devis. */
    private static List<SessionEntity> sessions(long firstId, int count) {
        List<SessionEntity> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SessionEntity session = new SessionEntity();
            session.setId(firstId + i);
            list.add(session);
        }
        return List.copyOf(list);
    }

    private AttendanceEntity attendance(boolean catchUp) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setIsCatchUp(catchUp);
        attendance.setIsPresent(true);
        attendance.setActive(true);
        return attendance;
    }

    @Test
    void quote_appliesDiscountToSessionPrice() {
        givenStatus("2800.00", "2100.00", "0.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.grossPricePerSession()).isEqualByComparingTo("2000.00");
        assertThat(quote.discountRate()).isEqualByComparingTo("0.65");
        // 2000 × (1 − 0,65) = 700 : c'est ce prix-là qui doit être proposé à la saisie.
        assertThat(quote.netPricePerSession()).isEqualByComparingTo("700.00");
    }

    @Test
    void quote_maxPayable_isSeriesCostMinusAlreadyPaid() {
        givenStatus("2800.00", "2100.00", "1000.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.monthTotalCost()).isEqualByComparingTo("2800.00");
        assertThat(quote.maxPayable()).isEqualByComparingTo("1800.00");
        assertThat(quote.remainingToPay()).isEqualByComparingTo("1800.00");
        assertThat(quote.existingExcess()).isEqualByComparingTo("0.00");
    }

    @Test
    void quote_settledSeries_maxPayableIsZero() {
        givenStatus("2800.00", "2100.00", "2800.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.maxPayable()).isEqualByComparingTo("0.00");
        assertThat(quote.remainingToPay()).isEqualByComparingTo("0.00");
        assertThat(quote.existingExcess()).isEqualByComparingTo("0.00");
    }

    @Test
    void quote_exemptedStudent_hasNothingToPay() {
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("1.00"));
        givenStatus("0.00", "0.00", "0.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.exempted()).isTrue();
        assertThat(quote.netPricePerSession()).isEqualByComparingTo("0.00");
        assertThat(quote.maxPayable()).isEqualByComparingTo("0.00");
    }

    @Test
    void quote_catchUpOnlyStudent_ceilingIsAmountDueSoFar() {
        // Un rattrapage seul ne doit que les séances suivies : il ne peut pas régler la série
        // complète d'avance.
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(attendance(true), attendance(true)));
        givenStatus("2800.00", "1400.00", "0.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.catchUpOnly()).isTrue();
        assertThat(quote.maxPayable()).isEqualByComparingTo("1400.00");
    }

    @Test
    void quote_catchUpOnlyStudent_maxPayableIsAmountDueSoFarMinusPaid() {
        // Exigence 3.2 : plafond = montant dû à ce jour − versé, et non le coût de la série.
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(attendance(true), attendance(true)));
        givenStatus("2800.00", "1400.00", "500.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.catchUpOnly()).isTrue();
        assertThat(quote.maxPayable()).isEqualByComparingTo("900.00");
    }

    @Test
    void quote_catchUpOnlyStudentPaidBeyondDue_maxPayableIsNeverNegative() {
        // Exigence 3.3 : le calcul produit −600, le devis doit retourner zéro.
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(attendance(true)));
        givenStatus("2800.00", "700.00", "1300.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.catchUpOnly()).isTrue();
        assertThat(quote.maxPayable()).isEqualByComparingTo("0.00");
        // Le versé reste sous le coût au prorata : ce n'est pas un excédent de série.
        assertThat(quote.existingExcess()).isEqualByComparingTo("0.00");
    }

    @Test
    void quote_regularStudentWithAttendance_ceilingIsSeriesCost() {
        // Une présence régulière n'est pas un rattrapage : l'étudiant peut régler son mois.
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(attendance(false), attendance(true)));
        givenStatus("2800.00", "1400.00", "0.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.catchUpOnly()).isFalse();
        assertThat(quote.maxPayable()).isEqualByComparingTo("2800.00");
    }

    @Test
    void quote_noDiscount_netPriceEqualsGrossPrice() {
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(null);
        givenStatus("8000.00", "6000.00", "0.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.discountRate()).isEqualByComparingTo("0.00");
        assertThat(quote.netPricePerSession()).isEqualByComparingTo("2000.00");
    }

    @Test
    void quote_unknownSeries_throwsNotFound() {
        when(sessionSeriesRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quote(STUDENT_ID, 99L))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    void maxPayable_delegatesToQuote() {
        givenStatus("2800.00", "2100.00", "800.00");

        assertThat(service.maxPayable(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("2000.00");
    }

    // ------------------------------------------------------------------
    // Prorata : décomptes exposés et excédent existant (exigences 3.4, 3.5)
    // ------------------------------------------------------------------

    @Test
    void quote_lateEnrolment_exposesBillableAndExcludedCounts() {
        // Étudiant arrivé après deux séances non suivies : 2 facturables sur 4, 2 écartées.
        givenBillable(2, 1, 2);
        givenStatus("1400.00", "700.00", "0.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.billableSessions()).isEqualTo(2);
        assertThat(quote.excludedSessions()).isEqualTo(2);
        // plannedSessions est déprécié et porte le décompte facturable, pas total_sessions (4).
        assertThat(quote.plannedSessions()).isEqualTo(2);
        // Le plafond ne dépasse plus ce que la facturation reconnaît : 2 séances, pas 4.
        assertThat(quote.maxPayable()).isEqualByComparingTo("1400.00");
    }

    @Test
    void quote_attendedSessions_comesFromBillableResolverNotAttendanceRepository() {
        // Exigence 1.5 : le devis et le résolveur de coût doivent retenir la même définition de
        // séance facturable. Un décompte issu de countPresentForStudentAndSeries compterait ici
        // des présences hors des séances facturables.
        givenBillable(3, 2, 1);
        givenStatus("2100.00", "1400.00", "0.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.attendedSessions()).isEqualTo(2);
    }

    @Test
    void quote_historicallyOverPaidSeries_exposesExcessAndZeroCeiling() {
        // Série encaissée avant l'entrée en vigueur du prorata : 4 séances encaissées, 2 seulement
        // sont facturables. Aucune reprise de données : l'excédent reste visible (exigence 3.5).
        givenBillable(2, 2, 2);
        givenStatus("1400.00", "1400.00", "2800.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.existingExcess()).isEqualByComparingTo("1400.00");
        assertThat(quote.maxPayable()).isEqualByComparingTo("0.00");
        assertThat(quote.remainingToPay()).isEqualByComparingTo("0.00");
    }

    @Test
    void quote_catchUpOnlyOverPaidSeries_excessForcesCeilingToZero() {
        // Un rattrapage seul sur-encaissé : le plafond du rattrapage serait déjà nul, mais
        // l'excédent doit être exposé et ne jamais rouvrir l'encaissement.
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(attendance(true)));
        givenBillable(1, 1, 3);
        givenStatus("700.00", "700.00", "900.00");

        PaymentQuoteDTO quote = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(quote.catchUpOnly()).isTrue();
        assertThat(quote.existingExcess()).isEqualByComparingTo("200.00");
        assertThat(quote.maxPayable()).isEqualByComparingTo("0.00");
    }

    @Test
    void quote_calledTwiceWithoutDataChange_producesIdenticalAmounts() {
        // Exigence 3.6 : le devis est une lecture pure, deux appels consécutifs doivent coïncider.
        givenBillable(3, 2, 1);
        givenStatus("2100.00", "1400.00", "700.00");

        PaymentQuoteDTO first = service.quote(STUDENT_ID, SERIES_ID);
        PaymentQuoteDTO second = service.quote(STUDENT_ID, SERIES_ID);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void quotesForGroup_exposesProrataFieldsForEachSeries() {
        // Le point d'entrée groupe doit rester cohérent avec les nouveaux champs : il passe par
        // quote(), donc par le même calcul de plafond.
        SessionSeriesEntity series = sessionSeriesRepository.findById(SERIES_ID).orElseThrow();
        when(sessionSeriesRepository.findByGroupId(1L)).thenReturn(List.of(series));
        givenBillable(2, 1, 2);
        givenStatus("1400.00", "700.00", "1500.00");

        List<PaymentQuoteDTO> quotes = service.quotesForGroup(STUDENT_ID, 1L);

        assertThat(quotes).hasSize(1);
        assertThat(quotes.get(0).billableSessions()).isEqualTo(2);
        assertThat(quotes.get(0).excludedSessions()).isEqualTo(2);
        assertThat(quotes.get(0).existingExcess()).isEqualByComparingTo("100.00");
        assertThat(quotes.get(0).maxPayable()).isEqualByComparingTo("0.00");
    }
}
