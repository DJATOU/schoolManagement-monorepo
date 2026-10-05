package com.school.management.service.payment;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ventilation d'une Imputation sur les séances de sa série (exigence 4.5 ; spec admin-corrections,
 * exigences 1.3 et 1.6, design D3).
 *
 * <p>Trois règles sont verrouillées ici :</p>
 * <ul>
 *   <li>les séances candidates viennent du {@link BillableSessionsResolver} : aucune affectation
 *       hors des facturables ;</li>
 *   <li>chaque ligne créée est une part de l'Imputation ventilée, datée de son Encaissement, et
 *       aucune ligne existante n'est jamais complétée : une séance porte une ligne par
 *       Encaissement ;</li>
 *   <li>une séance ne reçoit que ce qui lui reste dû au <b>prix net</b>, toutes lignes actives
 *       confondues.</li>
 * </ul>
 *
 * <p>Le mode rattrapage est conservé, avec son critère d'origine : <strong>toutes</strong> les
 * présences de la série sont des rattrapages.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentDistributionServiceTest {

    private static final Long STUDENT_ID = 5L;
    private static final Long SERIES_ID = 20L;
    private static final Long PAYMENT_ID = 900L;
    private static final Date RECEIVED_AT = date("2025-01-12");

    @Mock private SessionRepository sessionRepository;
    @Mock private PaymentDetailRepository paymentDetailRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private PaymentQuoteService paymentQuoteService;
    @Mock private BillableSessionsResolver billableSessionsResolver;

    private PaymentDistributionService service;

    /** Les quatre séances de la série, du 3 au 24 janvier. */
    private final SessionEntity session1 = session(1L, "2025-01-03");
    private final SessionEntity session2 = session(2L, "2025-01-10");
    private final SessionEntity session3 = session(3L, "2025-01-17");
    private final SessionEntity session4 = session(4L, "2025-01-24");

    private PaymentEntity payment;

    /** Part déjà ventilée par séance, lignes actives d'autres Encaissements. */
    private final Map<Long, Double> alreadyVentilated = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new PaymentDistributionService(sessionRepository, paymentDetailRepository,
                attendanceRepository, paymentQuoteService, billableSessionsResolver);

        payment = payment();
        givenNetPrice("30.00");
        when(paymentDetailRepository.sumActiveAmountForPaymentAndSession(eq(PAYMENT_ID), anyLong()))
                .thenAnswer(invocation -> alreadyVentilated.getOrDefault(invocation.getArgument(1), 0.0));
        when(paymentDetailRepository.save(any(PaymentDetailEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        givenAttendances();
    }

    // ------------------------------------------------------------------
    // Fabriques de données
    // ------------------------------------------------------------------

    private static Date date(String isoDate) {
        return Date.from(LocalDate.parse(isoDate).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static SessionEntity session(Long id, String isoDate) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setSessionTimeStart(date(isoDate));
        return session;
    }

    private PaymentEntity payment() {
        StudentEntity student = new StudentEntity();
        student.setId(STUDENT_ID);

        PricingEntity pricing = new PricingEntity();
        pricing.setPrice(30.0);

        GroupEntity group = new GroupEntity();
        group.setId(3L);
        group.setPrice(pricing);

        PaymentEntity entity = new PaymentEntity();
        entity.setId(PAYMENT_ID);
        entity.setStudent(student);
        entity.setGroup(group);
        entity.setAmountPaid(0.0);
        return entity;
    }

    /** Imputation du montant donné, sur la série, d'un Encaissement reçu le {@link #RECEIVED_AT}. */
    private EncashmentAllocationEntity imputation(String amount) {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        return EncashmentAllocationEntity.builder()
                .id(700L)
                .encashment(EncashmentEntity.builder().id(70L).receivedAt(RECEIVED_AT).build())
                .series(series)
                .payment(payment)
                .amount(new BigDecimal(amount))
                .carriedOver(false)
                .active(true)
                .build();
    }

    private void givenNetPrice(String netPrice) {
        when(paymentQuoteService.netPricePerSession(STUDENT_ID, SERIES_ID)).thenReturn(new BigDecimal(netPrice));
    }

    private AttendanceEntity attendance(SessionEntity session, boolean catchUp) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setSession(session);
        attendance.setIsPresent(true);
        attendance.setIsCatchUp(catchUp);
        attendance.setActive(true);
        return attendance;
    }

    private void givenAttendances(AttendanceEntity... attendances) {
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(attendances));
    }

    /** Le résolveur est la source des candidates : on lui fait dire ce qui est facturable. */
    private void givenBillable(List<SessionEntity> billable, List<SessionEntity> excluded) {
        when(billableSessionsResolver.resolve(STUDENT_ID, SERIES_ID)).thenReturn(
                new BillableSessions(billable, excluded, 0, true,
                        billable.stream().map(SessionEntity::getId).collect(java.util.stream.Collectors.toSet())));
    }

    private static List<Long> sessionIds(List<PaymentDetailEntity> details) {
        return details.stream().map(detail -> detail.getSession().getId()).toList();
    }

    private static List<Double> amounts(List<PaymentDetailEntity> details) {
        return details.stream().map(PaymentDetailEntity::getAmountPaid).toList();
    }

    // ------------------------------------------------------------------
    // Exigence 4.5 : aucune affectation hors des séances facturables
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Séance antérieure à l'inscription et non assistée : aucune affectation créée")
    void noDetailIsCreatedOnASessionBeforeEnrolment() {
        // Inscription au 10 janvier : la séance du 3 n'a pas été suivie, elle n'est pas due.
        givenBillable(List.of(session2, session3, session4), List.of(session1));

        List<PaymentDetailEntity> details = service.distribute(imputation("90.00"));

        assertThat(sessionIds(details)).containsExactly(2L, 3L, 4L).doesNotContain(1L);
        // La série entière n'est plus lue : la définition du facturable appartient au résolveur.
        verify(sessionRepository, never()).findBySessionSeriesId(SERIES_ID);
    }

    @Test
    @DisplayName("Aucune séance facturable : aucune affectation, aucune erreur")
    void nothingIsDistributedWhenNoSessionIsBillable() {
        givenBillable(List.of(), List.of(session1, session2));

        assertThat(service.distribute(imputation("60.00"))).isEmpty();
        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
    }

    // ------------------------------------------------------------------
    // Exigence 4.5 : versement intégral réparti sur toutes les facturables
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versement intégral d'une série réparti sur toutes les séances facturables")
    void fullSeriesPaymentCoversEveryBillableSession() {
        givenBillable(List.of(session1, session2, session3, session4), List.of());

        List<PaymentDetailEntity> details = service.distribute(imputation("120.00"));

        assertThat(sessionIds(details)).containsExactly(1L, 2L, 3L, 4L);
        assertThat(amounts(details)).containsOnly(30.0);
        // Rien n'est perdu en route : le montant imputé est intégralement ventilé.
        assertThat(details.stream().mapToDouble(PaymentDetailEntity::getAmountPaid).sum()).isEqualTo(120.0);
    }

    @Test
    @DisplayName("Versement partiel : ventilation dans l'ordre chronologique du résolveur")
    void partialPaymentFollowsTheResolverOrder() {
        givenBillable(List.of(session2, session3, session4), List.of(session1));

        // 45 DA = une séance et demie : la première séance facturable est soldée, la deuxième
        // partiellement, la troisième n'est pas touchée.
        List<PaymentDetailEntity> details = service.distribute(imputation("45.00"));

        assertThat(sessionIds(details)).containsExactly(2L, 3L);
        assertThat(amounts(details)).containsExactly(30.0, 15.0);
    }

    // ------------------------------------------------------------------
    // Une ligne par (Encaissement, séance) — exigence 1.3
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Chaque ligne est une part de l'Imputation ventilée, datée de son Encaissement")
    void everyLineBelongsToTheImputationAndCarriesTheEncashmentDate() {
        givenBillable(List.of(session1, session2), List.of());
        EncashmentAllocationEntity imputation = imputation("60.00");

        List<PaymentDetailEntity> details = service.distribute(imputation);

        assertThat(details).hasSize(2).allSatisfy(detail -> {
            assertThat(detail.getEncashmentAllocation()).isSameAs(imputation);
            assertThat(detail.getPayment()).isSameAs(payment);
            assertThat(detail.getPaymentDate()).isEqualTo(RECEIVED_AT);
        });
    }

    @Test
    @DisplayName("Séance déjà partiellement réglée par un autre Encaissement : une ligne neuve pour le reste, "
            + "l'ancienne jamais complétée")
    void aPartlyPaidSessionReceivesANewLineForTheRemainderOnly() {
        givenBillable(List.of(session1, session2), List.of());
        alreadyVentilated.put(1L, 10.0);

        List<PaymentDetailEntity> details = service.distribute(imputation("30.00"));

        // 20 DA complètent la séance 1, 10 DA ouvrent la séance 2. Aucune ligne existante n'est
        // relue pour être modifiée : seules des lignes neuves sont écrites.
        assertThat(sessionIds(details)).containsExactly(1L, 2L);
        assertThat(amounts(details)).containsExactly(20.0, 10.0);
        assertThat(details).allSatisfy(detail -> assertThat(detail.getId()).isNull());
    }

    @Test
    @DisplayName("Séance déjà soldée par d'autres Encaissements : sautée")
    void aSettledSessionIsSkipped() {
        givenBillable(List.of(session1, session2), List.of());
        alreadyVentilated.put(1L, 30.0);

        List<PaymentDetailEntity> details = service.distribute(imputation("30.00"));

        assertThat(sessionIds(details)).containsExactly(2L);
    }

    @Test
    @DisplayName("Toutes les séances déjà couvertes : rien n'est ventilé, et rien n'échoue")
    void aFullyCoveredSeriesLeavesTheRemainderUnventilatedWithoutFailing() {
        // Avant A.5, une ligne supprimée définitivement levait ici une erreur 500, et la séance
        // ne pouvait plus jamais être payée. Une ligne inactive ne compte plus et ne bloque plus.
        givenBillable(List.of(session1), List.of());
        alreadyVentilated.put(1L, 30.0);

        assertThat(service.distribute(imputation("30.00"))).isEmpty();
    }

    // ------------------------------------------------------------------
    // Plafond au prix net — exigence 1.6
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Étudiant réduit : chaque séance reçoit son prix net, pas le tarif catalogue")
    void eachSessionIsCappedAtTheNetPrice() {
        // Tarif 30 DA, réduction 20 % : 24 DA par séance. Au tarif catalogue, 48 DA auraient
        // « payé » la première séance à 30 DA et laissé la seconde à 18 DA.
        givenNetPrice("24.00");
        givenBillable(List.of(session1, session2, session3), List.of());

        List<PaymentDetailEntity> details = service.distribute(imputation("48.00"));

        assertThat(amounts(details)).containsExactly(24.0, 24.0);
    }

    @Test
    @DisplayName("Étudiant exempté : prix net nul, rien n'est ventilé")
    void anExemptedStudentReceivesNoLine() {
        givenNetPrice("0.00");
        givenBillable(List.of(session1, session2), List.of());

        assertThat(service.distribute(imputation("30.00"))).isEmpty();
        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
    }

    // ------------------------------------------------------------------
    // Mode rattrapage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Rattrapage seul : affectation limitée aux séances de rattrapage, marquées comme telles")
    void catchUpOnlyStudentIsChargedOnCatchUpSessionsOnly() {
        // Toutes les présences de la série sont des rattrapages : l'étudiant ne doit que celles-ci.
        givenAttendances(attendance(session1, true), attendance(session3, true));
        // Le résolveur retient en plus les séances postérieures à l'inscription, dont l'étudiant
        // n'est pas redevable en rattrapage.
        givenBillable(List.of(session1, session2, session3, session4), List.of());

        List<PaymentDetailEntity> details = service.distribute(imputation("120.00"));

        assertThat(sessionIds(details)).containsExactly(1L, 3L);
        assertThat(amounts(details)).containsExactly(30.0, 30.0);
        assertThat(details).allSatisfy(detail -> assertThat(detail.getIsCatchUp()).isTrue());
    }

    @Test
    @DisplayName("Rattrapage hors des facturables : ignoré, il relève de sa propre série")
    void catchUpSessionOutsideTheBillableSetIsIgnored() {
        givenAttendances(attendance(session1, true));
        givenBillable(List.of(session2, session3), List.of());

        assertThat(service.distribute(imputation("60.00"))).isEmpty();
        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
    }

    @Test
    @DisplayName("Inscrit régulier ayant une seule présence : ventilation sur toute la série")
    void aRegularStudentWithOneAttendanceIsNotInCatchUpMode() {
        // Piège déjà corrigé : le critère est « toutes les présences sont des rattrapages », et
        // non « l'étudiant a au moins une présence ». Avec ce dernier, cet étudiant n'aurait reçu
        // qu'une affectation de 30 DA, laissant 90 DA encaissés mais non ventilés.
        givenAttendances(attendance(session1, false));
        givenBillable(List.of(session1, session2, session3, session4), List.of());

        assertThat(sessionIds(service.distribute(imputation("120.00")))).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("Présences mixtes rattrapage et régulière : ventilation sur toute la série")
    void mixedAttendancesKeepTheNormalMode() {
        givenAttendances(attendance(session1, true), attendance(session2, false));
        givenBillable(List.of(session1, session2, session3, session4), List.of());

        List<PaymentDetailEntity> details = service.distribute(imputation("120.00"));

        assertThat(sessionIds(details)).containsExactly(1L, 2L, 3L, 4L);
        // Seule la séance couverte par un rattrapage est marquée comme telle.
        assertThat(details).extracting(PaymentDetailEntity::getIsCatchUp).containsExactly(true, false, false, false);
    }
}
