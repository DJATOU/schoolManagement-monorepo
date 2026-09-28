package com.school.management.service.payment;

import com.school.management.persistance.PaymentCarryOverEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (exemples, dépôts simulés) de {@link PaymentCarryOverService}.
 *
 * <p>La trace de report doit permettre de justifier un encaissement lors d'un contrôle
 * (exigence 6.1) : le cas nominal vérifie donc que les six informations attendues sont
 * enregistrées, pas seulement que {@code save} a été appelé.</p>
 *
 * <p>Les cas de refus portent tous sur la même idée : une trace ne doit jamais mentir. Un
 * montant nul, négatif ou arrondissant à zéro afficherait dans l'historique un report qui n'a
 * rien crédité ; un report dont la source est aussi la destination afficherait « série A vers
 * série A » alors qu'il s'agirait d'une imputation directe.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentCarryOverServiceTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long SOURCE_SERIES_ID = 20L;
    private static final Long TARGET_SERIES_ID = 21L;
    private static final Date ORIGIN_DATE = date("2025-03-05");

    @Mock private PaymentCarryOverRepository paymentCarryOverRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private SessionSeriesRepository sessionSeriesRepository;

    @InjectMocks private PaymentCarryOverService service;

    // ------------------------------------------------------------------
    // Fabriques de données
    // ------------------------------------------------------------------

    private static Date date(String isoDate) {
        return Date.from(LocalDate.parse(isoDate).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private StudentEntity student() {
        StudentEntity student = new StudentEntity();
        student.setId(STUDENT_ID);
        return student;
    }

    private SessionSeriesEntity series(Long id) {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(id);
        return series;
    }

    private PaymentEntity targetPayment() {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(300L);
        return payment;
    }

    /** Simule les trois lectures du chemin nominal. */
    private void givenExistingReferences() {
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student()));
        when(sessionSeriesRepository.findById(SOURCE_SERIES_ID))
                .thenReturn(Optional.of(series(SOURCE_SERIES_ID)));
        when(sessionSeriesRepository.findById(TARGET_SERIES_ID))
                .thenReturn(Optional.of(series(TARGET_SERIES_ID)));
    }

    private void givenSaveReturnsItsArgument() {
        when(paymentCarryOverRepository.save(any(PaymentCarryOverEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------------
    // Enregistrement nominal (exigence 6.1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Enregistrement nominal : étudiant, série source, série destination, ligne de "
            + "paiement, montant et date du versement d'origine sont tracés")
    void recordsAllTraceabilityFields() {
        givenExistingReferences();
        givenSaveReturnsItsArgument();
        PaymentEntity payment = targetPayment();

        PaymentCarryOverEntity saved = service.record(STUDENT_ID, SOURCE_SERIES_ID,
                TARGET_SERIES_ID, payment, new BigDecimal("6000.00"), ORIGIN_DATE);

        ArgumentCaptor<PaymentCarryOverEntity> captor =
                ArgumentCaptor.forClass(PaymentCarryOverEntity.class);
        verify(paymentCarryOverRepository).save(captor.capture());
        PaymentCarryOverEntity persisted = captor.getValue();

        assertThat(persisted.getStudent().getId()).isEqualTo(STUDENT_ID);
        assertThat(persisted.getSourceSeries().getId()).isEqualTo(SOURCE_SERIES_ID);
        assertThat(persisted.getTargetSeries().getId()).isEqualTo(TARGET_SERIES_ID);
        assertThat(persisted.getTargetPayment()).isSameAs(payment);
        assertThat(persisted.getAmount()).isEqualByComparingTo("6000.00");
        assertThat(persisted.getOriginPaymentDate()).isEqualTo(ORIGIN_DATE);
        assertThat(saved).isSameAs(persisted);
    }

    @Test
    @DisplayName("Le montant est ramené à l'échelle monétaire (2 décimales, HALF_UP)")
    void normalizesAmountToMoneyScale() {
        givenExistingReferences();
        givenSaveReturnsItsArgument();

        PaymentCarryOverEntity saved = service.record(STUDENT_ID, SOURCE_SERIES_ID,
                TARGET_SERIES_ID, targetPayment(), new BigDecimal("1500.005"), ORIGIN_DATE);

        // HALF_UP sur la troisième décimale : 1500.005 devient 1500.01, jamais 1500.00
        assertThat(saved.getAmount()).isEqualByComparingTo("1500.01");
        assertThat(saved.getAmount().scale()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Refus d'un montant nul ou négatif
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un montant nul est refusé sans rien écrire")
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> service.record(STUDENT_ID, SOURCE_SERIES_ID, TARGET_SERIES_ID,
                targetPayment(), BigDecimal.ZERO, ORIGIN_DATE))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("strictement positif")
                .extracting(e -> ((CustomServiceException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(paymentCarryOverRepository);
    }

    @Test
    @DisplayName("Un montant négatif est refusé sans rien écrire")
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> service.record(STUDENT_ID, SOURCE_SERIES_ID, TARGET_SERIES_ID,
                targetPayment(), new BigDecimal("-10.00"), ORIGIN_DATE))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("strictement positif");

        verifyNoInteractions(paymentCarryOverRepository);
    }

    @Test
    @DisplayName("Un montant qui s'arrondit à zéro est refusé : la trace serait vide")
    void rejectsAmountRoundingToZero() {
        assertThatThrownBy(() -> service.record(STUDENT_ID, SOURCE_SERIES_ID, TARGET_SERIES_ID,
                targetPayment(), new BigDecimal("0.004"), ORIGIN_DATE))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("strictement positif");

        verify(paymentCarryOverRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Cohérence des arguments
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un report dont la destination est la série source est refusé")
    void rejectsCarryOverOntoSourceSeries() {
        assertThatThrownBy(() -> service.record(STUDENT_ID, SOURCE_SERIES_ID, SOURCE_SERIES_ID,
                targetPayment(), new BigDecimal("500.00"), ORIGIN_DATE))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("propre série source");

        verifyNoInteractions(paymentCarryOverRepository);
    }

    @Test
    @DisplayName("Une ligne de paiement absente est refusée : le report ne créditerait rien")
    void rejectsMissingTargetPayment() {
        assertThatThrownBy(() -> service.record(STUDENT_ID, SOURCE_SERIES_ID, TARGET_SERIES_ID,
                null, new BigDecimal("500.00"), ORIGIN_DATE))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("ligne de paiement");

        verifyNoInteractions(paymentCarryOverRepository);
    }

    @Test
    @DisplayName("Une date de versement d'origine absente est refusée (exigence 6.1)")
    void rejectsMissingOriginPaymentDate() {
        assertThatThrownBy(() -> service.record(STUDENT_ID, SOURCE_SERIES_ID, TARGET_SERIES_ID,
                targetPayment(), new BigDecimal("500.00"), null))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("date du versement d'origine");

        verifyNoInteractions(paymentCarryOverRepository);
    }

    // ------------------------------------------------------------------
    // Références introuvables
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un étudiant introuvable produit une 404 sans écriture")
    void rejectsUnknownStudent() {
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(STUDENT_ID, SOURCE_SERIES_ID, TARGET_SERIES_ID,
                targetPayment(), new BigDecimal("500.00"), ORIGIN_DATE))
                .isInstanceOf(CustomServiceException.class)
                .extracting(e -> ((CustomServiceException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(paymentCarryOverRepository);
    }

    @Test
    @DisplayName("Une série destination introuvable produit une 404 sans écriture")
    void rejectsUnknownTargetSeries() {
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student()));
        when(sessionSeriesRepository.findById(SOURCE_SERIES_ID))
                .thenReturn(Optional.of(series(SOURCE_SERIES_ID)));
        when(sessionSeriesRepository.findById(TARGET_SERIES_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(STUDENT_ID, SOURCE_SERIES_ID, TARGET_SERIES_ID,
                targetPayment(), new BigDecimal("500.00"), ORIGIN_DATE))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("Série destination introuvable");

        verifyNoInteractions(paymentCarryOverRepository);
    }
}
