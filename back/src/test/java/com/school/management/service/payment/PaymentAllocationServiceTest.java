package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.AllocationPlan.SeriesAllocation;
import com.school.management.service.payment.AllocationPlan.SkipReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (exemples, devis et dépôt simulés) de {@link PaymentAllocationService}.
 *
 * <p>Le plan est le cœur de la règle de report : il décide seul où va chaque dinar d'un
 * versement. Les cas ci-dessous couvrent la chaîne complète — imputation simple, plafonnement,
 * cascade sans limite de profondeur, séries écartées — et surtout la <strong>distinction des
 * trois motifs d'écartement</strong>. Les trois donnent le même plafond nul et appellent trois
 * réactions différentes : une série soldée ne demande rien à l'administrateur ; une série sans
 * séance planifiée exige qu'il en crée pour l'ouvrir ; une série dont les séances existent mais
 * ne sont pas facturables à cet étudiant ne se débloquerait pas en en créant d'autres
 * (exigences 5.8, 5.12).</p>
 *
 * <p>Le service étant en lecture seule, aucun test ne vérifie d'écriture : il n'a aucun dépôt
 * d'écriture à sa disposition, et {@code plan} ne peut donc pas en produire.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentAllocationServiceTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long GROUP_ID = 3L;

    @Mock private SessionSeriesRepository sessionSeriesRepository;
    @Mock private PaymentQuoteService paymentQuoteService;

    @InjectMocks private PaymentAllocationService service;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static SessionSeriesEntity series(long id, String name) {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(id);
        series.setName(name);
        return series;
    }

    /**
     * Devis réduit à ce que le plan consomme : les deux décomptes de séances et le plafond.
     *
     * <p>{@code excludedSessions} n'est pas décoratif : associé à un décompte facturable nul,
     * c'est lui qui départage une série vide d'une série dont les séances existent mais ne sont
     * pas facturables à cet étudiant.</p>
     */
    private static PaymentQuoteDTO quote(long seriesId, int billableSessions, int excludedSessions,
                                         String maxPayable) {
        BigDecimal zero = new BigDecimal("0.00");
        return new PaymentQuoteDTO(STUDENT_ID, seriesId,
                billableSessions, billableSessions, excludedSessions, 0,
                zero, zero, zero, zero, zero, zero, zero,
                new BigDecimal(maxPayable), zero, false, false);
    }

    private void givenChain(SessionSeriesEntity... series) {
        when(sessionSeriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(series));
    }

    /** Série dont toutes les séances existantes sont facturables : aucune écartée. */
    private void givenQuote(long seriesId, int billableSessions, String maxPayable) {
        givenQuote(seriesId, billableSessions, 0, maxPayable);
    }

    private void givenQuote(long seriesId, int billableSessions, int excludedSessions,
                            String maxPayable) {
        when(paymentQuoteService.quote(STUDENT_ID, seriesId))
                .thenReturn(quote(seriesId, billableSessions, excludedSessions, maxPayable));
    }

    // ------------------------------------------------------------------
    // Imputation sur la seule série visée
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Montant inférieur au plafond : une seule allocation, aucun report")
    void amountBelowCeilingProducesSingleDirectAllocation() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"));
        givenQuote(10L, 8, "240.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("100.00"));

        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.unplaceable()).isEqualByComparingTo("0.00");
        assertThat(plan.allocations()).singleElement().satisfies(allocation -> {
            assertThat(allocation.seriesId()).isEqualTo(10L);
            assertThat(allocation.seriesName()).isEqualTo("Sept 2025");
            assertThat(allocation.amount()).isEqualByComparingTo("100.00");
            assertThat(allocation.carriedOver()).isFalse();
        });
        // La série suivante n'est jamais interrogée : le reliquat est nul avant d'y arriver.
        verify(paymentQuoteService).quote(STUDENT_ID, 10L);
        assertThat(plan.skipped()).isEmpty();
    }

    @Test
    @DisplayName("Montant égal au plafond : une allocation, reliquat nul")
    void amountEqualToCeilingLeavesNoRemainder() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"));
        givenQuote(10L, 8, "240.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("240.00"));

        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.allocations()).singleElement()
                .extracting(SeriesAllocation::amount, SeriesAllocation::carriedOver)
                .containsExactly(new BigDecimal("240.00"), false);
        assertThat(plan.totalAllocated()).isEqualByComparingTo("240.00");
    }

    // ------------------------------------------------------------------
    // Report et cascade
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Dépassement avec série suivante disponible : deux allocations dont une reportée")
    void overflowIsCarriedOverToNextSeries() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"));
        givenQuote(10L, 8, "240.00");
        givenQuote(11L, 8, "240.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("300.00"));

        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.allocations())
                .extracting(SeriesAllocation::seriesId, SeriesAllocation::amount,
                        SeriesAllocation::carriedOver)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, new BigDecimal("240.00"), false),
                        org.assertj.core.groups.Tuple.tuple(11L, new BigDecimal("60.00"), true));
        // Conservation : rien ne se perd entre la saisie et le plan (exigence 4.3).
        assertThat(plan.totalAllocated()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("Cascade sur trois séries, sans limite de profondeur")
    void carryOverCascadesOverThreeSeries() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"), series(12L, "Nov 2025"));
        givenQuote(10L, 8, "240.00");
        givenQuote(11L, 8, "240.00");
        givenQuote(12L, 8, "240.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("600.00"));

        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.allocations())
                .extracting(SeriesAllocation::seriesId, SeriesAllocation::amount,
                        SeriesAllocation::carriedOver)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, new BigDecimal("240.00"), false),
                        org.assertj.core.groups.Tuple.tuple(11L, new BigDecimal("240.00"), true),
                        org.assertj.core.groups.Tuple.tuple(12L, new BigDecimal("120.00"), true));
        assertThat(plan.totalAllocated()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("Série intermédiaire déjà soldée : sautée avec le motif SETTLED, report sur la suivante")
    void settledIntermediateSeriesIsSkipped() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"), series(12L, "Nov 2025"));
        givenQuote(10L, 8, "240.00");
        givenQuote(11L, 8, "0.00");
        givenQuote(12L, 8, "240.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("300.00"));

        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.allocations())
                .extracting(SeriesAllocation::seriesId, SeriesAllocation::amount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, new BigDecimal("240.00")),
                        org.assertj.core.groups.Tuple.tuple(12L, new BigDecimal("60.00")));
        assertThat(plan.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.seriesId()).isEqualTo(11L);
            assertThat(skipped.reason()).isEqualTo(SkipReason.SETTLED);
        });
        // Une série soldée n'est pas bloquante : rien à demander à l'administrateur.
        assertThat(plan.firstBlockingSeries()).isEmpty();
    }

    @Test
    @DisplayName("Étudiant exempté sur la série suivante : plafond nul, le report continue")
    void exemptedNextSeriesDoesNotStopTheCarryOver() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"), series(12L, "Nov 2025"));
        givenQuote(10L, 8, "240.00");
        // Exempté à 100 % : coût nul donc plafond nul, mais la série a bien des séances.
        givenQuote(11L, 8, "0.00");
        givenQuote(12L, 8, "120.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("360.00"));

        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.allocations())
                .extracting(SeriesAllocation::seriesId, SeriesAllocation::amount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, new BigDecimal("240.00")),
                        org.assertj.core.groups.Tuple.tuple(12L, new BigDecimal("120.00")));
        assertThat(plan.skipped())
                .extracting(AllocationPlan.SkippedSeries::seriesId,
                        AllocationPlan.SkippedSeries::reason)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(11L, SkipReason.SETTLED));
    }

    // ------------------------------------------------------------------
    // Plans incomplets
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Aucune série suivante : plan incomplet avec reliquat")
    void withoutNextSeriesTheRemainderIsUnplaceable() {
        givenChain(series(10L, "Sept 2025"));
        givenQuote(10L, 8, "240.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("300.00"));

        assertThat(plan.isComplete()).isFalse();
        assertThat(plan.unplaceable()).isEqualByComparingTo("60.00");
        // Le maximum encaissable annoncé à l'administrateur (exigence 5.12).
        assertThat(plan.totalAllocated()).isEqualByComparingTo("240.00");
        assertThat(plan.firstBlockingSeries()).isEmpty();
    }

    @Test
    @DisplayName("Toutes les séries soldées : plan incomplet, aucune allocation")
    void allSeriesSettledProducesEmptyPlan() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"));
        givenQuote(10L, 8, "0.00");
        givenQuote(11L, 8, "0.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("500.00"));

        assertThat(plan.isComplete()).isFalse();
        assertThat(plan.allocations()).isEmpty();
        assertThat(plan.totalAllocated()).isEqualByComparingTo("0.00");
        assertThat(plan.unplaceable()).isEqualByComparingTo("500.00");
        assertThat(plan.skipped())
                .extracting(AllocationPlan.SkippedSeries::reason)
                .containsExactly(SkipReason.SETTLED, SkipReason.SETTLED);
        // Aucune série bloquante : le refus doit dire « toutes les séries sont soldées ».
        assertThat(plan.firstBlockingSeries()).isEmpty();
    }

    @Test
    @DisplayName("Série suivante existante mais sans aucune séance planifiée : "
            + "écartée comme NO_SESSIONS_PLANNED")
    void nextSeriesWithoutAnyPlannedSessionIsNotOpen() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"));
        givenQuote(10L, 8, "240.00");
        // Série créée mais vide : ni facturable, ni écartée. Son plafond serait positif, elle
        // n'est pourtant pas ouverte et ne peut rien recevoir (exigence 5.8).
        givenQuote(11L, 0, 0, "240.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("300.00"));

        assertThat(plan.isComplete()).isFalse();
        assertThat(plan.unplaceable()).isEqualByComparingTo("60.00");
        assertThat(plan.allocations())
                .extracting(SeriesAllocation::seriesId)
                .containsExactly(10L);
        assertThat(plan.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.seriesId()).isEqualTo(11L);
            assertThat(skipped.seriesName()).isEqualTo("Oct 2025");
            assertThat(skipped.reason()).isEqualTo(SkipReason.NO_SESSIONS_PLANNED);
        });
        // Le message de refus peut nommer la série à ouvrir, et non parler de série soldée.
        assertThat(plan.firstBlockingSeries()).get()
                .extracting(AllocationPlan.SkippedSeries::seriesName)
                .isEqualTo("Oct 2025");
    }

    @Test
    @DisplayName("Série suivante avec des séances, mais aucune facturable à cet étudiant : "
            + "écartée comme NO_BILLABLE_SESSION_FOR_STUDENT, jamais comme série vide")
    void nextSeriesWithSessionsButNoneBillableToTheStudentIsDistinguished() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"));
        givenQuote(10L, 8, "240.00");
        // Quatre séances existent, toutes antérieures à l'inscription et non suivies : elles
        // sont écartées (exigence 1.3). Créer une cinquième séance dans le passé n'ouvrirait
        // rien — c'est ce qui distingue ce motif de la série vide.
        givenQuote(11L, 0, 4, "240.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("300.00"));

        assertThat(plan.isComplete()).isFalse();
        assertThat(plan.unplaceable()).isEqualByComparingTo("60.00");
        assertThat(plan.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.seriesId()).isEqualTo(11L);
            assertThat(skipped.seriesName()).isEqualTo("Oct 2025");
            assertThat(skipped.reason()).isEqualTo(SkipReason.NO_BILLABLE_SESSION_FOR_STUDENT);
        });
        // La série est bien bloquante : c'est elle que le message doit nommer, avec l'action
        // corrective propre à ce motif.
        assertThat(plan.firstBlockingSeries()).get()
                .extracting(AllocationPlan.SkippedSeries::reason)
                .isEqualTo(SkipReason.NO_BILLABLE_SESSION_FOR_STUDENT);
    }

    @Test
    @DisplayName("Série soldée puis série non ouverte : le motif retenu est celui de la série bloquante")
    void blockingSeriesIsPreferredOverSettledOneInTheRefusalReason() {
        givenChain(series(10L, "Sept 2025"), series(11L, "Oct 2025"), series(12L, "Nov 2025"));
        givenQuote(10L, 8, "0.00");
        givenQuote(11L, 0, 0, "240.00");
        givenQuote(12L, 8, "0.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("240.00"));

        assertThat(plan.allocations()).isEmpty();
        assertThat(plan.unplaceable()).isEqualByComparingTo("240.00");
        assertThat(plan.skipped())
                .extracting(AllocationPlan.SkippedSeries::reason)
                .containsExactly(SkipReason.SETTLED, SkipReason.NO_SESSIONS_PLANNED,
                        SkipReason.SETTLED);
        assertThat(plan.firstBlockingSeries()).get()
                .extracting(AllocationPlan.SkippedSeries::seriesId)
                .isEqualTo(11L);
    }

    // ------------------------------------------------------------------
    // Refus et garde-fous
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Montant nul ou négatif : refus 400 sans consulter le moindre devis")
    void nonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("0.00")))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("strictement positif")
                .extracting(exception -> ((CustomServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("-5.00")))
                .isInstanceOf(CustomServiceException.class);

        verifyNoInteractions(sessionSeriesRepository, paymentQuoteService);
    }

    @Test
    @DisplayName("Série visée absente du groupe : refus 404")
    void startSeriesOutsideTheGroupIsRejected() {
        givenChain(series(10L, "Sept 2025"));

        assertThatThrownBy(() -> service.plan(STUDENT_ID, GROUP_ID, 99L, new BigDecimal("100.00")))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("introuvable dans le groupe")
                .extracting(exception -> ((CustomServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Les séries antérieures à la série visée ne reçoivent jamais de report")
    void seriesBeforeTheTargetedOneAreIgnored() {
        givenChain(series(9L, "Août 2025"), series(10L, "Sept 2025"));
        givenQuote(10L, 8, "240.00");

        AllocationPlan plan = service.plan(STUDENT_ID, GROUP_ID, 10L, new BigDecimal("240.00"));

        assertThat(plan.allocations())
                .extracting(SeriesAllocation::seriesId)
                .containsExactly(10L);
        assertThat(plan.skipped()).isEmpty();
    }
}
