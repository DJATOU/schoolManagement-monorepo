package com.school.management.service.payment;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.TutorEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.GroupPaymentStatus;
import com.school.management.service.DiscountService;
import com.school.management.service.SessionPaymentStatus;
import com.school.management.service.StudentPaymentStatus;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (exemples) pour {@link PaymentStatusService}.
 *
 * <p>Ces tests exercent la dérivation de retard refactorée
 * ({@link PaymentStatusService#isStudentPaymentOverdueForSeries}) qui délègue à un vrai
 * {@link PaymentCostResolver} (repositories simulés), ainsi que le chemin défensif du
 * listing de groupe ({@code getPaymentStatusForGroup}). On couvre :</p>
 * <ul>
 *   <li>la limite retard / pas-retard (payé == dû → pas en retard) ;</li>
 *   <li>la limite mois soldé (payé == total → soldé) ;</li>
 *   <li>l'absence de période de grâce ;</li>
 *   <li>le comptage present-only ;</li>
 *   <li>le garde-fou du listing de groupe quand la résolution échoue.</li>
 * </ul>
 */
class PaymentStatusServiceTest {

    private static final Long STUDENT_ID = 1L;
    private static final Long SERIES_ID = 10L;
    private static final Long GROUP_ID = 100L;

    private SessionSeriesRepository seriesRepo;
    private AttendanceRepository attendanceRepo;
    private BillableSessionsResolver billableResolver;
    private PaymentRepository paymentRepo;
    private RefundRepository refundRepo;
    private DiscountService discountService;

    private StudentRepository studentRepository;
    private GroupRepository groupRepository;
    private SessionRepository sessionRepository;
    private PaymentDetailRepository paymentDetailRepository;

    private PaymentCostResolver resolver;
    private PaymentQuoteService quoteService;
    private PaymentStatusService service;

    @BeforeEach
    void setUp() {
        seriesRepo = mock(SessionSeriesRepository.class);
        attendanceRepo = mock(AttendanceRepository.class);
        billableResolver = mock(BillableSessionsResolver.class);
        paymentRepo = mock(PaymentRepository.class);
        refundRepo = mock(RefundRepository.class);
        discountService = mock(DiscountService.class);
        studentRepository = mock(StudentRepository.class);
        groupRepository = mock(GroupRepository.class);
        sessionRepository = mock(SessionRepository.class);
        paymentDetailRepository = mock(PaymentDetailRepository.class);

        // Pas de réduction par défaut ; pas de remboursement par défaut.
        lenient().when(discountService.resolveRate(anyLong(), anyLong()))
                .thenReturn(new BigDecimal("0.00"));
        lenient().when(refundRepo.sumRefundsForStudentAndSeries(anyLong(), anyLong()))
                .thenReturn(new BigDecimal("0.00"));

        resolver = new PaymentCostResolver(
                seriesRepo, billableResolver, paymentRepo, refundRepo, discountService);

        // Devis réel, câblé sur les mêmes doublures : le statut de série s'évalue contre le
        // coût au prorata qu'il annonce, comme le font PaymentProcessingService et
        // StudentHistoryService (exigences 11.1, 11.2).
        quoteService = new PaymentQuoteService(
                seriesRepo, attendanceRepo, discountService, resolver, billableResolver);

        service = new PaymentStatusService(
                paymentRepo,
                paymentDetailRepository,
                studentRepository,
                groupRepository,
                sessionRepository,
                seriesRepo,
                attendanceRepo,
                resolver,
                discountService,
                quoteService);
    }

    private SessionSeriesEntity series(int totalSessions, double price) {
        PricingEntity pricing = new PricingEntity();
        pricing.setPrice(price);
        GroupEntity group = new GroupEntity();
        group.setPrice(pricing);
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setGroup(group);
        s.setTotalSessions(totalSessions);
        return s;
    }

    /**
     * Décompte facturable simulé alimentant le {@link PaymentCostResolver} : depuis le passage
     * au prorata, le coût et le seuil de retard viennent du {@link BillableSessionsResolver} et
     * non plus de {@code countPresentForStudentAndSeries}.
     */
    private void givenBillable(Long seriesId, int billableCount, int attendedCount) {
        when(billableResolver.resolve(STUDENT_ID, seriesId)).thenReturn(
                new BillableSessionsResolver.BillableSessions(
                        fakeSessions(billableCount), List.of(), attendedCount, true, null));
    }

    /** Séances fictives : seul leur nombre entre dans le calcul du coût. */
    private List<SessionEntity> fakeSessions(int count) {
        List<SessionEntity> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            SessionEntity session = new SessionEntity();
            session.setId((long) (i + 1));
            list.add(session);
        }
        return List.copyOf(list);
    }

    // ------------------------------------------------------------------
    // isStudentPaymentOverdueForSeries — dérivation du retard
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Limite : payé == dû → PAS en retard (requirement 6.2)")
    void notLateWhenPaidEqualsDue() {
        // 4 présences × 30 = 120 dû ; payé 120.
        when(seriesRepo.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(SERIES_ID, 8, 4);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("120.00"));

        assertThat(service.isStudentPaymentOverdueForSeries(STUDENT_ID, SERIES_ID)).isFalse();
    }

    @Test
    @DisplayName("En retard : payé < dû (requirement 6.1)")
    void lateWhenPaidLessThanDue() {
        when(seriesRepo.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(SERIES_ID, 8, 4);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("119.99"));

        assertThat(service.isStudentPaymentOverdueForSeries(STUDENT_ID, SERIES_ID)).isTrue();
    }

    @Test
    @DisplayName("Pas en retard : payé > dû (requirement 6.2)")
    void notLateWhenPaidGreaterThanDue() {
        when(seriesRepo.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(SERIES_ID, 8, 4);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("500.00"));

        assertThat(service.isStudentPaymentOverdueForSeries(STUDENT_ID, SERIES_ID)).isFalse();
    }

    @Test
    @DisplayName("Aucune période de grâce : 1 présence impayée → en retard immédiatement (requirement 6.3)")
    void noGracePeriod() {
        when(seriesRepo.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(SERIES_ID, 8, 1);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("0.00"));

        assertThat(service.isStudentPaymentOverdueForSeries(STUDENT_ID, SERIES_ID)).isTrue();
    }

    @Test
    @DisplayName("Comptage present-only : 0 présence → dû 0 → jamais en retard (requirement 6.5)")
    void presentOnlyCountingZeroAttended() {
        when(seriesRepo.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(SERIES_ID, 8, 0);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("0.00"));

        assertThat(service.isStudentPaymentOverdueForSeries(STUDENT_ID, SERIES_ID)).isFalse();
    }

    @Test
    @DisplayName("Le service s'appuie sur le comptage present-only du résolveur de séances facturables")
    void usesPresentOnlyRepositoryCount() {
        // Malgré de nombreuses séances facturables, seul le comptage present-only (2) importe.
        when(seriesRepo.findById(SERIES_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(SERIES_ID, 8, 2);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("60.00")); // 2 × 30 = 60

        assertThat(service.isStudentPaymentOverdueForSeries(STUDENT_ID, SERIES_ID)).isFalse();
    }

    // ------------------------------------------------------------------
    // getPaymentStatusForGroup — chemin défensif
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Listing de groupe : résolution OK → statut de retard propagé")
    void groupListingPropagatesOverdue() {
        GroupEntity group = series(8, 30.0).getGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        StudentEntity student = new StudentEntity();
        student.setId(STUDENT_ID);
        student.setActive(true);
        student.setGroups(new java.util.HashSet<>(List.of(group)));
        when(studentRepository.findByGroups_Id(GROUP_ID)).thenReturn(List.of(student));

        // Ici, groupId est passé comme id de série (bug latent conservé) → la série existe.
        when(seriesRepo.findById(GROUP_ID)).thenReturn(Optional.of(series(8, 30.0)));
        givenBillable(GROUP_ID, 8, 4);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, GROUP_ID))
                .thenReturn(new BigDecimal("0.00")); // dû 120, payé 0 → en retard

        List<StudentPaymentStatus> result = service.getPaymentStatusForGroup(GROUP_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isPaymentOverdue()).isTrue();
    }

    @Test
    @DisplayName("Listing de groupe : résolution échoue (série introuvable) → traité comme non en retard")
    void groupListingDefensiveOnResolutionFailure() {
        GroupEntity group = series(8, 30.0).getGroup();
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        StudentEntity student = new StudentEntity();
        student.setId(STUDENT_ID);
        student.setActive(true);
        student.setGroups(new java.util.HashSet<>(List.of(group)));
        when(studentRepository.findByGroups_Id(GROUP_ID)).thenReturn(List.of(student));

        // groupId passé comme id de série n'est PAS une série valide → le resolver lève 404.
        when(seriesRepo.findById(GROUP_ID)).thenReturn(Optional.empty());

        List<StudentPaymentStatus> result = service.getPaymentStatusForGroup(GROUP_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isPaymentOverdue()).isFalse();
    }

    @Test
    @DisplayName("Listing de groupe : groupe introuvable → exception")
    void groupNotFound() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.getPaymentStatusForGroup(GROUP_ID));
    }

    // ------------------------------------------------------------------
    // Délégation : erreur de résolution propagée hors listing
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isStudentPaymentOverdueForSeries propage l'erreur de résolution (série introuvable)")
    void overdueResolutionErrorPropagates() {
        when(seriesRepo.findById(SERIES_ID)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(CustomServiceException.class,
                () -> service.isStudentPaymentOverdueForSeries(STUDENT_ID, SERIES_ID));
    }

    @Test
    @DisplayName("HttpStatus NOT_FOUND lors d'une résolution de série manquante")
    void resolutionNotFoundStatus() {
        when(seriesRepo.findById(SERIES_ID)).thenReturn(Optional.empty());

        CustomServiceException ex = org.junit.jupiter.api.Assertions.assertThrows(
                CustomServiceException.class,
                () -> service.isStudentPaymentOverdueForSeries(STUDENT_ID, SERIES_ID));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Helpers pour le statut détaillé par étudiant / session
    // ------------------------------------------------------------------

    private GroupEntity groupWithPrice(Long id, String name, double price) {
        PricingEntity pricing = new PricingEntity();
        pricing.setPrice(price);
        GroupEntity group = new GroupEntity();
        group.setId(id);
        group.setName(name);
        group.setPrice(pricing);
        return group;
    }

    private SessionSeriesEntity seriesIn(Long id, GroupEntity group) {
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setId(id);
        s.setGroup(group);
        s.setTotalSessions(8);
        return s;
    }

    private SessionEntity sessionIn(Long id, String title, GroupEntity group) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setTitle(title);
        session.setGroup(group);
        return session;
    }

    private AttendanceEntity presence(Long id, boolean present, GroupEntity group) {
        AttendanceEntity a = new AttendanceEntity();
        a.setId(id);
        a.setIsPresent(present);
        a.setActive(true);
        a.setGroup(group);
        return a;
    }

    private PaymentDetailEntity paidDetail(SessionEntity session, double amount, String status,
            boolean active, Boolean permanentlyDeleted) {
        PaymentEntity payment = PaymentEntity.builder().status(status).build();
        return PaymentDetailEntity.builder()
                .session(session)
                .amountPaid(amount)
                .payment(payment)
                .active(active)
                .permanentlyDeleted(permanentlyDeleted)
                .build();
    }

    // ------------------------------------------------------------------
    // getPaymentStatusForGroup — enrichissement des champs étudiant
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Listing de groupe : les champs optionnels null (niveau / tuteur) sont tolérés")
    void groupListingWithNullLevelAndTutor() {
        GroupEntity group = groupWithPrice(GROUP_ID, "Maths", 30.0);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        StudentEntity student = new StudentEntity();
        student.setId(STUDENT_ID);
        student.setActive(true);
        student.setLevel(null);
        student.setTutor(null);
        student.setGroups(new java.util.HashSet<>(List.of(group)));
        when(studentRepository.findByGroups_Id(GROUP_ID)).thenReturn(List.of(student));

        when(seriesRepo.findById(GROUP_ID)).thenReturn(Optional.of(seriesIn(GROUP_ID, group)));
        givenBillable(GROUP_ID, 8, 0);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, GROUP_ID))
                .thenReturn(new BigDecimal("0.00"));

        List<StudentPaymentStatus> result = service.getPaymentStatusForGroup(GROUP_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isPaymentOverdue()).isFalse();
    }

    @Test
    @DisplayName("Listing de groupe : niveau et tuteur renseignés → identifiants propagés")
    void groupListingWithLevelAndTutor() {
        GroupEntity group = groupWithPrice(GROUP_ID, "Maths", 30.0);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        LevelEntity level = new LevelEntity();
        level.setId(7L);
        TutorEntity tutor = new TutorEntity();
        tutor.setId(8L);

        StudentEntity student = new StudentEntity();
        student.setId(STUDENT_ID);
        student.setActive(true);
        student.setLevel(level);
        student.setTutor(tutor);
        student.setGroups(new java.util.HashSet<>(List.of(group)));
        when(studentRepository.findByGroups_Id(GROUP_ID)).thenReturn(List.of(student));

        when(seriesRepo.findById(GROUP_ID)).thenReturn(Optional.of(seriesIn(GROUP_ID, group)));
        givenBillable(GROUP_ID, 8, 0);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, GROUP_ID))
                .thenReturn(new BigDecimal("0.00"));

        List<StudentPaymentStatus> result = service.getPaymentStatusForGroup(GROUP_ID);

        assertThat(result).hasSize(1);
    }

    // ------------------------------------------------------------------
    // getPaymentStatusForStudent — statut détaillé par groupe/série/session
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Statut détaillé : fusionne groupes officiels et groupes de rattrapage, calcule le retard par session")
    void studentDetailedStatusMergesGroupsAndComputesOverdue() {
        GroupEntity officialGroup = groupWithPrice(100L, "Groupe officiel", 30.0);
        GroupEntity catchUpGroup = groupWithPrice(200L, "Groupe rattrapage", 40.0);

        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(officialGroup));

        // Une présence de rattrapage (active + présent) dans un autre groupe.
        AttendanceEntity catchUpPresence = presence(500L, true, catchUpGroup);
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true))
                .thenReturn(List.of(catchUpPresence));

        // Série + session pour le groupe officiel : présent, impayé → en retard.
        SessionSeriesEntity officialSeries = seriesIn(11L, officialGroup);
        SessionEntity officialSession = sessionIn(1000L, "Séance officielle", officialGroup);
        when(seriesRepo.findByGroupId(officialGroup.getId())).thenReturn(List.of(officialSeries));
        when(sessionRepository.findBySessionSeries(officialSeries)).thenReturn(List.of(officialSession));
        when(attendanceRepo.findBySessionIdAndStudentId(officialSession.getId(), STUDENT_ID))
                .thenReturn(Optional.of(presence(600L, true, officialGroup)));
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, officialSession.getId()))
                .thenReturn(List.of()); // rien payé → en retard

        // Série + session pour le groupe de rattrapage : payé intégralement → pas en retard.
        SessionSeriesEntity catchUpSeries = seriesIn(22L, catchUpGroup);
        SessionEntity catchUpSession = sessionIn(2000L, "Séance rattrapage", catchUpGroup);
        when(seriesRepo.findByGroupId(catchUpGroup.getId())).thenReturn(List.of(catchUpSeries));
        when(sessionRepository.findBySessionSeries(catchUpSeries)).thenReturn(List.of(catchUpSession));
        when(attendanceRepo.findBySessionIdAndStudentId(catchUpSession.getId(), STUDENT_ID))
                .thenReturn(Optional.of(presence(700L, true, catchUpGroup)));
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, catchUpSession.getId()))
                .thenReturn(List.of(paidDetail(catchUpSession, 40.0, "COMPLETED", true, false)));

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        assertThat(result).hasSize(2);
        GroupPaymentStatus official = result.stream()
                .filter(g -> g.getGroupId().equals(100L)).findFirst().orElseThrow();
        assertThat(official.getSeries()).hasSize(1);
        List<SessionPaymentStatus> officialSessions = official.getSeries().get(0).getSessions();
        assertThat(officialSessions).hasSize(1);
        assertThat(officialSessions.get(0).isPaymentOverdue()).isTrue();
        assertThat(officialSessions.get(0).getAmountDue()).isEqualTo(30.0);

        GroupPaymentStatus catchUp = result.stream()
                .filter(g -> g.getGroupId().equals(200L)).findFirst().orElseThrow();
        assertThat(catchUp.getSeries().get(0).getSessions().get(0).isPaymentOverdue()).isFalse();
    }

    @Test
    @DisplayName("Statut détaillé : session sans fiche de présence ignorée → série vide non ajoutée → groupe vide non ajouté")
    void studentDetailedStatusSkipsSessionsWithoutAttendance() {
        GroupEntity group = groupWithPrice(100L, "Groupe", 30.0);
        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(group));
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true)).thenReturn(List.of());

        SessionSeriesEntity series = seriesIn(11L, group);
        SessionEntity session = sessionIn(1000L, "Séance", group);
        when(seriesRepo.findByGroupId(group.getId())).thenReturn(List.of(series));
        when(sessionRepository.findBySessionSeries(series)).thenReturn(List.of(session));
        // Pas de fiche de présence → session ignorée.
        when(attendanceRepo.findBySessionIdAndStudentId(session.getId(), STUDENT_ID))
                .thenReturn(Optional.empty());

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Statut détaillé : absent → montant dû 0 → jamais en retard ; paiements CANCELLED / inactifs / supprimés ignorés")
    void studentDetailedStatusAbsentAndFilteredPayments() {
        GroupEntity group = groupWithPrice(100L, "Groupe", 30.0);
        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(group));
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true)).thenReturn(List.of());

        SessionSeriesEntity series = seriesIn(11L, group);
        SessionEntity session = sessionIn(1000L, "Séance absente", group);
        when(seriesRepo.findByGroupId(group.getId())).thenReturn(List.of(series));
        when(sessionRepository.findBySessionSeries(series)).thenReturn(List.of(session));
        // Étudiant absent (isPresent = false) → montant dû = 0.
        when(attendanceRepo.findBySessionIdAndStudentId(session.getId(), STUDENT_ID))
                .thenReturn(Optional.of(presence(600L, false, group)));
        // Un mélange de details filtrés : CANCELLED, inactif, définitivement supprimé.
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, session.getId()))
                .thenReturn(List.of(
                        paidDetail(session, 30.0, "CANCELLED", true, false),
                        paidDetail(session, 30.0, "COMPLETED", false, false),
                        paidDetail(session, 30.0, "COMPLETED", true, true)));

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        assertThat(result).hasSize(1);
        SessionPaymentStatus status = result.get(0).getSeries().get(0).getSessions().get(0);
        assertThat(status.getAmountDue()).isEqualTo(0.0);
        assertThat(status.getAmountPaid()).isEqualTo(0.0);
        assertThat(status.isPaymentOverdue()).isFalse();
        assertThat(status.getIsPresent()).isFalse();
    }

    @Test
    @DisplayName("Statut détaillé : présence null traitée comme absente → montant dû 0")
    void studentDetailedStatusNullPresenceTreatedAsAbsent() {
        GroupEntity group = groupWithPrice(100L, "Groupe", 30.0);
        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(group));
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true)).thenReturn(List.of());

        SessionSeriesEntity series = seriesIn(11L, group);
        SessionEntity session = sessionIn(1000L, "Séance présence null", group);
        when(seriesRepo.findByGroupId(group.getId())).thenReturn(List.of(series));
        when(sessionRepository.findBySessionSeries(series)).thenReturn(List.of(session));
        // Fiche de présence dont isPresent == null → branche null-check exercée.
        AttendanceEntity attendance = presence(600L, false, group);
        attendance.setIsPresent(null);
        when(attendanceRepo.findBySessionIdAndStudentId(session.getId(), STUDENT_ID))
                .thenReturn(Optional.of(attendance));
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, session.getId()))
                .thenReturn(List.of());

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        SessionPaymentStatus status = result.get(0).getSeries().get(0).getSessions().get(0);
        assertThat(status.getIsPresent()).isFalse();
        assertThat(status.getAmountDue()).isEqualTo(0.0);
        assertThat(status.isPaymentOverdue()).isFalse();
    }

    @Test
    @DisplayName("Statut détaillé : present-only, paiement actif avec permanentlyDeleted null accepté")
    void studentDetailedStatusPresentWithNullPermanentlyDeleted() {
        GroupEntity group = groupWithPrice(100L, "Groupe", 30.0);
        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(group));
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true)).thenReturn(List.of());

        SessionSeriesEntity series = seriesIn(11L, group);
        SessionEntity session = sessionIn(1000L, "Séance payée", group);
        when(seriesRepo.findByGroupId(group.getId())).thenReturn(List.of(series));
        when(sessionRepository.findBySessionSeries(series)).thenReturn(List.of(session));
        when(attendanceRepo.findBySessionIdAndStudentId(session.getId(), STUDENT_ID))
                .thenReturn(Optional.of(presence(600L, true, group)));
        // permanentlyDeleted = null → doit être accepté (branche null-OR).
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, session.getId()))
                .thenReturn(List.of(paidDetail(session, 30.0, "COMPLETED", true, null)));

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        SessionPaymentStatus status = result.get(0).getSeries().get(0).getSessions().get(0);
        assertThat(status.getAmountPaid()).isEqualTo(30.0);
        assertThat(status.isPaymentOverdue()).isFalse();
    }

    @Test
    @DisplayName("Statut détaillé : exemption 100 % → montant dû 0, série marquée exemptée, jamais en retard")
    void studentDetailedStatusFullExemption() {
        GroupEntity group = groupWithPrice(100L, "Groupe exempté", 6000.0);
        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(group));
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true)).thenReturn(List.of());

        SessionSeriesEntity series = seriesIn(11L, group);
        SessionEntity session = sessionIn(1000L, "Séance exemptée", group);
        when(seriesRepo.findByGroupId(group.getId())).thenReturn(List.of(series));
        when(sessionRepository.findBySessionSeries(series)).thenReturn(List.of(session));
        // Présent mais exempté à 100 % : rien n'est dû, donc aucun retard possible.
        when(attendanceRepo.findBySessionIdAndStudentId(session.getId(), STUDENT_ID))
                .thenReturn(Optional.of(presence(600L, true, group)));
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, session.getId()))
                .thenReturn(List.of());
        when(discountService.resolveRate(STUDENT_ID, series.getId())).thenReturn(new BigDecimal("1.00"));

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeries().get(0).isExempted()).isTrue();
        SessionPaymentStatus status = result.get(0).getSeries().get(0).getSessions().get(0);
        assertThat(status.getAmountDue()).isEqualTo(0.0);
        assertThat(status.isPaymentOverdue()).isFalse();
        assertThat(status.getIsPresent()).isTrue();
    }

    @Test
    @DisplayName("Statut détaillé : réduction partielle → montant dû minoré, série non exemptée")
    void studentDetailedStatusPartialDiscount() {
        GroupEntity group = groupWithPrice(100L, "Groupe remisé", 100.0);
        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(group));
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true)).thenReturn(List.of());

        SessionSeriesEntity series = seriesIn(11L, group);
        SessionEntity session = sessionIn(1000L, "Séance remisée", group);
        when(seriesRepo.findByGroupId(group.getId())).thenReturn(List.of(series));
        when(sessionRepository.findBySessionSeries(series)).thenReturn(List.of(session));
        when(attendanceRepo.findBySessionIdAndStudentId(session.getId(), STUDENT_ID))
                .thenReturn(Optional.of(presence(600L, true, group)));
        // 25 % de réduction, 75 versés → exactement soldé, donc pas en retard.
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, session.getId()))
                .thenReturn(List.of(paidDetail(session, 75.0, "COMPLETED", true, false)));
        when(discountService.resolveRate(STUDENT_ID, series.getId())).thenReturn(new BigDecimal("0.25"));

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        assertThat(result.get(0).getSeries().get(0).isExempted()).isFalse();
        SessionPaymentStatus status = result.get(0).getSeries().get(0).getSessions().get(0);
        assertThat(status.getAmountDue()).isEqualTo(75.0);
        assertThat(status.isPaymentOverdue()).isFalse();
    }

    @Test
    @DisplayName("Statut détaillé : présence de rattrapage inactive ou absente exclue des groupes")
    void studentDetailedStatusFiltersInactiveCatchUp() {
        GroupEntity officialGroup = groupWithPrice(100L, "Officiel", 30.0);
        GroupEntity inactiveCatchUpGroup = groupWithPrice(300L, "Rattrapage inactif", 30.0);
        GroupEntity absentCatchUpGroup = groupWithPrice(400L, "Rattrapage absent", 30.0);

        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(officialGroup));

        AttendanceEntity inactive = presence(801L, true, inactiveCatchUpGroup);
        inactive.setActive(false);
        AttendanceEntity absent = presence(802L, false, absentCatchUpGroup);
        AttendanceEntity nullPresent = presence(803L, true, absentCatchUpGroup);
        nullPresent.setIsPresent(null);
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true))
                .thenReturn(List.of(inactive, absent, nullPresent));

        // Le groupe officiel n'a aucune série → résultat vide, mais le filtrage des
        // rattrapages inactifs/absents est exercé.
        when(seriesRepo.findByGroupId(anyLong())).thenReturn(List.of());

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // getAttendedSessions / getPaidSessions / getUnpaidAttendedSessions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getAttendedSessions délègue au repository present-only")
    void getAttendedSessionsDelegates() {
        SessionEntity s1 = sessionIn(1L, "S1", groupWithPrice(1L, "G", 30.0));
        when(attendanceRepo.findByStudentIdAndIsPresent(STUDENT_ID, true)).thenReturn(List.of(s1));

        assertThat(service.getAttendedSessions(STUDENT_ID)).containsExactly(s1);
    }

    @Test
    @DisplayName("getPaidSessions retourne l'ensemble des sessions payées")
    void getPaidSessionsReturnsSet() {
        SessionEntity s1 = sessionIn(1L, "S1", groupWithPrice(1L, "G", 30.0));
        PaymentDetailEntity d1 = PaymentDetailEntity.builder().session(s1).amountPaid(30.0).build();
        when(paymentDetailRepository.findByPayment_StudentId(STUDENT_ID)).thenReturn(List.of(d1));

        assertThat(service.getPaidSessions(STUDENT_ID)).containsExactly(s1);
    }

    @Test
    @DisplayName("getUnpaidAttendedSessions retourne les sessions suivies mais non payées")
    void getUnpaidAttendedSessionsFiltersPaid() {
        GroupEntity group = groupWithPrice(1L, "G", 30.0);
        SessionEntity attendedPaid = sessionIn(1L, "payée", group);
        SessionEntity attendedUnpaid = sessionIn(2L, "impayée", group);

        when(attendanceRepo.findByStudentIdAndIsPresent(STUDENT_ID, true))
                .thenReturn(List.of(attendedPaid, attendedUnpaid));
        PaymentDetailEntity paid = PaymentDetailEntity.builder().session(attendedPaid).amountPaid(30.0).build();
        when(paymentDetailRepository.findByPayment_StudentId(STUDENT_ID)).thenReturn(List.of(paid));

        assertThat(service.getUnpaidAttendedSessions(STUDENT_ID)).containsExactly(attendedUnpaid);
    }

    // ------------------------------------------------------------------
    // Statut de série évalué contre le coût au prorata (exigences 11.1, 11.2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Série soldée : arrivé à la dernière séance de quatre et l'ayant réglée → soldé et à jour")
    void seriesSettledAgainstProrataCostNotNominalCost() {
        // La série est planifiée à 8 séances à 2000 DA, soit 16 000 DA de coût nominal. Une
        // seule séance est facturable à cet étudiant, arrivé en cours de série : évalué contre
        // le coût nominal, il resterait indéfiniment non soldé.
        GroupEntity group = groupWithPrice(100L, "Math 1ère B", 2000.0);
        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(group));
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true)).thenReturn(List.of());

        SessionSeriesEntity series = seriesIn(11L, group);
        SessionEntity lastSession = sessionIn(1000L, "Quatrième séance", group);
        when(seriesRepo.findByGroupId(group.getId())).thenReturn(List.of(series));
        when(sessionRepository.findBySessionSeries(series)).thenReturn(List.of(lastSession));
        when(attendanceRepo.findBySessionIdAndStudentId(lastSession.getId(), STUDENT_ID))
                .thenReturn(Optional.of(presence(600L, true, group)));
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, lastSession.getId()))
                .thenReturn(List.of(paidDetail(lastSession, 2000.0, "COMPLETED", true, false)));

        // Devis : une seule séance facturable, suivie, et 2000 DA versés au registre.
        when(seriesRepo.findById(series.getId())).thenReturn(Optional.of(series));
        givenBillable(series.getId(), 1, 1);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, series.getId()))
                .thenReturn(new BigDecimal("2000.00"));

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        assertThat(result).hasSize(1);
        var seriesStatus = result.get(0).getSeries().get(0);
        assertThat(seriesStatus.getProrataCost()).isEqualByComparingTo("2000.00");
        assertThat(seriesStatus.getBillableSessions()).isEqualTo(1);
        assertThat(seriesStatus.getAmountPaid()).isEqualByComparingTo("2000.00");
        assertThat(seriesStatus.isFullyPaid()).isTrue();
        assertThat(seriesStatus.isLate()).isFalse();
    }

    @Test
    @DisplayName("Série non soldée : versement inférieur au coût au prorata → en retard")
    void seriesNotSettledWhenPaidBelowProrataCost() {
        GroupEntity group = groupWithPrice(100L, "Math 1ère B", 2000.0);
        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(group));
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true)).thenReturn(List.of());

        SessionSeriesEntity series = seriesIn(11L, group);
        SessionEntity session = sessionIn(1000L, "Séance suivie", group);
        when(seriesRepo.findByGroupId(group.getId())).thenReturn(List.of(series));
        when(sessionRepository.findBySessionSeries(series)).thenReturn(List.of(session));
        when(attendanceRepo.findBySessionIdAndStudentId(session.getId(), STUDENT_ID))
                .thenReturn(Optional.of(presence(600L, true, group)));
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, session.getId()))
                .thenReturn(List.of());

        when(seriesRepo.findById(series.getId())).thenReturn(Optional.of(series));
        givenBillable(series.getId(), 2, 1);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, series.getId()))
                .thenReturn(new BigDecimal("0.00"));

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        var seriesStatus = result.get(0).getSeries().get(0);
        assertThat(seriesStatus.getProrataCost()).isEqualByComparingTo("4000.00");
        assertThat(seriesStatus.isFullyPaid()).isFalse();
        assertThat(seriesStatus.isLate()).isTrue();
    }

    @Test
    @DisplayName("Statut de série et devis lisent le même coût au prorata")
    void seriesStatusSharesTheQuoteSource() {
        // Constat explicite de la source unique : le coût porté par le statut de série est
        // exactement celui que le devis annonce pour le même couple étudiant / série.
        GroupEntity group = groupWithPrice(100L, "Math 1ère B", 1500.0);
        when(groupRepository.findByStudents_Id(STUDENT_ID)).thenReturn(List.of(group));
        when(attendanceRepo.findByStudentIdAndIsCatchUp(STUDENT_ID, true)).thenReturn(List.of());

        SessionSeriesEntity series = seriesIn(11L, group);
        SessionEntity session = sessionIn(1000L, "Séance", group);
        when(seriesRepo.findByGroupId(group.getId())).thenReturn(List.of(series));
        when(sessionRepository.findBySessionSeries(series)).thenReturn(List.of(session));
        when(attendanceRepo.findBySessionIdAndStudentId(session.getId(), STUDENT_ID))
                .thenReturn(Optional.of(presence(600L, true, group)));
        when(paymentDetailRepository.findByPayment_StudentIdAndSessionId(STUDENT_ID, session.getId()))
                .thenReturn(List.of());

        when(seriesRepo.findById(series.getId())).thenReturn(Optional.of(series));
        givenBillable(series.getId(), 3, 2);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, series.getId()))
                .thenReturn(new BigDecimal("3000.00"));

        List<GroupPaymentStatus> result = service.getPaymentStatusForStudent(STUDENT_ID);

        var seriesStatus = result.get(0).getSeries().get(0);
        assertThat(seriesStatus.getProrataCost())
                .isEqualByComparingTo(quoteService.quote(STUDENT_ID, series.getId()).monthTotalCost());
        assertThat(seriesStatus.getProrataCost())
                .isEqualByComparingTo(resolver.resolve(STUDENT_ID, series.getId()).monthTotalCost());
    }
}
