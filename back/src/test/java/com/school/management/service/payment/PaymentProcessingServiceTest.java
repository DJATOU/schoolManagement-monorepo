package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.AllocationPlan.SeriesAllocation;
import com.school.management.service.payment.AllocationPlan.SkipReason;
import com.school.management.service.payment.AllocationPlan.SkippedSeries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Encaissement avec plafonnement et report (tâche 6.3).
 *
 * <p>Ce qui est vérifié ici n'est pas la répartition — elle appartient au
 * {@link PaymentAllocationService} et y est testée — mais son <strong>application</strong> :
 * chaque série du plan est-elle créditée du bon montant, chaque report est-il tracé, et surtout
 * le refus intervient-il <em>avant</em> toute écriture.</p>
 *
 * <h2>Pourquoi le refus total se teste par l'absence d'interaction</h2>
 * L'exigence 5.11 demande de refuser le versement <strong>en totalité</strong>, y compris la part
 * plaçable. Le plan étant calculé en lecture seule avant la moindre écriture, le test se réduit à
 * constater qu'aucun dépôt d'écriture, aucune ventilation et aucune trace de report n'ont été
 * touchés. C'est plus fort qu'une vérification d'état après annulation : il n'y a rien à annuler.
 *
 * <h2>Le message de refus est un livrable, pas un détail</h2>
 * Un refus qui n'indique pas l'action corrective laisse l'administrateur sans issue. Deux motifs
 * distincts mènent au refus, et les confondre produirait un message faux : une série sans séance
 * demande qu'on crée ses séances, une chaîne entièrement soldée demande une nouvelle série ou un
 * montant plus petit. Les deux formulations sont donc sous test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentProcessingServiceTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long GROUP_ID = 3L;
    private static final Long SERIES_ID = 10L;
    private static final Long NEXT_SERIES_ID = 11L;

    @Mock private PaymentRepository paymentRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private SessionSeriesRepository sessionSeriesRepository;
    @Mock private StudentGroupRepository studentGroupRepository;
    @Mock private PaymentDistributionService distributionService;
    @Mock private PaymentQuoteService paymentQuoteService;
    @Mock private PaymentAllocationService allocationService;
    @Mock private PaymentCarryOverService carryOverService;

    private PaymentProcessingService service;

    private StudentEntity student;
    private GroupEntity group;

    /** Lignes de paiement existantes par série, pour observer le cumul crédité. */
    private final Map<Long, PaymentEntity> existingPayments = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new PaymentProcessingService(paymentRepository, studentRepository, groupRepository,
                sessionRepository, sessionSeriesRepository, studentGroupRepository,
                distributionService, paymentQuoteService, allocationService, carryOverService);

        student = new StudentEntity();
        student.setId(STUDENT_ID);

        group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("Math 1ère B");

        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(sessionSeriesRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(series(invocation.getArgument(0))));

        StudentGroupEntity enrolment = new StudentGroupEntity();
        enrolment.setStudent(student);
        enrolment.setGroup(group);
        when(studentGroupRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(enrolment));

        // Le garde-fou du montant nul ou négatif interroge le devis de la série visée.
        when(paymentQuoteService.quote(anyLong(), anyLong()))
                .thenAnswer(invocation -> quote(invocation.getArgument(1), "240.00"));

        // Aucune ligne de paiement préexistante par défaut : le service en crée une par série.
        when(paymentRepository.findActiveByStudentIdAndSessionSeriesId(eq(STUDENT_ID), anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(existingPayments.get(invocation.getArgument(1))));
        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenAnswer(invocation -> {
                    PaymentEntity saved = invocation.getArgument(0);
                    if (saved.getSessionSeries() != null) {
                        existingPayments.put(saved.getSessionSeries().getId(), saved);
                    }
                    return saved;
                });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static SessionSeriesEntity series(Long id) {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(id);
        series.setName("Série " + id);
        return series;
    }

    /** Devis réduit à ce que l'encaissement consomme : le coût au prorata et le plafond. */
    private static PaymentQuoteDTO quote(Long seriesId, String prorataCost) {
        BigDecimal zero = new BigDecimal("0.00");
        BigDecimal cost = new BigDecimal(prorataCost);
        return new PaymentQuoteDTO(STUDENT_ID, seriesId, 8, 8, 0, 0,
                new BigDecimal("30.00"), zero, new BigDecimal("30.00"),
                cost, zero, zero, cost, cost, zero, false, false);
    }

    private void givenProrataCost(Long seriesId, String prorataCost) {
        when(paymentQuoteService.quote(STUDENT_ID, seriesId)).thenReturn(quote(seriesId, prorataCost));
    }

    private void givenPlan(AllocationPlan plan) {
        when(allocationService.plan(eq(STUDENT_ID), eq(GROUP_ID), eq(SERIES_ID), any(BigDecimal.class)))
                .thenReturn(plan);
    }

    private static AllocationPlan complete(SeriesAllocation... allocations) {
        return new AllocationPlan(List.of(allocations), List.of(), new BigDecimal("0.00"));
    }

    private BigDecimal creditedAmount(Long seriesId) {
        PaymentEntity payment = existingPayments.get(seriesId);
        assertThat(payment).as("ligne de paiement de la série " + seriesId).isNotNull();
        return BigDecimal.valueOf(payment.getAmountPaid()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Versement tenant sur la série visée
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versement sous le plafond : une seule série créditée, aucun report")
    void paymentBelowCeilingCreditsASingleSeries() {
        givenPlan(complete(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("100.00"), false)));

        PaymentAllocationResult result = service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 100.0);

        assertThat(result.amountAllocated()).isEqualByComparingTo("100.00");
        assertThat(result.carryOvers()).isEmpty();
        assertThat(result.amountCarriedOver()).isEqualByComparingTo("0.00");
        assertThat(creditedAmount(SERIES_ID)).isEqualByComparingTo("100.00");

        verify(distributionService).distributePayment(any(PaymentEntity.class), eq(SERIES_ID), eq(100.0));
        verifyNoInteractions(carryOverService);
    }

    @Test
    @DisplayName("Le cumul de la série est incrémenté, jamais remplacé")
    void existingSeriesTotalIsIncremented() {
        PaymentEntity existing = PaymentEntity.builder()
                .student(student).group(group).sessionSeries(series(SERIES_ID))
                .amountPaid(90.00).status("IN_PROGRESS").build();
        existingPayments.put(SERIES_ID, existing);

        givenPlan(complete(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("60.00"), false)));

        service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 60.0);

        assertThat(creditedAmount(SERIES_ID)).isEqualByComparingTo("150.00");
        // Seul le montant imputé est ventilé, pas le cumul (exigence 4.4).
        verify(distributionService).distributePayment(any(PaymentEntity.class), eq(SERIES_ID), eq(60.0));
    }

    // ------------------------------------------------------------------
    // Versement au-delà du plafond : report
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versement au-delà : série visée au plafond, série suivante du reste, report tracé")
    void overflowCreditsTheNextSeriesAndRecordsTheCarryOver() {
        givenPlan(complete(
                new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("240.00"), false),
                new SeriesAllocation(NEXT_SERIES_ID, "Série 11", new BigDecimal("60.00"), true)));

        PaymentAllocationResult result = service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 300.0);

        assertThat(creditedAmount(SERIES_ID)).isEqualByComparingTo("240.00");
        assertThat(creditedAmount(NEXT_SERIES_ID)).isEqualByComparingTo("60.00");

        assertThat(result.amountAllocated()).isEqualByComparingTo("240.00");
        assertThat(result.carryOvers()).singleElement().satisfies(carryOver -> {
            assertThat(carryOver.seriesId()).isEqualTo(NEXT_SERIES_ID);
            assertThat(carryOver.seriesName()).isEqualTo("Série 11");
            assertThat(carryOver.amount()).isEqualByComparingTo("60.00");
        });

        // La trace nomme la série source et la série destination (exigence 6.1).
        verify(carryOverService).record(eq(STUDENT_ID), eq(SERIES_ID), eq(NEXT_SERIES_ID),
                any(PaymentEntity.class), eq(new BigDecimal("60.00")), any(Date.class));
        // Une imputation directe ne produit aucune trace (exigence 6.4).
        verify(carryOverService, never()).record(eq(STUDENT_ID), eq(SERIES_ID), eq(SERIES_ID),
                any(PaymentEntity.class), any(BigDecimal.class), any(Date.class));
    }

    @Test
    @DisplayName("Conservation : la somme des montants imputés égale le montant du versement")
    void allocatedAmountsSumUpToTheReceivedAmount() {
        givenPlan(complete(
                new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("240.00"), false),
                new SeriesAllocation(NEXT_SERIES_ID, "Série 11", new BigDecimal("240.00"), true),
                new SeriesAllocation(12L, "Série 12", new BigDecimal("120.00"), true)));

        PaymentAllocationResult result = service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 600.0);

        assertThat(result.amountReceived()).isEqualByComparingTo("600.00");
        assertThat(result.amountAllocated().add(result.amountCarriedOver()))
                .isEqualByComparingTo(result.amountReceived());
        assertThat(creditedAmount(SERIES_ID).add(creditedAmount(NEXT_SERIES_ID)).add(creditedAmount(12L)))
                .isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("Série visée soldée : tout part en report, la ligne principale est la première créditée")
    void whenTargetedSeriesIsSettledEverythingIsCarriedOver() {
        givenPlan(complete(
                new SeriesAllocation(NEXT_SERIES_ID, "Série 11", new BigDecimal("200.00"), true)));

        PaymentAllocationResult result = service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 200.0);

        assertThat(result.amountAllocated()).isEqualByComparingTo("0.00");
        assertThat(result.amountCarriedOver()).isEqualByComparingTo("200.00");
        assertThat(result.payment().getSessionSeries().getId()).isEqualTo(NEXT_SERIES_ID);
    }

    // ------------------------------------------------------------------
    // Statut évalué contre le coût au prorata
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Étudiant arrivé à la dernière séance et l'ayant réglée : série soldée, pas en cours")
    void statusIsEvaluatedAgainstTheProrataCost() {
        // Une seule séance facturable à 30 DA : le coût nominal de la série (8 × 30 = 240 DA) ne
        // doit pas servir de référence, sans quoi l'étudiant resterait indéfiniment « en cours ».
        givenProrataCost(SERIES_ID, "30.00");
        givenPlan(complete(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("30.00"), false)));

        service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 30.0);

        assertThat(existingPayments.get(SERIES_ID).getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Versement partiel : la ligne reste en cours")
    void partialPaymentLeavesTheLineInProgress() {
        givenProrataCost(SERIES_ID, "240.00");
        givenPlan(complete(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("100.00"), false)));

        service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 100.0);

        assertThat(existingPayments.get(SERIES_ID).getStatus()).isEqualTo("IN_PROGRESS");
    }

    // ------------------------------------------------------------------
    // Refus total : aucune écriture
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versement non plaçable en totalité : refus 400 et aucune écriture")
    void unplaceablePaymentIsRefusedWithoutAnyWrite() {
        givenPlan(new AllocationPlan(
                List.of(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("240.00"), false)),
                List.of(new SkippedSeries(NEXT_SERIES_ID, "Oct 2025",
                        SkipReason.NO_SESSIONS_PLANNED)),
                new BigDecimal("60.00")));

        assertThatThrownBy(() -> service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 300.0))
                .isInstanceOf(CustomServiceException.class)
                .extracting(exception -> ((CustomServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Rien n'a été écrit : le plan précède l'écriture, il n'y a même rien à annuler.
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
        verify(distributionService, never()).distributePayment(any(), anyLong(), anyDouble());
        verifyNoInteractions(carryOverService);
    }

    @Test
    @DisplayName("Refus, série vide : le message nomme la série à ouvrir et le maximum encaissable")
    void refusalMessageNamesTheSeriesToOpenAndTheMaximum() {
        givenPlan(new AllocationPlan(
                List.of(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("240.00"), false)),
                List.of(new SkippedSeries(NEXT_SERIES_ID, "Oct 2025",
                        SkipReason.NO_SESSIONS_PLANNED)),
                new BigDecimal("60.00")));

        assertThatThrownBy(() -> service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 300.0))
                .isInstanceOf(CustomServiceException.class)
                // Le maximum réellement encaissable sur la chaîne (exigence 5.12).
                .hasMessageContaining("au maximum 240.00 DA")
                // L'action corrective, la série nommée. Elle n'est exacte que parce que la série
                // est réellement vide.
                .hasMessageContaining("créez d'abord les séances de la série « Oct 2025 » pour l'ouvrir")
                // Et pas les motifs des autres causes : ce serait faux ici.
                .hasMessageNotContaining("déjà soldées")
                .hasMessageNotContaining("aucune n'est facturable");
    }

    @Test
    @DisplayName("Refus, série peuplée mais non facturable à l'étudiant : le message ne conseille "
            + "pas de créer des séances là où il en existe déjà")
    void refusalMessageForSeriesWithSessionsButNoneBillableDoesNotAdviseCreatingSessions() {
        givenPlan(new AllocationPlan(
                List.of(),
                List.of(new SkippedSeries(SERIES_ID, "Série 1",
                        SkipReason.NO_BILLABLE_SESSION_FOR_STUDENT)),
                new BigDecimal("8000.00")));

        assertThatThrownBy(() -> service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 8000.0))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("au maximum 0.00 DA")
                // Le fait exact : les séances existent, mais aucune n'est due par cet étudiant.
                .hasMessageContaining("La série « Série 1 » comporte des séances, mais aucune n'est "
                        + "facturable à cet étudiant")
                // L'action réellement corrective.
                .hasMessageContaining("il faut une séance postérieure à son inscription")
                // Le défaut corrigé : annoncer une série sans séance, et conseiller d'en créer.
                .hasMessageNotContaining("ne comporte aucune séance")
                .hasMessageNotContaining("créez d'abord les séances")
                .hasMessageNotContaining("déjà soldées");
    }

    @Test
    @DisplayName("Refus sans série à ouvrir : le message ne parle pas de séances à créer")
    void refusalMessageWithoutUnopenedSeriesDoesNotMentionSessionsToCreate() {
        givenPlan(new AllocationPlan(
                List.of(),
                List.of(new SkippedSeries(SERIES_ID, "Série 10", SkipReason.SETTLED),
                        new SkippedSeries(NEXT_SERIES_ID, "Série 11", SkipReason.SETTLED)),
                new BigDecimal("500.00")));

        assertThatThrownBy(() -> service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 500.0))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("au maximum 0.00 DA")
                .hasMessageContaining("déjà soldées")
                // Piège à éviter : annoncer une série à ouvrir là où il n'y en a aucune.
                .hasMessageNotContaining("pour l'ouvrir");
    }

    // ------------------------------------------------------------------
    // Échec de ventilation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Échec de ventilation sur la série visée : l'erreur remonte, aucun report tracé")
    void distributionFailureOnTheTargetedSeriesPropagates() {
        givenPlan(complete(
                new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("240.00"), false),
                new SeriesAllocation(NEXT_SERIES_ID, "Série 11", new BigDecimal("60.00"), true)));
        doThrow(new IllegalStateException("ventilation impossible"))
                .when(distributionService).distributePayment(any(), eq(SERIES_ID), anyDouble());

        assertThatThrownBy(() -> service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 300.0))
                .isInstanceOf(IllegalStateException.class);

        // L'échec interrompt la boucle : la série suivante n'est ni créditée ni ventilée, et
        // aucune trace de report n'est écrite. L'annulation du montant déjà porté sur la série
        // visée est assurée par la transaction unique de processPayment (exigences 4.9, 5.5, 5.7).
        assertThat(existingPayments.get(NEXT_SERIES_ID)).isNull();
        verify(distributionService, never()).distributePayment(any(), eq(NEXT_SERIES_ID), anyDouble());
        verifyNoInteractions(carryOverService);
    }

    @Test
    @DisplayName("Échec de ventilation sur la série reportée : aucun report tracé")
    void distributionFailureOnTheCarriedOverSeriesPropagates() {
        givenPlan(complete(
                new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("240.00"), false),
                new SeriesAllocation(NEXT_SERIES_ID, "Série 11", new BigDecimal("60.00"), true)));
        doThrow(new IllegalStateException("ventilation impossible"))
                .when(distributionService).distributePayment(any(), eq(NEXT_SERIES_ID), anyDouble());

        assertThatThrownBy(() -> service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 300.0))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(carryOverService);
    }

    // ------------------------------------------------------------------
    // Garde-fous conservés
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Étudiant jamais inscrit au groupe : refus 400 avant tout calcul de plan")
    void neverEnrolledStudentIsRejected() {
        when(studentGroupRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 100.0))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("Math 1ère B");

        verifyNoInteractions(allocationService, carryOverService);
    }

    @Test
    @DisplayName("Montant nul : refus 400 porté par le garde-fou contextuel, sans plan")
    void nonPositiveAmountIsRejectedBeforePlanning() {
        doThrow(new CustomServiceException("Cette série est déjà soldée : il n'y a plus rien à encaisser.",
                HttpStatus.BAD_REQUEST))
                .when(distributionService).canProcessPayment(STUDENT_ID, SERIES_ID, 0.0);

        assertThatThrownBy(() -> service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 0.0))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("déjà soldée");

        verifyNoInteractions(allocationService, carryOverService);
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
    }
}
