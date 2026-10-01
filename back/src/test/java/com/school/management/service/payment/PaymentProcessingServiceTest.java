package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.EncashmentKind;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
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
import com.school.management.service.payment.PaymentProcessingService.PaymentMeans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Encaissement avec plafonnement et report (tâche 6.3), au travers de l'Encaissement (spec
 * admin-corrections, tâche A.4).
 *
 * <p>Ce qui est vérifié ici n'est pas la répartition — elle appartient au
 * {@link PaymentAllocationService} et y est testée — ni le calcul du cumul et du statut — ils
 * appartiennent à {@link EncashmentService} et sont éprouvés sur une vraie base par
 * {@code EncashmentServiceIntegrationTest}. C'est leur <strong>application</strong> : un seul
 * Encaissement par versement, une Imputation par série du plan avec le bon indicateur de report,
 * chaque report tracé avec son Imputation, et surtout le refus <em>avant</em> toute écriture.</p>
 *
 * <h2>Pourquoi le refus total se teste par l'absence d'interaction</h2>
 * L'exigence 5.11 demande de refuser le versement <strong>en totalité</strong>, y compris la part
 * plaçable. Le plan étant calculé en lecture seule avant la moindre écriture, le test se réduit à
 * constater qu'aucun Encaissement n'a été ouvert, aucune ventilation ni trace de report écrite.
 * C'est plus fort qu'une vérification d'état après annulation : il n'y a rien à annuler, et aucun
 * numéro de reçu n'a été attribué.
 *
 * <h2>Le message de refus est un livrable, pas un détail</h2>
 * Un refus qui n'indique pas l'action corrective laisse l'administrateur sans issue. Les motifs
 * distincts du refus sont donc sous test, chacun avec sa formulation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentProcessingServiceTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long GROUP_ID = 3L;
    private static final Long SERIES_ID = 10L;
    private static final Long NEXT_SERIES_ID = 11L;
    private static final Long SESSION_ID = 40L;
    private static final Date RECEIVED_AT = new Date(1_900_000_000_000L);

    @Mock private PaymentRepository paymentRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private SessionSeriesRepository sessionSeriesRepository;
    @Mock private StudentGroupRepository studentGroupRepository;
    /**
     * Présences de la série : elles rattachent au groupe un étudiant venu en rattrapage sans y
     * être inscrit, second motif d'acceptation d'un versement à côté de l'inscription.
     */
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private PaymentDistributionService distributionService;
    @Mock private PaymentQuoteService paymentQuoteService;
    @Mock private PaymentAllocationService allocationService;
    @Mock private PaymentCarryOverService carryOverService;

    /**
     * Sans clé d'idempotence, ce service est transparent : {@code normalizeKey} rend {@code null}
     * et les recherches de rejeu un optionnel vide. L'idempotence est éprouvée à part par
     * {@link PaymentIdempotencyServiceTest} et de bout en bout par le test du point d'entrée.
     */
    @Mock private PaymentIdempotencyService idempotencyService;

    @Mock private EncashmentService encashmentService;

    /** Sans effet ici : la garde d'année est éprouvée par PaymentProcessingEndpointIntegrationTest. */
    @Mock private com.school.management.service.ReadOnlyYearGuard readOnlyYearGuard;

    private PaymentProcessingService service;

    private StudentEntity student;
    private GroupEntity group;

    /** Lignes de paiement existantes par série. */
    private final Map<Long, PaymentEntity> existingPayments = new HashMap<>();

    /** Imputations demandées à EncashmentService, dans l'ordre. */
    private final List<EncashmentAllocationEntity> imputations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PaymentProcessingService(paymentRepository, studentRepository, groupRepository,
                sessionRepository, sessionSeriesRepository, studentGroupRepository,
                attendanceRepository, distributionService, paymentQuoteService, allocationService,
                carryOverService, idempotencyService, encashmentService, readOnlyYearGuard);
        when(idempotencyService.findReplay(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(idempotencyService.findCatchUpReplay(any(), any(), any(), any())).thenReturn(Optional.empty());

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
                .thenAnswer(invocation -> quote(invocation.getArgument(1), "30.00", "240.00", false));

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

        // L'Encaissement reçoit son numéro et sa date du serveur ; ici, des valeurs fixes.
        when(encashmentService.open(any())).thenAnswer(invocation -> {
            EncashmentService.NewEncashment request = invocation.getArgument(0);
            return EncashmentEntity.builder()
                    .id(500L)
                    .receiptNumber("RECU-2030-0001")
                    .student(request.student())
                    .group(request.group())
                    .targetSeries(request.targetSeries())
                    .amountReceived(request.amount())
                    .kind(request.kind())
                    .receivedAt(RECEIVED_AT)
                    .build();
        });
        when(encashmentService.allocate(any(), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(1);
            EncashmentAllocationEntity imputation = EncashmentAllocationEntity.builder()
                    .id(900L + imputations.size())
                    .encashment(invocation.getArgument(0))
                    .payment(payment)
                    .series(payment.getSessionSeries())
                    .amount(invocation.getArgument(2))
                    .carriedOver(invocation.getArgument(3))
                    .active(true)
                    .build();
            imputations.add(imputation);
            return imputation;
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

    /** Devis réduit à ce que l'encaissement consomme : prix net, coût au prorata et plafond. */
    private static PaymentQuoteDTO quote(Long seriesId, String netPrice, String maxPayable, boolean exempted) {
        BigDecimal zero = new BigDecimal("0.00");
        BigDecimal net = new BigDecimal(netPrice);
        BigDecimal max = new BigDecimal(maxPayable);
        return new PaymentQuoteDTO(STUDENT_ID, seriesId, 8, 8, 0, 0,
                net, zero, net,
                max, zero, zero, max, max, zero, exempted, false);
    }

    private void givenPlan(AllocationPlan plan) {
        when(allocationService.plan(eq(STUDENT_ID), eq(GROUP_ID), eq(SERIES_ID), any(BigDecimal.class)))
                .thenReturn(plan);
    }

    private static AllocationPlan complete(SeriesAllocation... allocations) {
        return new AllocationPlan(List.of(allocations), List.of(), new BigDecimal("0.00"));
    }

    /** Montant imputé sur une série par ce versement. */
    private BigDecimal imputedOn(Long seriesId) {
        return imputations.stream()
                .filter(imputation -> imputation.getSeries().getId().equals(seriesId))
                .map(EncashmentAllocationEntity::getAmount)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune imputation sur la série " + seriesId));
    }

    private EncashmentService.NewEncashment openedEncashment() {
        ArgumentCaptor<EncashmentService.NewEncashment> captor =
                ArgumentCaptor.forClass(EncashmentService.NewEncashment.class);
        verify(encashmentService).open(captor.capture());
        return captor.getValue();
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((CustomServiceException) e).getStatus();
    }

    // ------------------------------------------------------------------
    // L'Encaissement
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un versement accepté ouvre un seul Encaissement : montant, série visée, mode et note")
    void acceptedPaymentOpensOneEncashment() {
        givenPlan(complete(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("100.00"), false)));

        PaymentAllocationResult result = service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 100.0,
                null, new PaymentMeans("cash", "Réglé par le père"));

        EncashmentService.NewEncashment request = openedEncashment();
        assertThat(request.student()).isSameAs(student);
        assertThat(request.group()).isSameAs(group);
        assertThat(request.targetSeries().getId()).isEqualTo(SERIES_ID);
        assertThat(request.amount()).isEqualByComparingTo("100.00");
        assertThat(request.kind()).isEqualTo(EncashmentKind.REGULAR);
        assertThat(request.paymentMethod()).isEqualTo("cash");
        assertThat(request.notes()).isEqualTo("Réglé par le père");

        assertThat(result.encashment().getReceiptNumber()).isEqualTo("RECU-2030-0001");
        // La ligne de paiement porte la date de l'Encaissement, fixée par le serveur.
        assertThat(result.payment().getPaymentDate()).isEqualTo(RECEIVED_AT);
    }

    @Test
    @DisplayName("Versement sous le plafond : une Imputation directe, ventilée, aucun report")
    void paymentBelowCeilingCreditsASingleSeries() {
        givenPlan(complete(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("100.00"), false)));

        PaymentAllocationResult result = service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 100.0);

        assertThat(result.amountAllocated()).isEqualByComparingTo("100.00");
        assertThat(result.carryOvers()).isEmpty();
        assertThat(result.amountCarriedOver()).isEqualByComparingTo("0.00");
        assertThat(imputations).singleElement().satisfies(imputation -> {
            assertThat(imputation.getAmount()).isEqualByComparingTo("100.00");
            assertThat(imputation.getCarriedOver()).isFalse();
        });

        verify(distributionService).distributePayment(any(PaymentEntity.class), eq(SERIES_ID), eq(100.0));
        verifyNoInteractions(carryOverService);
    }

    @Test
    @DisplayName("Ligne de série existante : l'Imputation la crédite, le cumul n'est jamais incrémenté ici")
    void existingSeriesLineIsCreditedThroughAnImputation() {
        PaymentEntity existing = PaymentEntity.builder()
                .student(student).group(group).sessionSeries(series(SERIES_ID))
                .amountPaid(90.00).status("IN_PROGRESS").build();
        existingPayments.put(SERIES_ID, existing);

        givenPlan(complete(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("60.00"), false)));

        service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 60.0);

        verify(encashmentService).allocate(any(EncashmentEntity.class), same(existing),
                eq(new BigDecimal("60.00")), eq(false));
        // Le cumul est la somme des Imputations, recalculée par EncashmentService : incrémenté ici
        // aussi, il compterait deux fois le versement.
        assertThat(existing.getAmountPaid()).isEqualTo(90.00);
        // Seul le montant imputé est ventilé, pas le cumul (exigence 4.4).
        verify(distributionService).distributePayment(same(existing), eq(SERIES_ID), eq(60.0));
    }

    // ------------------------------------------------------------------
    // Versement au-delà du plafond : report
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versement au-delà : Imputation directe au plafond, Imputation reportée du reste, report tracé avec elle")
    void overflowCreditsTheNextSeriesAndRecordsTheCarryOver() {
        givenPlan(complete(
                new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("240.00"), false),
                new SeriesAllocation(NEXT_SERIES_ID, "Série 11", new BigDecimal("60.00"), true)));

        PaymentAllocationResult result = service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 300.0);

        assertThat(imputedOn(SERIES_ID)).isEqualByComparingTo("240.00");
        assertThat(imputedOn(NEXT_SERIES_ID)).isEqualByComparingTo("60.00");
        // Les deux parts viennent du MÊME Encaissement : un versement, un reçu.
        verify(encashmentService).open(any());
        assertThat(imputations).extracting(EncashmentAllocationEntity::getCarriedOver).containsExactly(false, true);

        assertThat(result.amountAllocated()).isEqualByComparingTo("240.00");
        assertThat(result.carryOvers()).singleElement().satisfies(carryOver -> {
            assertThat(carryOver.seriesId()).isEqualTo(NEXT_SERIES_ID);
            assertThat(carryOver.seriesName()).isEqualTo("Série 11");
            assertThat(carryOver.amount()).isEqualByComparingTo("60.00");
        });

        // La trace nomme source et destination (exigence 6.1), porte la date de l'Encaissement
        // et désigne l'Imputation reportée : l'annulation la désactivera avec elle.
        verify(carryOverService).record(eq(STUDENT_ID), eq(SERIES_ID), eq(NEXT_SERIES_ID),
                any(PaymentEntity.class), eq(new BigDecimal("60.00")), eq(RECEIVED_AT), same(imputations.get(1)));
        // Une imputation directe ne produit aucune trace (exigence 6.4).
        verify(carryOverService, never()).record(eq(STUDENT_ID), eq(SERIES_ID), eq(SERIES_ID),
                any(PaymentEntity.class), any(BigDecimal.class), any(Date.class), any());
    }

    @Test
    @DisplayName("Conservation : la somme des Imputations égale le montant du versement")
    void allocatedAmountsSumUpToTheReceivedAmount() {
        givenPlan(complete(
                new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("240.00"), false),
                new SeriesAllocation(NEXT_SERIES_ID, "Série 11", new BigDecimal("240.00"), true),
                new SeriesAllocation(12L, "Série 12", new BigDecimal("120.00"), true)));

        PaymentAllocationResult result = service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 600.0);

        assertThat(result.amountReceived()).isEqualByComparingTo("600.00");
        assertThat(result.amountAllocated().add(result.amountCarriedOver()))
                .isEqualByComparingTo(result.amountReceived());
        assertThat(imputations.stream().map(EncashmentAllocationEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
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
        // L'Encaissement vise toujours la série saisie, même si elle n'a rien reçu.
        assertThat(openedEncashment().targetSeries().getId()).isEqualTo(SERIES_ID);
    }

    @Test
    @DisplayName("L'empreinte d'idempotence est conservée avec l'Encaissement, sans séance")
    void fingerprintIsRememberedWithTheEncashment() {
        givenPlan(complete(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("100.00"), false)));
        when(idempotencyService.normalizeKey("cle-1")).thenReturn("cle-1");

        service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 100.0, "cle-1");

        ArgumentCaptor<PaymentAllocationResult> captor = ArgumentCaptor.forClass(PaymentAllocationResult.class);
        verify(idempotencyService).remember(eq("cle-1"), captor.capture(), isNull());
        assertThat(captor.getValue().encashment().getId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("Rejeu : le résultat original est rendu, sans ouvrir d'Encaissement")
    void replayOpensNoEncashment() {
        PaymentAllocationResult original = new PaymentAllocationResult(STUDENT_ID, GROUP_ID, SERIES_ID,
                new BigDecimal("100.00"), new BigDecimal("100.00"), List.of(), new PaymentEntity(),
                EncashmentEntity.builder().id(42L).build());
        when(idempotencyService.normalizeKey("cle-1")).thenReturn("cle-1");
        when(idempotencyService.findReplay(eq("cle-1"), any(), any(), any(), any())).thenReturn(Optional.of(original));

        assertThat(service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 100.0, "cle-1")).isSameAs(original);

        verifyNoInteractions(encashmentService, allocationService, distributionService, carryOverService);
    }

    // ------------------------------------------------------------------
    // Refus total : aucune écriture
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versement non plaçable en totalité : refus 400, aucun Encaissement ouvert, aucune écriture")
    void unplaceablePaymentIsRefusedWithoutAnyWrite() {
        givenPlan(new AllocationPlan(
                List.of(new SeriesAllocation(SERIES_ID, "Série 10", new BigDecimal("240.00"), false)),
                List.of(new SkippedSeries(NEXT_SERIES_ID, "Oct 2025",
                        SkipReason.NO_SESSIONS_PLANNED)),
                new BigDecimal("60.00")));

        assertThatThrownBy(() -> service.processPayment(STUDENT_ID, GROUP_ID, SERIES_ID, 300.0))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));

        // Rien n'a été écrit : le plan précède l'écriture, il n'y a même rien à annuler, et aucun
        // numéro de reçu n'a été attribué.
        verifyNoInteractions(encashmentService, carryOverService);
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
        verify(distributionService, never()).distributePayment(any(), anyLong(), anyDouble());
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

        // L'échec interrompt la boucle : la série suivante ne reçoit aucune Imputation, et aucune
        // trace de report n'est écrite. L'annulation de l'Encaissement et de l'Imputation déjà
        // faite est assurée par la transaction unique de processPayment (exigences 4.9, 5.5, 5.7).
        assertThat(imputations).extracting(imputation -> imputation.getSeries().getId()).containsExactly(SERIES_ID);
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

        verifyNoInteractions(allocationService, carryOverService, encashmentService);
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

        verifyNoInteractions(allocationService, carryOverService, encashmentService);
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
    }

    // ------------------------------------------------------------------
    // Rattrapage : même Encaissement, même clé (exigence 1.7)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Rattrapage")
    class Rattrapage {

        @BeforeEach
        void givenACatchUpSession() {
            SessionEntity session = new SessionEntity();
            session.setId(SESSION_ID);
            session.setGroup(group);
            session.setSessionSeries(series(SERIES_ID));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            givenCatchUpQuote("2000.00", "2000.00", false);
        }

        private void givenCatchUpQuote(String netPrice, String maxPayable, boolean exempted) {
            when(paymentQuoteService.quote(STUDENT_ID, SERIES_ID)).thenReturn(quote(SERIES_ID, netPrice, maxPayable, exempted));
        }

        @Test
        @DisplayName("Encaissement CATCH_UP sur la série de la séance, une Imputation directe, ni plan ni report")
        void catchUpOpensACatchUpEncashment() {
            PaymentAllocationResult result = service.processCatchUpPayment(STUDENT_ID, SESSION_ID, 2000.0,
                    null, new PaymentMeans("cash", null));

            EncashmentService.NewEncashment request = openedEncashment();
            assertThat(request.kind()).isEqualTo(EncashmentKind.CATCH_UP);
            assertThat(request.targetSeries().getId()).isEqualTo(SERIES_ID);
            assertThat(request.group()).isSameAs(group);
            assertThat(request.amount()).isEqualByComparingTo("2000.00");
            assertThat(request.paymentMethod()).isEqualTo("cash");

            assertThat(imputations).singleElement().satisfies(imputation -> {
                assertThat(imputation.getAmount()).isEqualByComparingTo("2000.00");
                assertThat(imputation.getCarriedOver()).isFalse();
            });
            assertThat(result.amountAllocated()).isEqualByComparingTo("2000.00");
            assertThat(result.carryOvers()).isEmpty();
            verify(distributionService).distributePayment(any(PaymentEntity.class), eq(SERIES_ID), eq(2000.0));
            verifyNoInteractions(allocationService, carryOverService);
        }

        @Test
        @DisplayName("Empreinte conservée avec la séance payée")
        void fingerprintCarriesTheSession() {
            when(idempotencyService.normalizeKey("cle-r")).thenReturn("cle-r");

            service.processCatchUpPayment(STUDENT_ID, SESSION_ID, 2000.0, "cle-r", PaymentMeans.NONE);

            verify(idempotencyService).remember(eq("cle-r"), any(PaymentAllocationResult.class), eq(SESSION_ID));
        }

        @Test
        @DisplayName("Rejeu : le résultat original est rendu, sans rien relire ni écrire")
        void replayWritesNothing() {
            PaymentAllocationResult original = new PaymentAllocationResult(STUDENT_ID, GROUP_ID, SERIES_ID,
                    new BigDecimal("2000.00"), new BigDecimal("2000.00"), List.of(), new PaymentEntity(),
                    EncashmentEntity.builder().id(42L).build());
            when(idempotencyService.normalizeKey("cle-r")).thenReturn("cle-r");
            when(idempotencyService.findCatchUpReplay(eq("cle-r"), eq(STUDENT_ID), eq(SESSION_ID), any()))
                    .thenReturn(Optional.of(original));

            assertThat(service.processCatchUpPayment(STUDENT_ID, SESSION_ID, 2000.0, "cle-r", PaymentMeans.NONE))
                    .isSameAs(original);
            verifyNoInteractions(encashmentService, sessionRepository, distributionService);
        }

        @Test
        @DisplayName("Au-delà du prix net de la séance : refus 400, réduction rappelée, aucun Encaissement")
        void aboveTheSessionPriceIsRefused() {
            givenCatchUpQuote("1500.00", "1500.00", false);

            assertThatThrownBy(() -> service.processCatchUpPayment(STUDENT_ID, SESSION_ID, 2000.0))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("dépasse le coût de la séance (1500.00 DA)")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
            verifyNoInteractions(encashmentService);
        }

        @Test
        @DisplayName("Au-delà de ce qui reste dû sur la série : refus 400 avec le maximum, jamais de report")
        void aboveWhatRemainsDueIsRefusedWithTheMaximum() {
            givenCatchUpQuote("2000.00", "500.00", false);

            assertThatThrownBy(() -> service.processCatchUpPayment(STUDENT_ID, SESSION_ID, 1000.0))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("au maximum 500.00 DA")
                    .hasMessageContaining("ne se reporte pas");
            verifyNoInteractions(encashmentService, allocationService, carryOverService);
        }

        @Test
        @DisplayName("Rien de dû (gratuit ici, réglé ou « à préciser ») : refus 400 qui dit pourquoi")
        void nothingDueIsRefusedWithItsCauses() {
            givenCatchUpQuote("2000.00", "0.00", false);

            assertThatThrownBy(() -> service.processCatchUpPayment(STUDENT_ID, SESSION_ID, 2000.0))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("Rien à encaisser pour ce rattrapage")
                    .hasMessageContaining("rattrapage compensatoire")
                    .hasMessageContaining("« à préciser »");
            verifyNoInteractions(encashmentService);
        }

        @Test
        @DisplayName("Étudiant exempté : refus 400 qui le dit")
        void exemptedStudentIsRefused() {
            givenCatchUpQuote("0.00", "0.00", true);

            assertThatThrownBy(() -> service.processCatchUpPayment(STUDENT_ID, SESSION_ID, 100.0))
                    .hasMessageContaining("exempté");
            verifyNoInteractions(encashmentService);
        }

        @Test
        @DisplayName("Ni inscrit ni présent sur la série : refus 400, aucun Encaissement")
        void strangerToTheGroupIsRefused() {
            when(studentGroupRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());
            when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_ID))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.processCatchUpPayment(STUDENT_ID, SESSION_ID, 2000.0))
                    .hasMessageContaining("ni inscrit");
            verifyNoInteractions(encashmentService);
        }

        @Test
        @DisplayName("Montant nul : refus 400 avant toute lecture")
        void zeroIsRefused() {
            assertThatThrownBy(() -> service.processCatchUpPayment(STUDENT_ID, SESSION_ID, 0.0))
                    .hasMessageContaining("strictement positif");
            verifyNoInteractions(encashmentService, sessionRepository);
        }
    }
}
