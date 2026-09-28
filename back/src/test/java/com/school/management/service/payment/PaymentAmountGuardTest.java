package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Garde-fou du montant à la saisie d'un versement, après le passage au report.
 *
 * <p>Ce fichier remplace {@code PaymentOverpaymentGuardTest} : les assertions de plafond y sont
 * devenues fausses par construction. Dépasser le montant dû d'une série n'est plus une erreur
 * mais un <strong>report</strong> sur les séries suivantes, et l'autorité du plafond appartient
 * au {@link PaymentAllocationService}, seul à connaître la chaîne complète des séries. Refuser
 * ici aurait empêché le report d'être même envisagé (exigence 4.6).</p>
 *
 * <p>Ce qui reste sous test : le refus du montant nul ou négatif et la <b>justesse de ses
 * messages contextuels</b>. « Le montant doit être positif » est exact mais inutile — dans la
 * plupart des cas l'administrateur a saisi 0 parce qu'il n'y avait de toute façon rien à
 * encaisser, et c'est cette cause que le message doit nommer.</p>
 */
class PaymentAmountGuardTest {

    private static final long STUDENT_ID = 7L;
    private static final long SERIES_ID = 1L;

    private PaymentQuoteService paymentQuoteService;
    private PaymentDistributionService service;

    @BeforeEach
    void setUp() {
        paymentQuoteService = mock(PaymentQuoteService.class);
        service = new PaymentDistributionService(
                mock(SessionRepository.class),
                mock(PaymentDetailRepository.class),
                mock(AttendanceRepository.class),
                paymentQuoteService,
                mock(BillableSessionsResolver.class));
    }

    /**
     * Devis d'un étudiant à 65 % de réduction : 4 séances à 700 DA nets (2 800 DA au total)
     * au lieu de 2 000 DA brut.
     */
    private void givenDiscountedQuote(String amountPaid, String maxPayable) {
        when(paymentQuoteService.quote(STUDENT_ID, SERIES_ID)).thenReturn(new PaymentQuoteDTO(
                STUDENT_ID, SERIES_ID, 4, 4, 0, 3,
                new BigDecimal("2000.00"), new BigDecimal("0.65"), new BigDecimal("700.00"),
                new BigDecimal("2800.00"), new BigDecimal("2100.00"),
                new BigDecimal(amountPaid), new BigDecimal(maxPayable), new BigDecimal(maxPayable),
                new BigDecimal("0.00"), false, false));
    }

    // ------------------------------------------------------------------
    // Le plafond n'est plus refusé ici
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Montant dans le plafond de la série : accepté")
    void amountWithinDiscountedCeilingIsAccepted() {
        givenDiscountedQuote("0.00", "2800.00");

        assertThat(service.canProcessPayment(STUDENT_ID, SERIES_ID, 2800.0)).isTrue();
    }

    @Test
    @DisplayName("Montant au-delà du plafond de la série : accepté, le surplus sera reporté")
    void amountAboveTheSeriesCeilingIsNoLongerRejectedHere() {
        givenDiscountedQuote("0.00", "2800.00");

        // 8 000 DA = 4 × tarif catalogue, soit bien au-delà du dû de la série. Ce montant était
        // refusé ici ; il est désormais plafonné puis reporté par le plan d'allocation, qui seul
        // sait si la chaîne des séries peut l'absorber.
        assertThat(service.canProcessPayment(STUDENT_ID, SERIES_ID, 8000.0)).isTrue();
    }

    @Test
    @DisplayName("Série soldée et montant positif : accepté, le report se chargera de le placer")
    void positiveAmountOnASettledSeriesIsNoLongerRejectedHere() {
        givenDiscountedQuote("2800.00", "0.00");

        // Série soldée : le plan la saute et impute le montant sur la série suivante. Le refus
        // n'intervient qu'en l'absence de série apte, et il est alors porté par l'encaissement.
        assertThat(service.canProcessPayment(STUDENT_ID, SERIES_ID, 100.0)).isTrue();
    }

    @Test
    @DisplayName("Versement partiel sous le reste à payer : accepté")
    void partialPaymentBelowRemainingIsAccepted() {
        givenDiscountedQuote("2100.00", "700.00");

        assertThat(service.canProcessPayment(STUDENT_ID, SERIES_ID, 700.0)).isTrue();
    }

    // ------------------------------------------------------------------
    // Refus du montant nul ou négatif, et justesse du motif annoncé
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Montant nul sur une série due : refus 400 annonçant le reste à payer")
    void zeroAmountIsRejectedWithTheRemainingAmount() {
        givenDiscountedQuote("2100.00", "700.00");

        assertThatThrownBy(() -> service.canProcessPayment(STUDENT_ID, SERIES_ID, 0.0))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("700")
                .extracting(exception -> ((CustomServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Montant négatif sur une série soldée : refus nommant la série soldée")
    void negativeAmountOnASettledSeriesNamesTheCause() {
        givenDiscountedQuote("2800.00", "0.00");

        assertThatThrownBy(() -> service.canProcessPayment(STUDENT_ID, SERIES_ID, -10.0))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("déjà soldée");
    }

    @Test
    @DisplayName("Montant nul et étudiant exempté : refus nommant l'exemption")
    void zeroAmountOnAnExemptedStudentNamesTheExemption() {
        when(paymentQuoteService.quote(STUDENT_ID, SERIES_ID)).thenReturn(new PaymentQuoteDTO(
                STUDENT_ID, SERIES_ID, 4, 4, 0, 4,
                new BigDecimal("2000.00"), new BigDecimal("1.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), true, false));

        assertThatThrownBy(() -> service.canProcessPayment(STUDENT_ID, SERIES_ID, 0.0))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("exempté");
    }
}
