package com.school.management.service.payment;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ventilation d'un montant imputé sur les séances d'une série (exigence 4.5).
 *
 * <p>Ce qui est vérifié ici est l'ensemble des séances autorisées à recevoir un
 * {@code payment_detail}. La ventilation lisait la série entière : une séance tenue avant
 * l'arrivée de l'étudiant dans le groupe et à laquelle il n'a pas assisté recevait donc une
 * affectation que la facturation ne reconnaît pas. Les candidates viennent désormais du
 * {@link BillableSessionsResolver}, source unique de la définition (exigence 1.5).</p>
 *
 * <p>Le mode rattrapage est conservé, avec son critère d'origine : <strong>toutes</strong> les
 * présences de la série sont des rattrapages. Un test verrouille explicitement le piège déjà
 * corrigé une fois dans ce dépôt — « l'étudiant a au moins une présence » faisait basculer un
 * inscrit régulier en mode rattrapage dès sa première séance suivie, et laissait non ventilée la
 * part d'un mois réglé d'avance.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentDistributionServiceTest {

    private static final Long STUDENT_ID = 5L;
    private static final Long SERIES_ID = 20L;
    private static final double PRICE = 30.0;

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

    @BeforeEach
    void setUp() {
        service = new PaymentDistributionService(sessionRepository, paymentDetailRepository,
                attendanceRepository, paymentQuoteService, billableSessionsResolver);

        payment = payment(900L, 0.0);
        // Aucune affectation préexistante : chaque séance retenue donne une création.
        when(paymentDetailRepository.findByPaymentIdAndSessionId(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(paymentDetailRepository.save(any(PaymentDetailEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
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

    private PaymentEntity payment(Long id, double alreadyPaid) {
        StudentEntity student = new StudentEntity();
        student.setId(STUDENT_ID);

        PricingEntity pricing = new PricingEntity();
        pricing.setPrice(PRICE);

        GroupEntity group = new GroupEntity();
        group.setId(3L);
        group.setPrice(pricing);
        group.setSessionNumberPerSerie(4);

        PaymentEntity entity = new PaymentEntity();
        entity.setId(id);
        entity.setStudent(student);
        entity.setGroup(group);
        entity.setAmountPaid(alreadyPaid);
        return entity;
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
                new BillableSessions(billable, excluded, 0, true, date("2025-01-10")));
    }

    /** Affectations créées ou mises à jour, dans l'ordre de la ventilation. */
    private List<PaymentDetailEntity> savedDetails() {
        ArgumentCaptor<PaymentDetailEntity> captor = ArgumentCaptor.forClass(PaymentDetailEntity.class);
        verify(paymentDetailRepository, atLeastOnce()).save(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    // ------------------------------------------------------------------
    // Exigence 4.5 : aucune affectation hors des séances facturables
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Séance antérieure à l'inscription et non assistée : aucune affectation créée")
    void noDetailIsCreatedOnASessionBeforeEnrolment() {
        givenAttendances();
        // Inscription au 10 janvier : la séance du 3 n'a pas été suivie, elle n'est pas due.
        givenBillable(List.of(session2, session3, session4), List.of(session1));

        service.distributePayment(payment, SERIES_ID, 90.0);

        assertThat(savedDetails()).extracting(detail -> detail.getSession().getId())
                .containsExactly(2L, 3L, 4L)
                .doesNotContain(1L);
        // La série entière n'est plus lue : la définition du facturable appartient au résolveur.
        verify(sessionRepository, never()).findBySessionSeriesId(SERIES_ID);
    }

    @Test
    @DisplayName("Aucune séance facturable : aucune affectation, aucune erreur")
    void nothingIsDistributedWhenNoSessionIsBillable() {
        givenAttendances();
        givenBillable(List.of(), List.of(session1, session2));

        service.distributePayment(payment, SERIES_ID, 60.0);

        verify(paymentDetailRepository, never()).save(any(PaymentDetailEntity.class));
    }

    // ------------------------------------------------------------------
    // Exigence 4.5 : versement intégral réparti sur toutes les facturables
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versement intégral d'une série réparti sur toutes les séances facturables")
    void fullSeriesPaymentCoversEveryBillableSession() {
        givenAttendances();
        givenBillable(List.of(session1, session2, session3, session4), List.of());

        service.distributePayment(payment, SERIES_ID, 120.0);

        List<PaymentDetailEntity> details = savedDetails();
        assertThat(details).extracting(detail -> detail.getSession().getId())
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(details).extracting(PaymentDetailEntity::getAmountPaid)
                .containsOnly(PRICE);
        // Rien n'est perdu en route : le montant imputé est intégralement ventilé.
        assertThat(details.stream().mapToDouble(PaymentDetailEntity::getAmountPaid).sum())
                .isEqualTo(120.0);
    }

    @Test
    @DisplayName("Versement partiel : ventilation dans l'ordre chronologique du résolveur")
    void partialPaymentFollowsTheResolverOrder() {
        givenAttendances();
        givenBillable(List.of(session2, session3, session4), List.of(session1));

        // 45 DA = une séance et demie : la première séance facturable est soldée, la deuxième
        // partiellement, la troisième n'est pas touchée.
        service.distributePayment(payment, SERIES_ID, 45.0);

        List<PaymentDetailEntity> details = savedDetails();
        assertThat(details).extracting(detail -> detail.getSession().getId())
                .containsExactly(2L, 3L);
        assertThat(details).extracting(PaymentDetailEntity::getAmountPaid)
                .containsExactly(30.0, 15.0);
    }

    // ------------------------------------------------------------------
    // Mode rattrapage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Rattrapage seul : affectation limitée aux séances de rattrapage")
    void catchUpOnlyStudentIsChargedOnCatchUpSessionsOnly() {
        // Toutes les présences de la série sont des rattrapages : l'étudiant ne doit que celles-ci.
        givenAttendances(attendance(session1, true), attendance(session3, true));
        // Le résolveur retient en plus les séances postérieures à l'inscription, dont l'étudiant
        // n'est pas redevable en rattrapage.
        givenBillable(List.of(session1, session2, session3, session4), List.of());

        service.distributePayment(payment, SERIES_ID, 120.0);

        List<PaymentDetailEntity> details = savedDetails();
        assertThat(details).extracting(detail -> detail.getSession().getId())
                .containsExactly(1L, 3L);
        assertThat(details).extracting(PaymentDetailEntity::getAmountPaid)
                .containsExactly(30.0, 30.0);
    }

    @Test
    @DisplayName("Rattrapage hors des facturables : ignoré, il relève de sa propre série")
    void catchUpSessionOutsideTheBillableSetIsIgnored() {
        givenAttendances(attendance(session1, true));
        givenBillable(List.of(session2, session3), List.of());

        service.distributePayment(payment, SERIES_ID, 60.0);

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

        service.distributePayment(payment, SERIES_ID, 120.0);

        assertThat(savedDetails()).extracting(detail -> detail.getSession().getId())
                .containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("Présences mixtes rattrapage et régulière : ventilation sur toute la série")
    void mixedAttendancesKeepTheNormalMode() {
        givenAttendances(attendance(session1, true), attendance(session2, false));
        givenBillable(List.of(session1, session2, session3, session4), List.of());

        service.distributePayment(payment, SERIES_ID, 120.0);

        assertThat(savedDetails()).extracting(detail -> detail.getSession().getId())
                .containsExactly(1L, 2L, 3L, 4L);
    }
}
