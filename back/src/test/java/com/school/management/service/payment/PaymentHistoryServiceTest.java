package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentCarryOverDTO;
import com.school.management.dto.payment.StudentPaymentHistoryDTO;
import com.school.management.persistance.PaymentCarryOverEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Restitution des reports dans l'historique de paiement (exigences 6.2, 6.4).
 *
 * <p>Deux choses sont vérifiées, et la seconde est la plus facile à perdre : que chaque report soit
 * restitué avec son montant et ses <strong>deux séries nommées</strong>, et qu'une série créditée
 * par report ne présente pas ce montant comme s'il avait été saisi sur elle. Un historique qui
 * confond les deux affiche un encaissement sans origine.</p>
 */
class PaymentHistoryServiceTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long SOURCE_SERIES_ID = 10L;
    private static final Long TARGET_SERIES_ID = 11L;
    private static final Long SOURCE_PAYMENT_ID = 100L;
    private static final Long TARGET_PAYMENT_ID = 101L;

    private PaymentRepository paymentRepository;
    private PaymentCarryOverRepository paymentCarryOverRepository;
    private PaymentHistoryService service;

    private StudentEntity student;
    private SessionSeriesEntity sourceSeries;
    private SessionSeriesEntity targetSeries;
    private PaymentEntity sourcePayment;
    private PaymentEntity targetPayment;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        paymentCarryOverRepository = mock(PaymentCarryOverRepository.class);
        service = new PaymentHistoryService(paymentRepository, paymentCarryOverRepository);

        student = new StudentEntity();
        student.setId(STUDENT_ID);

        sourceSeries = series(SOURCE_SERIES_ID, "Novembre 2026 - 1");
        targetSeries = series(TARGET_SERIES_ID, "Novembre 2026 - 2");

        sourcePayment = payment(SOURCE_PAYMENT_ID, sourceSeries, 4000.0, "COMPLETED");
        targetPayment = payment(TARGET_PAYMENT_ID, targetSeries, 2000.0, "IN_PROGRESS");
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private SessionSeriesEntity series(Long id, String name) {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(id);
        series.setName(name);
        return series;
    }

    private PaymentEntity payment(Long id, SessionSeriesEntity series, double amountPaid, String status) {
        PaymentEntity payment = PaymentEntity.builder()
                .student(student)
                .sessionSeries(series)
                .amountPaid(amountPaid)
                .status(status)
                .paymentDate(new Date())
                .build();
        payment.setId(id);
        return payment;
    }

    private PaymentCarryOverEntity carryOver(Long id, String amount) {
        PaymentCarryOverEntity carryOver = PaymentCarryOverEntity.builder()
                .student(student)
                .sourceSeries(sourceSeries)
                .targetSeries(targetSeries)
                .targetPayment(targetPayment)
                .amount(new BigDecimal(amount))
                .originPaymentDate(new Date())
                .build();
        carryOver.setId(id);
        return carryOver;
    }

    private void givenPayments(PaymentEntity... payments) {
        when(paymentRepository.findActiveByStudentIdOrderByPaymentDateDesc(STUDENT_ID))
                .thenReturn(List.of(payments));
    }

    private void givenCarryOvers(PaymentCarryOverEntity... carryOvers) {
        when(paymentCarryOverRepository.findByStudentIdAndActiveTrueOrderByIdAsc(STUDENT_ID))
                .thenReturn(List.of(carryOvers));
    }

    // ------------------------------------------------------------------
    // Exigence 6.2 : chaque report avec son montant et ses deux séries
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Chaque report est restitué avec son montant, sa série source et sa série destination")
    void carryOversAreRestitutedWithBothSeries() {
        givenPayments(sourcePayment, targetPayment);
        givenCarryOvers(carryOver(1L, "2000.00"));

        StudentPaymentHistoryDTO history = service.getStudentPaymentHistory(STUDENT_ID);

        assertThat(history.studentId()).isEqualTo(STUDENT_ID);
        assertThat(history.carryOvers()).singleElement().satisfies(carryOver -> {
            assertThat(carryOver.amount()).isEqualByComparingTo("2000.00");
            assertThat(carryOver.sourceSeriesId()).isEqualTo(SOURCE_SERIES_ID);
            assertThat(carryOver.sourceSeriesName()).isEqualTo("Novembre 2026 - 1");
            assertThat(carryOver.targetSeriesId()).isEqualTo(TARGET_SERIES_ID);
            assertThat(carryOver.targetSeriesName()).isEqualTo("Novembre 2026 - 2");
            assertThat(carryOver.targetPaymentId()).isEqualTo(TARGET_PAYMENT_ID);
            assertThat(carryOver.originPaymentDate()).isNotNull();
        });
    }

    @Test
    @DisplayName("Aucun report : historique restitué, listes de reports vide")
    void noCarryOverStillReturnsTheHistory() {
        givenPayments(sourcePayment);
        givenCarryOvers();

        StudentPaymentHistoryDTO history = service.getStudentPaymentHistory(STUDENT_ID);

        assertThat(history.carryOvers()).isEmpty();
        assertThat(history.series()).singleElement().satisfies(series -> {
            assertThat(series.amountPaid()).isEqualByComparingTo("4000.00");
            assertThat(series.amountAllocatedDirectly()).isEqualByComparingTo("4000.00");
            assertThat(series.amountReceivedByCarryOver()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    @DisplayName("Étudiant sans versement : historique vide, jamais nul")
    void studentWithoutPayments() {
        givenPayments();
        givenCarryOvers();

        StudentPaymentHistoryDTO history = service.getStudentPaymentHistory(STUDENT_ID);

        assertThat(history.series()).isEmpty();
        assertThat(history.carryOvers()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Exigence 6.4 : imputation directe distinguée d'un montant reçu par report
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Série créditée par report : la part reportée n'est pas présentée comme imputée directement")
    void carriedInAmountIsNotPresentedAsDirectlyAllocated() {
        givenPayments(sourcePayment, targetPayment);
        givenCarryOvers(carryOver(1L, "2000.00"));

        StudentPaymentHistoryDTO history = service.getStudentPaymentHistory(STUDENT_ID);

        var source = history.series().stream()
                .filter(series -> SOURCE_SERIES_ID.equals(series.seriesId())).findFirst().orElseThrow();
        var target = history.series().stream()
                .filter(series -> TARGET_SERIES_ID.equals(series.seriesId())).findFirst().orElseThrow();

        // Série source : le versement a bien été saisi sur elle.
        assertThat(source.amountAllocatedDirectly()).isEqualByComparingTo("4000.00");
        assertThat(source.amountReceivedByCarryOver()).isEqualByComparingTo("0.00");

        // Série destination : la totalité de son cumul vient du report.
        assertThat(target.amountAllocatedDirectly()).isEqualByComparingTo("0.00");
        assertThat(target.amountReceivedByCarryOver()).isEqualByComparingTo("2000.00");
        assertThat(target.seriesName()).isEqualTo("Novembre 2026 - 2");
    }

    @Test
    @DisplayName("Série créditée des deux façons : les deux parts s'additionnent au cumul")
    void directAndCarriedInAmountsSumUpToTheTotal() {
        // 2 000 reçus par report, puis 1 500 saisis directement sur la même série.
        targetPayment.setAmountPaid(3500.0);
        givenPayments(targetPayment);
        givenCarryOvers(carryOver(1L, "2000.00"));

        StudentPaymentHistoryDTO history = service.getStudentPaymentHistory(STUDENT_ID);

        var target = history.series().get(0);
        assertThat(target.amountReceivedByCarryOver()).isEqualByComparingTo("2000.00");
        assertThat(target.amountAllocatedDirectly()).isEqualByComparingTo("1500.00");
        assertThat(target.amountAllocatedDirectly().add(target.amountReceivedByCarryOver()))
                .isEqualByComparingTo(target.amountPaid());
    }

    @Test
    @DisplayName("Reports en cascade sur une même série : les montants s'additionnent")
    void severalCarryOversOnTheSameSeriesAreSummed() {
        targetPayment.setAmountPaid(3000.0);
        givenPayments(targetPayment);
        givenCarryOvers(carryOver(1L, "2000.00"), carryOver(2L, "1000.00"));

        StudentPaymentHistoryDTO history = service.getStudentPaymentHistory(STUDENT_ID);

        var target = history.series().get(0);
        assertThat(target.amountReceivedByCarryOver()).isEqualByComparingTo("3000.00");
        assertThat(target.amountAllocatedDirectly()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Cumul inférieur aux reports (ligne remboursée) : la part directe reste à zéro")
    void directShareIsNeverNegative() {
        // Cas des données antérieures : le cumul a été réduit après coup. Une part directe
        // négative s'afficherait comme une dette imaginaire.
        targetPayment.setAmountPaid(500.0);
        givenPayments(targetPayment);
        givenCarryOvers(carryOver(1L, "2000.00"));

        StudentPaymentHistoryDTO history = service.getStudentPaymentHistory(STUDENT_ID);

        assertThat(history.series().get(0).amountAllocatedDirectly()).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------
    // Cas dégradés
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versement hors série (rattrapage) : la ligne reste exposée sans série")
    void paymentWithoutSeriesIsStillExposed() {
        PaymentEntity withoutSeries = payment(200L, null, 1000.0, "COMPLETED");
        givenPayments(withoutSeries);
        givenCarryOvers();

        StudentPaymentHistoryDTO history = service.getStudentPaymentHistory(STUDENT_ID);

        assertThat(history.series()).singleElement().satisfies(series -> {
            assertThat(series.seriesId()).isNull();
            assertThat(series.seriesName()).isNull();
            assertThat(series.amountPaid()).isEqualByComparingTo("1000.00");
        });
    }

    @Test
    @DisplayName("Montant nul en base : traité comme zéro, jamais comme une absence de ligne")
    void nullAmountsAreTreatedAsZero() {
        PaymentEntity nullAmount = payment(300L, sourceSeries, 0.0, "IN_PROGRESS");
        nullAmount.setAmountPaid(null);
        PaymentCarryOverEntity nullCarryOverAmount = carryOver(1L, "0.00");
        nullCarryOverAmount.setAmount(null);

        givenPayments(nullAmount);
        givenCarryOvers(nullCarryOverAmount);

        StudentPaymentHistoryDTO history = service.getStudentPaymentHistory(STUDENT_ID);

        assertThat(history.series().get(0).amountPaid()).isEqualByComparingTo("0.00");
        assertThat(history.carryOvers().get(0).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Report orphelin (séries ou ligne absentes) : restitué sans faire échouer la lecture")
    void carryOverWithMissingReferencesIsStillRestituted() {
        PaymentCarryOverEntity orphan = carryOver(1L, "2000.00");
        orphan.setStudent(null);
        orphan.setSourceSeries(null);
        orphan.setTargetSeries(null);
        orphan.setTargetPayment(null);

        givenPayments();
        givenCarryOvers(orphan);

        List<PaymentCarryOverDTO> carryOvers = service.getCarryOversForStudent(STUDENT_ID);

        assertThat(carryOvers).singleElement().satisfies(carryOver -> {
            assertThat(carryOver.studentId()).isNull();
            assertThat(carryOver.sourceSeriesId()).isNull();
            assertThat(carryOver.targetSeriesId()).isNull();
            assertThat(carryOver.targetPaymentId()).isNull();
            assertThat(carryOver.amount()).isEqualByComparingTo("2000.00");
        });
    }

    @Test
    @DisplayName("Identifiant d'étudiant nul : rejeté immédiatement")
    void nullStudentIdIsRejected() {
        assertThatThrownBy(() -> service.getStudentPaymentHistory(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.getCarryOversForStudent(null))
                .isInstanceOf(NullPointerException.class);
    }
}
