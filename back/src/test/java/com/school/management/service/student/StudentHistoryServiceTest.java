package com.school.management.service.student;

import com.school.management.dto.group.GroupHistoryDTO;
import com.school.management.dto.serie.SeriesHistoryDTO;
import com.school.management.dto.session.BillingInclusionReason;
import com.school.management.dto.session.SessionHistoryDTO;
import com.school.management.dto.student.StudentFullHistoryDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.service.DiscountService;
import com.school.management.service.payment.BillableSessionsResolver;
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import com.school.management.service.payment.PaymentCostCalculator;
import com.school.management.service.payment.PaymentQuoteService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link StudentHistoryService}.
 *
 * <p>Couvre : le marquage distinct des séances de rattrapage (catchUpSession),
 * l'indicateur d'exemption (taux résolu 1.00 → isExempted vrai sur la série et ses
 * séances ; &lt; 1.00 → faux), l'inclusion des remboursements (totalRefunded issu du
 * {@link RefundRepository}) et l'historique vide (étudiant sans groupe → liste vide).</p>
 *
 * <p>Couvre également la restitution du prorata dans l'historique (exigences 11.3, 11.5, 11.6) :
 * la facturabilité de chaque séance, son motif d'inclusion et le décompte des séances
 * facturables de la série. Ces trois informations étaient auparavant approximées côté interface
 * par « aucune assiduité renseignée et aucun montant affecté », ce qui classait à tort non
 * facturée une séance postérieure à l'inscription dont la présence n'est pas encore saisie.</p>
 */
class StudentHistoryServiceTest {

    private static final long STUDENT_ID = 1L;
    private static final long GROUP_ID = 10L;
    private static final long SERIES_ID = 20L;
    private static final long SESSION_ID = 30L;

    private StudentRepository studentRepository;
    private AttendanceRepository attendanceRepository;
    private StudentGroupRepository studentGroupRepository;
    private PaymentDetailRepository paymentDetailRepository;
    private DiscountService discountService;
    private RefundRepository refundRepository;
    private BillableSessionsResolver billableSessionsResolver;
    private PaymentQuoteService paymentQuoteService;

    private StudentHistoryService service;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        attendanceRepository = mock(AttendanceRepository.class);
        studentGroupRepository = mock(StudentGroupRepository.class);
        paymentDetailRepository = mock(PaymentDetailRepository.class);
        discountService = mock(DiscountService.class);
        refundRepository = mock(RefundRepository.class);
        billableSessionsResolver = mock(BillableSessionsResolver.class);
        paymentQuoteService = mock(PaymentQuoteService.class);

        service = new StudentHistoryService(studentRepository, attendanceRepository,
                paymentDetailRepository, refundRepository, billableSessionsResolver,
                paymentQuoteService);

        // Valeurs par défaut : pas de rattrapage annexe, aucun payment detail chargé.
        when(attendanceRepository.findByStudentIdAndIsCatchUp(anyLong(), eq(true))).thenReturn(List.of());
        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(anyLong(), anyLong()))
                .thenReturn(List.of());
        when(refundRepository.sumRefundsForStudentAndSeries(anyLong(), anyLong())).thenReturn(BigDecimal.ZERO);
        when(discountService.resolveRate(anyLong(), anyLong())).thenReturn(new BigDecimal("0.00"));
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        stubBillableSessionsResolver();
        stubPaymentQuoteService();
    }

    /**
     * Doublure du devis, source unique du Coût_Série_Prorata, du prix net et de l'exemption.
     *
     * <p>Elle s'appuie sur le <strong>vrai</strong> {@link PaymentCostCalculator} et sur le
     * décompte facturable du résolveur, donc sur la même formule que la production : un
     * changement d'arrondi dans le calculateur se répercuterait ici sans que le test ait à
     * être retouché.</p>
     */
    private void stubPaymentQuoteService() {
        when(paymentQuoteService.quote(anyLong(), anyLong())).thenAnswer(invocation -> {
            Long studentId = invocation.getArgument(0);
            Long seriesId = invocation.getArgument(1);
            StudentEntity owner = studentRepository.findById(studentId).orElseThrow();
            SessionSeriesEntity series = seriesOf(owner, seriesId);

            BigDecimal gross = grossPrice(series.getGroup());
            BigDecimal rate = Optional.ofNullable(discountService.resolveRate(studentId, seriesId))
                    .orElse(BigDecimal.ZERO);
            BillableSessions billable = billableSessionsResolver.resolve(studentId, seriesId);

            PaymentCostCalculator calculator = new PaymentCostCalculator(
                    billable.billableCount(), billable.attendedCount(), gross, rate);
            BigDecimal netPrice = gross.multiply(BigDecimal.ONE.subtract(rate))
                    .setScale(2, RoundingMode.HALF_UP);

            return quote(seriesId, billable.billableCount(), billable.excludedCount(),
                    billable.attendedCount(), gross, rate, netPrice,
                    calculator.monthTotalCost(), calculator.amountDueSoFar());
        });
    }

    private PaymentQuoteDTO quote(Long seriesId, int billableSessions, int excludedSessions,
            int attendedSessions, BigDecimal gross, BigDecimal rate, BigDecimal netPrice,
            BigDecimal monthTotalCost, BigDecimal amountDueSoFar) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new PaymentQuoteDTO(STUDENT_ID, seriesId, billableSessions, billableSessions,
                excludedSessions, attendedSessions, gross, rate, netPrice, monthTotalCost,
                amountDueSoFar, zero, monthTotalCost, monthTotalCost, zero,
                rate.compareTo(BigDecimal.ONE) == 0, false);
    }

    private BigDecimal grossPrice(GroupEntity group) {
        if (group == null || group.getPrice() == null || group.getPrice().getPrice() == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(group.getPrice().getPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Doublure du résolveur partagé.
     *
     * <p>Elle applique la règle du prorata sur le graphe d'entités construit par chaque test,
     * là où {@code BillableSessionsResolverImpl} la lit en base. L'inscription est résolue par
     * le même dépôt que le résolveur réel ({@code findByGroupIdAndStudentIdAndActiveTrue}), de
     * sorte que les tests continuent de piloter l'inscription comme avant. Les règles du
     * résolveur sont vérifiées par {@code BillableSessionsResolverTest} ; ici on vérifie que
     * l'historique les consomme au lieu d'en porter sa propre copie.</p>
     */
    private void stubBillableSessionsResolver() {
        when(billableSessionsResolver.resolve(anyLong(), anyLong())).thenAnswer(invocation -> {
            Long studentId = invocation.getArgument(0);
            Long seriesId = invocation.getArgument(1);
            StudentEntity owner = studentRepository.findById(studentId).orElseThrow();
            SessionSeriesEntity series = seriesOf(owner, seriesId);

            Optional<StudentGroupEntity> enrolment = studentGroupRepository
                    .findByGroupIdAndStudentIdAndActiveTrue(series.getGroup().getId(), studentId);
            Date enrollmentDate = enrolment.map(StudentGroupEntity::getDateAssigned).orElse(null);

            List<SessionEntity> billable = new ArrayList<>();
            List<SessionEntity> excluded = new ArrayList<>();
            int attendedCount = 0;
            for (SessionEntity session : chronologicalSessions(series)) {
                AttendanceEntity attendance = activeAttendance(session, studentId);
                boolean onOrAfterEnrolment = enrollmentDate != null
                        && session.getSessionTimeStart() != null
                        && !session.getSessionTimeStart().before(enrollmentDate);
                if (attendance != null || onOrAfterEnrolment) {
                    billable.add(session);
                    if (attendance != null && Boolean.TRUE.equals(attendance.getIsPresent())) {
                        attendedCount++;
                    }
                } else {
                    excluded.add(session);
                }
            }
            return new BillableSessions(List.copyOf(billable), List.copyOf(excluded),
                    attendedCount, enrolment.isPresent(), enrollmentDate);
        });
    }

    private SessionSeriesEntity seriesOf(StudentEntity owner, Long seriesId) {
        return owner.getGroups().stream()
                .flatMap(group -> group.getSeries().stream())
                .filter(series -> seriesId.equals(series.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Série absente du graphe : " + seriesId));
    }

    private List<SessionEntity> chronologicalSessions(SessionSeriesEntity series) {
        return series.getSessions().stream()
                .sorted(Comparator.comparing(SessionEntity::getSessionTimeStart,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SessionEntity::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private AttendanceEntity activeAttendance(SessionEntity session, Long studentId) {
        return session.getAttendances().stream()
                .filter(a -> a.getStudent() != null && studentId.equals(a.getStudent().getId()))
                .filter(AttendanceEntity::isActive)
                .findFirst()
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private PricingEntity pricing(double price) {
        PricingEntity p = new PricingEntity();
        p.setId(99L);
        p.setPrice(price);
        return p;
    }

    private StudentEntity student() {
        StudentEntity s = new StudentEntity();
        s.setId(STUDENT_ID);
        s.setFirstName("Jean");
        s.setLastName("Dupont");
        return s;
    }

    private SessionEntity session(long id, boolean active, StudentEntity student,
            SessionSeriesEntity series, GroupEntity group,
            boolean present, boolean catchUp) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setTitle("Séance " + id);
        session.setActive(active);
        session.setSessionTimeStart(new Date());
        session.setSessionSeries(series);
        session.setGroup(group);

        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setId(id * 100);
        attendance.setStudent(student);
        attendance.setSession(session);
        attendance.setGroup(group);
        attendance.setActive(true);
        attendance.setIsPresent(present);
        attendance.setIsJustified(false);
        attendance.setIsCatchUp(catchUp);

        Set<AttendanceEntity> attendances = new HashSet<>();
        attendances.add(attendance);
        session.setAttendances(attendances);
        session.setPaymentDetails(new HashSet<>());
        return session;
    }

    /**
     * Construit un étudiant officiellement inscrit dans un groupe possédant une série
     * avec une unique séance (présent, éventuellement rattrapage).
     */
    private StudentEntity officialStudentWithOneSession(boolean present, boolean catchUp) {
        StudentEntity student = student();

        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("Groupe Maths");
        group.setPrice(pricing(30.0));

        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        series.setName("Série Groupe Maths - 01-2025-001");
        series.setGroup(group);

        SessionEntity session = session(SESSION_ID, true, student, series, group, present, catchUp);
        series.setSessions(new HashSet<>(Set.of(session)));

        group.setSeries(new HashSet<>(Set.of(series)));
        student.setGroups(new HashSet<>(Set.of(group)));
        return student;
    }

    private SeriesHistoryDTO firstSeries(StudentFullHistoryDTO dto) {
        GroupHistoryDTO group = dto.getGroups().get(0);
        return group.getSeries().get(0);
    }

    /** Séance sans aucune fiche de présence (présence « non renseignée »). */
    private SessionEntity sessionWithoutAttendance(long id, Date start,
            SessionSeriesEntity series, GroupEntity group) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setTitle("Séance " + id);
        session.setActive(true);
        session.setSessionTimeStart(start);
        session.setSessionSeries(series);
        session.setGroup(group);
        session.setAttendances(new HashSet<>());
        session.setPaymentDetails(new HashSet<>());
        return session;
    }

    private Date day(int dayOfMonth) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2025, Calendar.SEPTEMBER, dayOfMonth, 12, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * Reproduit la situation constatée : un groupe à {@code pricePerSession}, une série de
     * quatre séances de septembre, l'étudiant présent aux {@code attendedCount} premières.
     */
    private StudentEntity officialStudentWithFourSessions(double pricePerSession, int attendedCount) {
        StudentEntity student = student();

        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("Math 1ère A");
        group.setPrice(pricing(pricePerSession));

        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        series.setName("Septembre 2025");
        series.setGroup(group);

        Set<SessionEntity> sessions = new HashSet<>();
        for (int index = 0; index < 4; index++) {
            long id = SESSION_ID + index;
            if (index < attendedCount) {
                SessionEntity attended = session(id, true, student, series, group, true, false);
                attended.setSessionTimeStart(day(index + 1));
                sessions.add(attended);
            } else {
                sessions.add(sessionWithoutAttendance(id, day(index + 1), series, group));
            }
        }
        series.setSessions(sessions);

        group.setSeries(new HashSet<>(Set.of(series)));
        student.setGroups(new HashSet<>(Set.of(group)));
        return student;
    }

    /**
     * Étudiant arrivé à la dernière séance d'une série de quatre : les trois premières se sont
     * tenues avant son inscription et il n'y a aucune fiche de présence ; il est présent à la
     * quatrième.
     */
    private StudentEntity studentJoiningOnLastOfFourSessions(double pricePerSession) {
        StudentEntity student = student();

        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("Math 1ère B");
        group.setPrice(pricing(pricePerSession));

        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        series.setName("Septembre 2025");
        series.setGroup(group);

        Set<SessionEntity> sessions = new HashSet<>();
        for (int index = 0; index < 3; index++) {
            sessions.add(sessionWithoutAttendance(SESSION_ID + index, day(index + 1), series, group));
        }
        SessionEntity lastSession = session(SESSION_ID + 3, true, student, series, group, true, false);
        lastSession.setSessionTimeStart(day(4));
        sessions.add(lastSession);
        series.setSessions(sessions);

        group.setSeries(new HashSet<>(Set.of(series)));
        student.setGroups(new HashSet<>(Set.of(group)));
        return student;
    }

    /** Versement rattaché à une séance, daté, actif, sur un paiement non annulé. */
    private PaymentDetailEntity paymentDetail(long id, StudentEntity student, SessionEntity session,
            double amount, Date paymentDate) {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(500L + id);
        payment.setStudent(student);
        payment.setStatus("IN_PROGRESS");

        PaymentDetailEntity detail = new PaymentDetailEntity();
        detail.setId(id);
        detail.setPayment(payment);
        detail.setSession(session);
        detail.setAmountPaid(amount);
        detail.setPaymentDate(paymentDate);
        detail.setActive(true);
        return detail;
    }

    private SessionEntity sessionById(StudentEntity student, long sessionId) {
        return student.getGroups().iterator().next().getSeries().iterator().next().getSessions().stream()
                .filter(s -> s.getId().equals(sessionId))
                .findFirst()
                .orElseThrow();
    }

    private List<SessionHistoryDTO> sessionsOrdered(StudentFullHistoryDTO dto) {
        return firstSeries(dto).getSessions();
    }

    // ------------------------------------------------------------------
    // getStudentFullHistory — non trouvé
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_studentNotFound_throws() {
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStudentFullHistory(STUDENT_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Historique vide
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_studentWithoutGroups_returnsEmptyGroups() {
        StudentEntity student = student();
        student.setGroups(new HashSet<>());
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(dto.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(dto.getStudentName()).isEqualTo("Jean Dupont");
        assertThat(dto.getGroups()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Rattrapage marqué distinctement
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_catchUpAttendance_marksSessionAsCatchUp() {
        StudentEntity student = officialStudentWithOneSession(true, true);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SessionHistoryDTO session = firstSeries(dto).getSessions().get(0);
        assertThat(session.getCatchUpSession()).isTrue();
    }

    @Test
    void getStudentFullHistory_nonCatchUpAttendance_sessionNotMarkedCatchUp() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SessionHistoryDTO session = firstSeries(dto).getSessions().get(0);
        assertThat(session.getCatchUpSession()).isFalse();
    }

    // ------------------------------------------------------------------
    // Exemption
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_fullExemption_setsIsExemptedOnSeriesAndSessions() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        // Taux 1.00 (échelle différente pour vérifier l'usage de compareTo).
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("1.000"));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SeriesHistoryDTO series = firstSeries(dto);
        assertThat(series.getIsExempted()).isTrue();
        assertThat(series.getSessions().get(0).getIsExempted()).isTrue();
    }

    @Test
    void getStudentFullHistory_partialDiscount_isNotExempted() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("0.50"));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SeriesHistoryDTO series = firstSeries(dto);
        assertThat(series.getIsExempted()).isFalse();
        assertThat(series.getSessions().get(0).getIsExempted()).isFalse();
    }

    @Test
    void getStudentFullHistory_nullResolvedRate_isNotExempted() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(null);

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(firstSeries(dto).getIsExempted()).isFalse();
    }

    // ------------------------------------------------------------------
    // Remboursements
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_refundsExist_populatesTotalRefunded() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(refundRepository.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("45.00"));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(firstSeries(dto).getTotalRefunded()).isEqualByComparingTo("45.00");
    }

    @Test
    void getStudentFullHistory_noRefunds_totalRefundedIsZero() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(refundRepository.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID)).thenReturn(null);

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(firstSeries(dto).getTotalRefunded()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // Séance dévalidée (inactive) + paiement
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_inactiveSession_reportedAsNonRenseigne() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        // Rendre la séance inactive.
        SessionEntity session = student.getGroups().iterator().next()
                .getSeries().iterator().next().getSessions().iterator().next();
        session.setActive(false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SessionHistoryDTO sessionDto = firstSeries(dto).getSessions().get(0);
        assertThat(sessionDto.getAttendanceStatus()).isEqualTo("UNKNOWN");
        assertThat(sessionDto.getAmountPaid()).isEqualTo(0.0);
        // L'exemption reste propagée même sur une séance dévalidée.
        assertThat(sessionDto.getIsExempted()).isFalse();
    }

    @Test
    void getStudentFullHistory_withPaymentDetail_populatesPaymentFields() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        SessionEntity session = student.getGroups().iterator().next()
                .getSeries().iterator().next().getSessions().iterator().next();
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        PaymentEntity payment = new PaymentEntity();
        payment.setId(500L);
        payment.setStudent(student);
        payment.setStatus("Completed");

        PaymentDetailEntity pd = new PaymentDetailEntity();
        pd.setId(600L);
        pd.setPayment(payment);
        pd.setSession(session);
        pd.setAmountPaid(30.0);
        pd.setActive(true);

        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(pd));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SessionHistoryDTO sessionDto = firstSeries(dto).getSessions().get(0);
        // Le statut est déduit de la séance (30 versés pour une séance à 30), et non
        // recopié du paiement parent.
        assertThat(sessionDto.getPaymentStatus()).isEqualTo("PAID");
        assertThat(sessionDto.getAmountPaid()).isEqualTo(30.0);
    }

    @Test
    void getStudentFullHistory_sessionStatusIsPerSession_notParentPaymentStatus() {
        // Régression : un paiement parent « en cours » ne doit pas faire basculer une
        // séance intégralement réglée. L'administrateur qui corrige le montant d'UNE
        // séance ne doit pas voir les autres passer en « en cours ».
        StudentEntity student = officialStudentWithOneSession(true, false);
        SessionEntity session = student.getGroups().iterator().next()
                .getSeries().iterator().next().getSessions().iterator().next();
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        PaymentEntity payment = new PaymentEntity();
        payment.setId(500L);
        payment.setStudent(student);
        payment.setStatus("IN_PROGRESS");

        PaymentDetailEntity pd = new PaymentDetailEntity();
        pd.setId(600L);
        pd.setPayment(payment);
        pd.setSession(session);
        pd.setAmountPaid(30.0); // séance soldée
        pd.setActive(true);

        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(pd));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(firstSeries(dto).getSessions().get(0).getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void getStudentFullHistory_partiallyPaidSession_isInProgress() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        SessionEntity session = student.getGroups().iterator().next()
                .getSeries().iterator().next().getSessions().iterator().next();
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        PaymentEntity payment = new PaymentEntity();
        payment.setId(500L);
        payment.setStudent(student);
        payment.setStatus("COMPLETED"); // parent « soldé », séance non soldée

        PaymentDetailEntity pd = new PaymentDetailEntity();
        pd.setId(600L);
        pd.setPayment(payment);
        pd.setSession(session);
        pd.setAmountPaid(25.0); // 25 sur une séance à 30
        pd.setActive(true);

        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(pd));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(firstSeries(dto).getSessions().get(0).getPaymentStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void getStudentFullHistory_zeroPaidSession_isUnpaid() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        SessionEntity session = student.getGroups().iterator().next()
                .getSeries().iterator().next().getSessions().iterator().next();
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        PaymentEntity payment = new PaymentEntity();
        payment.setId(500L);
        payment.setStudent(student);
        payment.setStatus("COMPLETED");

        PaymentDetailEntity pd = new PaymentDetailEntity();
        pd.setId(600L);
        pd.setPayment(payment);
        pd.setSession(session);
        pd.setAmountPaid(0.0);
        pd.setActive(true);

        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(pd));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(firstSeries(dto).getSessions().get(0).getPaymentStatus()).isEqualTo("UNPAID");
    }

    // ------------------------------------------------------------------
    // Réduction appliquée au prix de la séance
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_discountedSession_statusUsesNetPrice() {
        // Régression : la fiche étudiante affichait « à jour » (réduction appliquée) alors
        // que l'historique jugeait la séance sur le plein tarif et la disait « non payée ».
        StudentEntity student = officialStudentWithOneSession(true, false);
        SessionEntity session = student.getGroups().iterator().next()
                .getSeries().iterator().next().getSessions().iterator().next();
        student.getGroups().iterator().next().setPrice(pricing(2000.0));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        // 65 % de réduction → prix net 700.
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("0.65"));

        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(paymentDetail(600L, student, session, 1000.0, new Date())));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SeriesHistoryDTO series = firstSeries(dto);
        assertThat(series.getTotalCost()).isEqualTo(700.0);
        assertThat(series.getSessions().get(0).getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void getStudentFullHistory_fullyExemptedSession_isNotReportedUnpaid() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("1.00"));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SeriesHistoryDTO series = firstSeries(dto);
        assertThat(series.getTotalCost()).isZero();
        // Rien à régler : la séance ne doit pas apparaître en rouge « non payée ».
        assertThat(series.getSessions().get(0).getPaymentStatus()).isEqualTo("PAID");
    }

    // ------------------------------------------------------------------
    // Affectation d'un versement à plusieurs séances
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_paymentSpreadsOverFollowingSessions() {
        // L'administrateur a saisi 2000 sur la séance 1 et 1000 sur la séance 2, pour un
        // prix net de 700 par séance. Le surplus doit couvrir les séances suivantes au lieu
        // de les laisser « non payées » alors que l'étudiant a versé plus que le total dû.
        StudentEntity student = officialStudentWithFourSessions(2000.0, 4);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("0.65"));

        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(
                        paymentDetail(600L, student, sessionById(student, SESSION_ID), 2000.0, day(10)),
                        paymentDetail(601L, student, sessionById(student, SESSION_ID + 1), 1000.0, day(11))));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SeriesHistoryDTO series = firstSeries(dto);
        assertThat(series.getTotalCost()).isEqualTo(2800.0); // 4 × 700
        assertThat(series.getTotalAmountPaid()).isEqualTo(3000.0);
        assertThat(series.getPaymentStatus()).isEqualTo("FULL");
        // Réconciliation : 2800 affectés aux séances, 200 versés au-delà du dû.
        assertThat(series.getTotalAllocated()).isEqualTo(2800.0);
        assertThat(series.getTotalOverpaid()).isEqualTo(200.0);

        // Les quatre séances sont couvertes, chacune à hauteur de son prix net.
        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getPaymentStatus)
                .containsExactly("PAID", "PAID", "PAID", "PAID");
        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getAmountPaid)
                .containsExactly(700.0, 700.0, 700.0, 700.0);
    }

    @Test
    void getStudentFullHistory_partialCoverageOnLastSession_isPartial() {
        StudentEntity student = officialStudentWithFourSessions(2000.0, 4);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("0.65"));

        // 1500 versés : séance 1 soldée (700), séance 2 soldée (700), séance 3 à 100.
        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(
                        paymentDetail(600L, student, sessionById(student, SESSION_ID), 1500.0, day(10))));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getPaymentStatus)
                .containsExactly("PAID", "PAID", "PARTIAL", "UNPAID");
        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getAmountPaid)
                .containsExactly(700.0, 700.0, 100.0, 0.0);
        assertThat(firstSeries(dto).getPaymentStatus()).isEqualTo("PARTIAL");
    }

    // ------------------------------------------------------------------
    // Séances antérieures à l'inscription
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_attendedSessionsBeforeEnrollment_areStillBilled() {
        // Régression : le coût ne retenait que les séances postérieures à la fiche
        // d'inscription. Une série entièrement antérieure tombait donc à 0, et l'en-tête
        // annonçait « Paiement : Complet — 3000 DA / 0 DA ».
        StudentEntity student = officialStudentWithFourSessions(2000.0, 3);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal("0.65"));

        StudentGroupEntity enrollment = new StudentGroupEntity();
        enrollment.setDateAssigned(day(30)); // inscription postérieure à toutes les séances
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(enrollment));

        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(
                        paymentDetail(600L, student, sessionById(student, SESSION_ID), 2000.0, day(10)),
                        paymentDetail(601L, student, sessionById(student, SESSION_ID + 1), 1000.0, day(11))));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SeriesHistoryDTO series = firstSeries(dto);
        // Trois séances suivies × 700 : la quatrième, ni suivie ni postérieure à
        // l'inscription, n'est pas facturée.
        assertThat(series.getTotalCost()).isEqualTo(2100.0);
        assertThat(series.getPaymentStatus()).isEqualTo("FULL");
        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getPaymentStatus)
                .containsExactly("PAID", "PAID", "PAID", "UNPAID");
        // Le détail justifie 2100 ; les 900 restants sont un trop-perçu nommé comme tel.
        assertThat(series.getTotalAllocated()).isEqualTo(2100.0);
        assertThat(series.getTotalOverpaid()).isEqualTo(900.0);
    }

    // ------------------------------------------------------------------
    // Statut de série évalué contre le coût au prorata (exigences 11.1, 11.2)
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_joinedOnLastSessionAndPaidIt_seriesIsSettled() {
        // Le piège de la fonctionnalité : évalué contre le coût nominal de la série
        // (4 × 2000), cet étudiant resterait indéfiniment « en retard » alors qu'il ne doit
        // qu'une séance et l'a réglée. Le verdict porte sur le Coût_Série_Prorata.
        StudentEntity student = studentJoiningOnLastOfFourSessions(2000.0);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        StudentGroupEntity enrollment = new StudentGroupEntity();
        enrollment.setDateAssigned(day(4)); // inscription le jour de la quatrième séance
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(enrollment));

        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(paymentDetail(600L, student,
                        sessionById(student, SESSION_ID + 3), 2000.0, day(4))));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SeriesHistoryDTO series = firstSeries(dto);
        // Une seule séance facturable : le coût de la série tombe à 2000, non 8000.
        assertThat(series.getTotalCost()).isEqualTo(2000.0);
        assertThat(series.getPaymentStatus()).isEqualTo("FULL");
        // Rien n'est classé en trop-perçu : le versement couvre exactement le dû.
        assertThat(series.getTotalOverpaid()).isZero();
        // Les trois séances antérieures restent visibles, mais ne sont pas des dettes.
        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getPaymentStatus)
                .containsExactly("UNPAID", "UNPAID", "UNPAID", "PAID");
    }

    @Test
    void getStudentFullHistory_seriesCostComesFromTheSharedQuote() {
        // Preuve de la source unique : l'historique ne recalcule plus le coût de série. Si le
        // devis annonce un coût au prorata donné, c'est lui qui fait foi, y compris quand il
        // s'écarte du produit prix × séances que l'historique calculait auparavant.
        StudentEntity student = officialStudentWithOneSession(true, false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(paymentQuoteService.quote(STUDENT_ID, SERIES_ID)).thenReturn(
                quote(SERIES_ID, 1, 0, 1,
                        new BigDecimal("30.00"), new BigDecimal("0.00"), new BigDecimal("12.00"),
                        new BigDecimal("12.00"), new BigDecimal("12.00")));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(firstSeries(dto).getTotalCost()).isEqualTo(12.00);
    }

    // ------------------------------------------------------------------
    // Facturabilité et motif d'inclusion des séances (exigences 11.3, 11.5, 11.6)
    // ------------------------------------------------------------------

    @Test
    void getStudentFullHistory_exposesBillabilityAndInclusionReasonPerSession() {
        // Les trois motifs dans une seule série : inscription le jour de la troisième séance,
        // présence sur la première (rattrapage avant inscription), rien sur la deuxième.
        //
        // Sans ces champs, l'interface devait approximer « non facturée » par « aucune
        // assiduité renseignée et aucun montant affecté » — ce qui classait à tort la
        // quatrième séance, postérieure à l'inscription et sans feuille de présence encore
        // saisie, alors qu'elle est bel et bien facturable.
        StudentEntity student = officialStudentWithFourSessions(2000.0, 1);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        StudentGroupEntity enrollment = new StudentGroupEntity();
        enrollment.setDateAssigned(day(3));
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(enrollment));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getBillable)
                .containsExactly(true, false, true, true);
        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getInclusionReason)
                .containsExactly(
                        // Séance 1 : antérieure à l'inscription mais suivie, donc facturée.
                        // C'est le seul cas à étiqueter « rattrapage » (exigence 11.5).
                        BillingInclusionReason.ATTENDED_BEFORE_ENROLMENT,
                        // Séance 2 : antérieure et non suivie, écartée (exigences 11.3, 11.4).
                        BillingInclusionReason.EXCLUDED,
                        // Séances 3 et 4 : postérieures ou égales à l'inscription.
                        BillingInclusionReason.AFTER_ENROLMENT,
                        BillingInclusionReason.AFTER_ENROLMENT);
        // Trois séances facturables sur quatre : le décompte justifie le coût annoncé et n'est
        // pas déductible du nombre de lignes affichées (exigence 11.6).
        assertThat(firstSeries(dto).getBillableSessions()).isEqualTo(3);
        assertThat(firstSeries(dto).getTotalCost()).isEqualTo(6000.0);
    }

    @Test
    void getStudentFullHistory_sessionAfterEnrolmentWithoutAttendance_isStillBillable() {
        // Le cas que l'approximation du front classait à tort non facturée : séance
        // postérieure à l'inscription dont la feuille de présence n'est pas encore saisie.
        StudentEntity student = officialStudentWithFourSessions(2000.0, 0);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        StudentGroupEntity enrollment = new StudentGroupEntity();
        enrollment.setDateAssigned(day(1));
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(enrollment));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getBillable)
                .containsOnly(true);
        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getInclusionReason)
                .containsOnly(BillingInclusionReason.AFTER_ENROLMENT);
        assertThat(firstSeries(dto).getBillableSessions()).isEqualTo(4);
    }

    @Test
    void getStudentFullHistory_everySessionBeforeEnrolmentAndUnattended_isExcluded() {
        // Le cas réel de l'inscription tardive : quatre séances existantes, aucune facturable.
        StudentEntity student = officialStudentWithFourSessions(2000.0, 0);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        StudentGroupEntity enrollment = new StudentGroupEntity();
        enrollment.setDateAssigned(day(30));
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(enrollment));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getBillable)
                .containsOnly(false);
        assertThat(sessionsOrdered(dto)).extracting(SessionHistoryDTO::getInclusionReason)
                .containsOnly(BillingInclusionReason.EXCLUDED);
        // Zéro facturable, et non « quatre séances dues » : les lignes restent visibles sans
        // être des dettes (exigences 11.4, 11.6).
        assertThat(firstSeries(dto).getBillableSessions()).isZero();
        assertThat(firstSeries(dto).getTotalCost()).isZero();
    }

    @Test
    void getStudentFullHistory_inactiveSessionStillCarriesBillabilityAndReason() {
        // Le raccourci de sortie des séances dévalidées ne doit pas priver la ligne de son
        // verdict de facturation : l'interface en a besoin pour toutes les lignes.
        StudentEntity student = officialStudentWithOneSession(true, false);
        SessionEntity session = student.getGroups().iterator().next()
                .getSeries().iterator().next().getSessions().iterator().next();
        session.setActive(false);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SessionHistoryDTO sessionDto = firstSeries(dto).getSessions().get(0);
        assertThat(sessionDto.getBillable()).isTrue();
        // Aucune inscription simulée : seules les séances suivies sont facturables
        // (exigence 1.4), c'est le rattrapage pur.
        assertThat(sessionDto.getInclusionReason())
                .isEqualTo(BillingInclusionReason.ATTENDED_BEFORE_ENROLMENT);
    }

    @Test
    void getStudentFullHistory_paidExactly_hasNoOverpaid() {
        StudentEntity student = officialStudentWithOneSession(true, false);
        SessionEntity session = student.getGroups().iterator().next()
                .getSeries().iterator().next().getSessions().iterator().next();
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        when(paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(paymentDetail(600L, student, session, 30.0, new Date())));

        StudentFullHistoryDTO dto = service.getStudentFullHistory(STUDENT_ID);

        SeriesHistoryDTO series = firstSeries(dto);
        assertThat(series.getTotalAllocated()).isEqualTo(30.0);
        assertThat(series.getTotalOverpaid()).isZero();
    }
}
