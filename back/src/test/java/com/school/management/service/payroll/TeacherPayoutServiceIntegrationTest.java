package com.school.management.service.payroll;

import com.school.management.config.security.SecurityAuditorAware;
import com.school.management.dto.payroll.PayRequest;
import com.school.management.dto.payroll.PayableSeriesDTO;
import com.school.management.dto.payroll.PayableSeriesDTO.PayableState;
import com.school.management.dto.payroll.PayoutDTO;
import com.school.management.dto.payroll.PayoutListDTO;
import com.school.management.dto.payroll.PayoutPreviewDTO;
import com.school.management.dto.payroll.PayoutSlipDTO;
import com.school.management.dto.payroll.TeacherPayRateRequest;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PayoutKind;
import com.school.management.persistance.PayoutStatus;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.persistance.TeacherPayRateEntity;
import com.school.management.persistance.TeacherPayoutEntity;
import com.school.management.repository.PayoutCounterRepository;
import com.school.management.repository.TeacherPayoutRepository;
import com.school.management.service.CurrentSchoolYearService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.SeriesCollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payer un enseignant, de bout en bout sur H2 (spec teacher-payroll, exigences 2 à 6 et 9).
 *
 * <h2>Le scénario de base</h2>
 * Nadia Aït Ahmed enseigne au groupe « Maths 4 AM A ». Sa série d'octobre compte deux séances,
 * validées. Ali a versé 40 000 DA, Sara 34 000 DA, dont 2 000 DA lui ont été rendus : 72 000 DA
 * encaissés nets. Au taux Standard de 60 %, l'enseignante reçoit 43 200 DA, l'école garde 28 800 DA.
 *
 * <p>La garantie « une seule paie initiale sous deux confirmations simultanées » (P2) dépend du
 * verrou de PostgreSQL : elle est éprouvée par {@code MigrationSchemaPostgresIntegrationTest}.</p>
 */
@DataJpaTest
@Import({ TeacherPayoutService.class, TeacherPayRateService.class, SeriesCollectionService.class,
        SeriesCompletionService.class, PayoutNumberService.class, CurrentSchoolYearService.class,
        PayoutSlipService.class, SecurityAuditorAware.class })
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("Paie des enseignants : séries à payer, paie, régularisation, consultation")
class TeacherPayoutServiceIntegrationTest {

    private static final String YEAR = String.valueOf(LocalDate.now(ZoneId.systemDefault()).getYear());

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TeacherPayoutService payouts;

    @Autowired
    private TeacherPayRateService rates;

    @Autowired
    private PayoutSlipService slips;

    @Autowired
    private TeacherPayoutRepository payoutRepository;

    @Autowired
    private PayoutCounterRepository counterRepository;

    private final AtomicInteger refundRank = new AtomicInteger();

    private TeacherEntity nadia;
    private GroupEntity group;
    private SessionSeriesEntity october;
    private StudentEntity ali;
    private StudentEntity sara;
    private TeacherPayRateEntity standard;
    private TeacherPayRateEntity expert;

    // ------------------------------------------------------------------
    // Jeu de données
    // ------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        nadia = teacher("Nadia", "Aït Ahmed");
        group = group("Maths 4 AM A", nadia, null);
        october = series(group, "Octobre");
        sessions(group, october, 2, 2);

        ali = em.persist(StudentEntity.builder().firstName("Ali").lastName("Bensalem").build());
        sara = em.persist(StudentEntity.builder().firstName("Sara").lastName("Merbah").build());
        pay(ali, october, "40000");
        refund(pay(sara, october, "34000"), "2000");

        standard = rate("Standard", "60");
        expert = rate("Expert", "70");
        em.flush();
    }

    private TeacherEntity teacher(String first, String last) {
        return em.persist(TeacherEntity.builder().firstName(first).lastName(last).build());
    }

    private GroupEntity group(String name, TeacherEntity teacher, SchoolYearEntity year) {
        return em.persist(GroupEntity.builder().name(name).teacher(teacher).schoolYear(year).build());
    }

    private SessionSeriesEntity series(GroupEntity owner, String name) {
        return em.persist(SessionSeriesEntity.builder().name(name).group(owner).serieTimeStart(new Date()).build());
    }

    /** {@code total} séances actives, dont les {@code validated} premières validées. */
    private void sessions(GroupEntity owner, SessionSeriesEntity series, int validated, int total) {
        for (int i = 0; i < total; i++) {
            em.persist(SessionEntity.builder().title("Séance " + (i + 1)).group(owner).sessionSeries(series)
                    .isFinished(i < validated).sessionTimeStart(new Date()).build());
        }
    }

    private PaymentEntity pay(StudentEntity student, SessionSeriesEntity series, String amount) {
        return em.persist(PaymentEntity.builder().student(student).group(series.getGroup()).sessionSeries(series)
                .amountPaid(Double.valueOf(amount)).paymentDate(new Date()).status("COMPLETED").build());
    }

    private void refund(PaymentEntity payment, String amount) {
        em.persist(RefundEntity.builder().payment(payment).student(payment.getStudent())
                .amount(new BigDecimal(amount)).refundDate(new Date()).reason("Trop-perçu")
                .refundNumber("REMB-2030-" + String.format("%04d", refundRank.incrementAndGet())).build());
    }

    private TeacherPayRateEntity rate(String label, String percent) {
        // À l'échelle de la colonne, numeric(5,2) : c'est ainsi qu'un taux est relu en base.
        return em.persist(TeacherPayRateEntity.builder().label(label)
                .teacherPercent(new BigDecimal(percent).setScale(2)).build());
    }

    private SchoolYearEntity year(String label, boolean current) {
        return em.persist(SchoolYearEntity.builder().label(label).startDate(new Date()).endDate(new Date())
                .isCurrent(current).build());
    }

    private static PayRequest ask(TeacherPayRateEntity rate) {
        return new PayRequest(rate == null ? null : rate.getId(), null, null);
    }

    private static PayRequest confirm(PayoutPreviewDTO preview, String note) {
        return new PayRequest(preview.rateId(), note, preview.previewToken());
    }

    private PayoutDTO payOctober() {
        PayoutPreviewDTO preview = payouts.previewPay(october.getId(), ask(standard));
        return payouts.confirmPay(october.getId(), confirm(preview, null));
    }

    private PayoutDTO regularizeOctober() {
        PayoutPreviewDTO preview = payouts.previewRegularize(october.getId());
        return payouts.confirmRegularize(october.getId(), confirm(preview, null));
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((CustomServiceException) e).getStatus();
    }

    private PayableSeriesDTO onlyRow(Long groupId) {
        List<PayableSeriesDTO> rows = payouts.payable(null, groupId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private static void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected).hasScaleOf(2);
    }

    // ------------------------------------------------------------------
    // Séries à payer (exigence 2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("série terminée et encaissée : à payer, avec enseignant, séances, brut, remboursé et net")
    void finishedSeriesIsPayable() {
        PayableSeriesDTO row = onlyRow(group.getId());

        assertThat(row.state()).isEqualTo(PayableState.PAYABLE);
        assertThat(row.seriesId()).isEqualTo(october.getId());
        assertThat(row.seriesName()).isEqualTo("Octobre");
        assertThat(row.groupName()).isEqualTo("Maths 4 AM A");
        assertThat(row.teacherId()).isEqualTo(nadia.getId());
        assertThat(row.teacherName()).isEqualTo("Nadia Aït Ahmed");
        assertThat(row.activeSessions()).isEqualTo(2);
        assertThat(row.validatedSessions()).isEqualTo(2);
        assertMoney(row.collectedGross(), "74000");
        assertMoney(row.refunded(), "2000");
        assertMoney(row.collectedNet(), "72000");
        assertThat(row.initialPayoutNumber()).isNull();
        assertThat(row.gap()).isNull();
    }

    @Test
    @DisplayName("série en cours : listée non terminée, séances validées sur actives ; série sans séance absente")
    void unfinishedAndEmptySeries() {
        SessionSeriesEntity november = series(group, "Novembre");
        sessions(group, november, 1, 2);
        series(group, "Décembre");

        List<PayableSeriesDTO> rows = payouts.payable(null, group.getId());

        assertThat(rows).extracting(PayableSeriesDTO::seriesName).containsExactly("Octobre", "Novembre");
        PayableSeriesDTO ongoing = rows.get(1);
        assertThat(ongoing.state()).isEqualTo(PayableState.NOT_FINISHED);
        assertThat(ongoing.activeSessions()).isEqualTo(2);
        assertThat(ongoing.validatedSessions()).isEqualTo(1);
    }

    @Test
    @DisplayName("groupe sans enseignant : série terminée signalée comme telle, sans enseignant")
    void groupWithoutTeacher() {
        GroupEntity orphan = group("Physique 3 AS", null, null);
        SessionSeriesEntity series = series(orphan, "Octobre");
        sessions(orphan, series, 1, 1);
        pay(ali, series, "5000");

        PayableSeriesDTO row = onlyRow(orphan.getId());
        assertThat(row.state()).isEqualTo(PayableState.NO_TEACHER);
        assertThat(row.teacherId()).isNull();
        assertThat(row.teacherName()).isNull();
    }

    @Test
    @DisplayName("rien d'encaissé, ou tout rendu : signalé, pas à payer")
    void nothingCollected() {
        GroupEntity empty = group("Anglais 2 AM", nadia, null);
        SessionSeriesEntity unpaid = series(empty, "Octobre");
        sessions(empty, unpaid, 1, 1);
        SessionSeriesEntity refunded = series(empty, "Novembre");
        sessions(empty, refunded, 1, 1);
        refund(pay(ali, refunded, "3000"), "3000");

        assertThat(payouts.payable(null, empty.getId()))
                .extracting(PayableSeriesDTO::state)
                .containsExactly(PayableState.NOTHING_COLLECTED, PayableState.NOTHING_COLLECTED);
    }

    @Test
    @DisplayName("série payée et à jour : absente ; de l'argent arrivé depuis : à régulariser, écart compris")
    void paidSeriesLeavesOrNeedsRegularization() {
        PayoutDTO initial = payOctober();
        assertThat(payouts.payable(null, group.getId())).isEmpty();

        pay(ali, october, "3000");
        PayableSeriesDTO row = onlyRow(group.getId());
        assertThat(row.state()).isEqualTo(PayableState.TO_REGULARIZE);
        assertThat(row.initialPayoutNumber()).isEqualTo(initial.payoutNumber());
        assertMoney(row.teacherPercent(), "60");
        assertMoney(row.teacherPaid(), "43200");
        assertMoney(row.collectedNet(), "75000");
        assertMoney(row.gap(), "1800");
    }

    @Test
    @DisplayName("argent rendu après la paie : écart négatif, à régulariser (retenue)")
    void refundAfterPayoutGivesANegativeGap() {
        payOctober();
        refund(firstPaymentOf(ali), "1000");

        PayableSeriesDTO row = onlyRow(group.getId());
        assertThat(row.state()).isEqualTo(PayableState.TO_REGULARIZE);
        assertMoney(row.gap(), "-600");
    }

    private PaymentEntity firstPaymentOf(StudentEntity student) {
        return em.getEntityManager().createQuery(
                        "SELECT p FROM PaymentEntity p WHERE p.student = :s ORDER BY p.id", PaymentEntity.class)
                .setParameter("s", student).setMaxResults(1).getSingleResult();
    }

    @Test
    @DisplayName("filtre par enseignant : celui du groupe, ou celui de la paie initiale après réaffectation")
    void teacherFilter() {
        TeacherEntity karim = teacher("Karim", "Haddad");
        GroupEntity other = group("SVT 1 AS", karim, null);
        SessionSeriesEntity series = series(other, "Octobre");
        sessions(other, series, 1, 1);
        pay(sara, series, "8000");

        assertThat(payouts.payable(karim.getId(), null)).extracting(PayableSeriesDTO::groupName)
                .containsExactly("SVT 1 AS");
        assertThat(payouts.payable(nadia.getId(), null)).extracting(PayableSeriesDTO::groupName)
                .containsExactly("Maths 4 AM A");

        // Payée à Nadia puis le groupe passe à Karim : l'écart reste celui de Nadia.
        payOctober();
        pay(ali, october, "1000");
        group.setTeacher(karim);
        assertThat(payouts.payable(nadia.getId(), null)).singleElement()
                .extracting(PayableSeriesDTO::state).isEqualTo(PayableState.TO_REGULARIZE);
        assertThat(payouts.payable(karim.getId(), null)).extracting(PayableSeriesDTO::groupName)
                .containsExactly("SVT 1 AS");
    }

    @Test
    @DisplayName("sans filtre : groupes actifs par nom ; groupe désactivé ignoré ; groupe inconnu 404")
    void scopeWithoutFilter() {
        GroupEntity first = group("Arabe 1 AM", nadia, null);
        SessionSeriesEntity series = series(first, "Octobre");
        sessions(first, series, 1, 1);
        pay(ali, series, "1000");
        GroupEntity disabled = group("Zoologie", nadia, null);
        sessions(disabled, series(disabled, "Octobre"), 1, 1);
        group("Bureautique", nadia, null);               // aucune série : aucune ligne
        em.flush();
        disabled.setActive(false);

        assertThat(payouts.payable(null, null)).extracting(PayableSeriesDTO::groupName)
                .containsExactly("Arabe 1 AM", "Maths 4 AM A");
        assertThatThrownBy(() -> payouts.payable(null, 9_999L))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("année courante : toutes ses séries ; années passées : seulement ce qui appelle un paiement")
    void pastYearsOnlyShowWhatCallsForPayment() {
        SchoolYearEntity past = year("2029-2030", false);
        SchoolYearEntity current = year("2030-2031", true);
        group.setSchoolYear(past);                         // Octobre, à payer : reste listée
        SessionSeriesEntity paidOfPast = series(group, "Mai");
        sessions(group, paidOfPast, 1, 1);
        pay(ali, paidOfPast, "2000");
        payouts.confirmPay(paidOfPast.getId(), confirm(payouts.previewPay(paidOfPast.getId(), ask(standard)), null));
        pay(sara, paidOfPast, "1000");                     // Mai, à régulariser : reste listée
        SessionSeriesEntity lastOfPast = series(group, "Juin");
        sessions(group, lastOfPast, 1, 2);                 // en cours d'une année passée : bruit

        GroupEntity now = group("Maths 1 AS", nadia, current);
        SessionSeriesEntity ongoing = series(now, "Septembre");
        sessions(now, ongoing, 0, 2);

        assertThat(payouts.payable(null, null))
                .extracting(PayableSeriesDTO::seriesName, PayableSeriesDTO::state)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Septembre", PayableState.NOT_FINISHED),
                        org.assertj.core.groups.Tuple.tuple("Octobre", PayableState.PAYABLE),
                        org.assertj.core.groups.Tuple.tuple("Mai", PayableState.TO_REGULARIZE));
        // Le filtre de groupe montre tout, année passée comprise.
        assertThat(payouts.payable(null, group.getId())).extracting(PayableSeriesDTO::seriesName)
                .containsExactly("Octobre", "Mai", "Juin");
        // Groupe sans année, alors qu'une année est courante : traité comme passé.
        GroupEntity undated = group("Chimie", nadia, null);
        sessions(undated, series(undated, "Octobre"), 0, 1);
        assertThat(payouts.payable(null, null)).extracting(PayableSeriesDTO::groupName)
                .doesNotContain("Chimie");
    }

    // ------------------------------------------------------------------
    // Séances prévues et séries payées (retours de tests)
    // ------------------------------------------------------------------

    private SessionSeriesEntity plannedSeries(GroupEntity owner, String name, int planned) {
        return em.persist(SessionSeriesEntity.builder().name(name).group(owner).totalSessions(planned)
                .serieTimeStart(new Date()).build());
    }

    @Test
    @DisplayName("séances prévues : 2 créées et validées sur 3 prévues, payable, une séance signalée manquante")
    void plannedSessionsAreReportedWithoutBlockingPayment() {
        GroupEntity english = group("Anglais 1 AS", nadia, null);
        SessionSeriesEntity series = plannedSeries(english, "11-2026-001", 3);
        sessions(english, series, 2, 2);
        pay(ali, series, "6000");

        PayableSeriesDTO row = onlyRow(english.getId());

        // Série_Terminée porte sur les séances actives (design D5, D9) : la séance prévue qui n'est
        // pas encore créée est une information, pas une condition de paie.
        assertThat(row.state()).isEqualTo(PayableState.PAYABLE);
        assertThat(row.validatedSessions()).isEqualTo(2);
        assertThat(row.activeSessions()).isEqualTo(2);
        assertThat(row.plannedSessions()).isEqualTo(3);
        assertThat(row.missingSessions()).isEqualTo(1);
    }

    @Test
    @DisplayName("séance annulée : elle a occupé sa place, la série n'est pas incomplète ; nombre prévu inconnu : rien")
    void cancelledSessionIsNotMissing() {
        GroupEntity english = group("Anglais 1 AS", nadia, null);
        SessionSeriesEntity series = plannedSeries(english, "11-2026-001", 3);
        sessions(english, series, 3, 3);
        em.flush();
        em.getEntityManager().createQuery("SELECT s FROM SessionEntity s WHERE s.sessionSeries = :series "
                        + "ORDER BY s.id DESC", SessionEntity.class)
                .setParameter("series", series).setMaxResults(1).getSingleResult().setActive(false);

        PayableSeriesDTO row = onlyRow(english.getId());
        assertThat(row.activeSessions()).isEqualTo(2);
        assertThat(row.plannedSessions()).isEqualTo(3);
        assertThat(row.missingSessions()).isZero();

        // Le socle ne renseigne pas le nombre prévu : ni nombre prévu, ni séance manquante.
        PayableSeriesDTO october = onlyRow(group.getId());
        assertThat(october.plannedSessions()).isZero();
        assertThat(october.missingSessions()).isZero();
    }

    @Test
    @DisplayName("« Afficher les séries payées » : payée et à jour listée PAID, sans écart ; masquée par défaut")
    void paidSeriesOnRequest() {
        PayoutDTO initial = payOctober();

        assertThat(payouts.payable(null, group.getId())).isEmpty();
        PayableSeriesDTO row = payouts.payable(null, group.getId(), true).get(0);
        assertThat(row.state()).isEqualTo(PayableState.PAID);
        assertThat(row.initialPayoutNumber()).isEqualTo(initial.payoutNumber());
        assertThat(row.teacherName()).isEqualTo("Nadia Aït Ahmed");
        assertMoney(row.teacherPaid(), "43200");
        assertThat(row.gap()).isEqualByComparingTo("0");

        // De l'argent arrivé depuis : à régulariser, avec ou sans la case.
        pay(ali, october, "3000");
        assertThat(payouts.payable(null, group.getId(), true)).extracting(PayableSeriesDTO::state)
                .containsExactly(PayableState.TO_REGULARIZE);
    }

    @Test
    @DisplayName("séries payées d'une année passée : pas sans filtre de groupe, même demandées ; avec le groupe, oui")
    void paidSeriesOfPastYearsStayOutWithoutGroupFilter() {
        SchoolYearEntity past = year("2029-2030", false);
        year("2030-2031", true);
        group.setSchoolYear(past);
        payOctober();

        assertThat(payouts.payable(null, null, true)).extracting(PayableSeriesDTO::seriesName)
                .doesNotContain("Octobre");
        assertThat(payouts.payable(null, group.getId(), true)).extracting(PayableSeriesDTO::state)
                .containsExactly(PayableState.PAID);
    }

    // ------------------------------------------------------------------
    // Aperçu et paie initiale (exigences 3 et 4)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("aperçu : 72 000 × 60 % = 43 200, école 28 800 ; rien n'est écrit, aucun numéro consommé (P4)")
    void previewComputesAndWritesNothing() {
        PayoutPreviewDTO preview = payouts.previewPay(october.getId(), ask(standard));

        assertThat(preview.kind()).isEqualTo(PayoutKind.INITIAL);
        assertThat(preview.teacherName()).isEqualTo("Nadia Aït Ahmed");
        assertThat(preview.groupName()).isEqualTo("Maths 4 AM A");
        assertThat(preview.seriesName()).isEqualTo("Octobre");
        assertThat(preview.rateId()).isEqualTo(standard.getId());
        assertThat(preview.rateLabel()).isEqualTo("Standard");
        assertMoney(preview.teacherPercent(), "60");
        assertMoney(preview.collectedGross(), "74000");
        assertMoney(preview.refunded(), "2000");
        assertMoney(preview.collectedNet(), "72000");
        assertMoney(preview.netCovered(), "0");
        assertMoney(preview.baseDelta(), "72000");
        assertMoney(preview.teacherAmount(), "43200");
        assertMoney(preview.schoolAmount(), "28800");
        assertMoney(preview.teacherPaid(), "0");
        assertThat(preview.previewToken()).matches("[0-9a-f]{64}");

        assertThat(payoutRepository.count()).isZero();
        assertThat(counterRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("jeton : identique pour deux aperçus égaux, différent pour un autre taux")
    void tokenIsAFingerprint() {
        String first = payouts.previewPay(october.getId(), ask(standard)).previewToken();
        String again = payouts.previewPay(october.getId(), ask(standard)).previewToken();
        String other = payouts.previewPay(october.getId(), ask(expert)).previewToken();

        assertThat(again).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
    }

    @Test
    @DisplayName("confirmation : numéro, date et auteur du serveur, copie figée du taux, des parts, de la note")
    void confirmRecordsAFrozenCopy() {
        PayoutPreviewDTO preview = payouts.previewPay(october.getId(), ask(standard));
        Date before = new Date();

        PayoutDTO paid = payouts.confirmPay(october.getId(), confirm(preview, "  Remis en main propre  "));

        assertThat(paid.payoutNumber()).isEqualTo("PAIE-" + YEAR + "-0001");
        assertThat(paid.kind()).isEqualTo(PayoutKind.INITIAL);
        assertThat(paid.initialPayoutNumber()).isNull();
        assertThat(paid.teacherId()).isEqualTo(nadia.getId());
        assertThat(paid.groupId()).isEqualTo(group.getId());
        assertThat(paid.seriesId()).isEqualTo(october.getId());
        assertThat(paid.rateLabel()).isEqualTo("Standard");
        assertMoney(paid.teacherPercent(), "60");
        assertMoney(paid.collectedGross(), "74000");
        assertMoney(paid.refunded(), "2000");
        assertMoney(paid.collectedNet(), "72000");
        assertMoney(paid.baseDelta(), "72000");
        assertMoney(paid.teacherAmount(), "43200");
        assertMoney(paid.schoolAmount(), "28800");
        assertThat(paid.note()).isEqualTo("Remis en main propre");
        assertThat(paid.paidAt()).isAfterOrEqualTo(before);
        assertThat(paid.paidBy()).isEqualTo("system");
        assertThat(paid.status()).isEqualTo(PayoutStatus.ACTIVE);

        TeacherPayoutEntity stored = payoutRepository.findById(paid.id()).orElseThrow();
        assertThat(stored.getRate().getId()).isEqualTo(standard.getId());

        // Le taux est modifié ensuite : la paie n'en garde que sa copie (exigence 1.6).
        rates.update(standard.getId(), new TeacherPayRateRequest("Standard révisé", new BigDecimal("65")));
        rates.disable(standard.getId());
        PayoutDTO reread = payouts.get(paid.id());
        assertThat(reread.rateLabel()).isEqualTo("Standard");
        assertMoney(reread.teacherPercent(), "60");
        assertMoney(reread.teacherAmount(), "43200");
    }

    @Test
    @DisplayName("note vide : aucune note enregistrée")
    void blankNoteIsDropped() {
        PayoutPreviewDTO preview = payouts.previewPay(october.getId(), ask(standard));
        assertThat(payouts.confirmPay(october.getId(), confirm(preview, "   ")).note()).isNull();
    }

    @Test
    @DisplayName("encaissement après l'aperçu : confirmation refusée avec le nouvel aperçu ; rien écrit (P4)")
    void staleConfirmationIsRefused() {
        PayoutPreviewDTO preview = payouts.previewPay(october.getId(), ask(standard));
        pay(ali, october, "1000");

        assertThatThrownBy(() -> payouts.confirmPay(october.getId(), confirm(preview, null)))
                .isInstanceOfSatisfying(StalePayoutPreviewException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertMoney(e.getPreview().collectedNet(), "73000");
                    assertMoney(e.getPreview().teacherAmount(), "43800");
                    assertThat(e.getPreview().previewToken()).isNotEqualTo(preview.previewToken());
                });
        assertThat(payoutRepository.count()).isZero();
        assertThat(counterRepository.findAll()).isEmpty();

        // Le nouvel aperçu, lui, se confirme.
        PayoutPreviewDTO fresh = payouts.previewPay(october.getId(), ask(standard));
        assertMoney(payouts.confirmPay(october.getId(), confirm(fresh, null)).teacherAmount(), "43800");
    }

    @Test
    @DisplayName("pourcentage du taux changé après l'aperçu : confirmation périmée")
    void rateChangedAfterPreviewIsStale() {
        PayoutPreviewDTO preview = payouts.previewPay(october.getId(), ask(standard));
        rates.update(standard.getId(), new TeacherPayRateRequest("Standard", new BigDecimal("65")));

        assertThatThrownBy(() -> payouts.confirmPay(october.getId(), confirm(preview, null)))
                .isInstanceOf(StalePayoutPreviewException.class);
    }

    @Test
    @DisplayName("jeton d'un autre taux, ou jeton altéré : périmé ; sans jeton : 400")
    void tokenMustMatchTheConfirmedRequest() {
        PayoutPreviewDTO preview = payouts.previewPay(october.getId(), ask(standard));

        assertThatThrownBy(() -> payouts.confirmPay(october.getId(),
                new PayRequest(expert.getId(), null, preview.previewToken())))
                .isInstanceOf(StalePayoutPreviewException.class);
        assertThatThrownBy(() -> payouts.confirmPay(october.getId(),
                new PayRequest(standard.getId(), null, "0".repeat(64))))
                .isInstanceOf(StalePayoutPreviewException.class);
        assertThatThrownBy(() -> payouts.confirmPay(october.getId(), new PayRequest(standard.getId(), null, "  ")))
                .hasMessageContaining("sans aperçu")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> payouts.confirmPay(october.getId(), new PayRequest(standard.getId(), null, null)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(payoutRepository.count()).isZero();

        // Espaces de bord tolérés : le jeton est relu nettoyé.
        assertThat(payouts.confirmPay(october.getId(),
                new PayRequest(standard.getId(), null, " " + preview.previewToken() + " ")).payoutNumber())
                .isEqualTo("PAIE-" + YEAR + "-0001");
    }

    @Test
    @DisplayName("série déjà payée : 409 nommant la paie et renvoyant vers la régularisation")
    void alreadyPaidSeriesIsRefused() {
        PayoutDTO paid = payOctober();

        assertThatThrownBy(() -> payouts.previewPay(october.getId(), ask(expert)))
                .hasMessageContaining("déjà payée (" + paid.payoutNumber() + ")")
                .hasMessageContaining("régularisation")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("série non terminée : 409 disant combien de séances restent ; série vide : 409")
    void unfinishedSeriesIsRefused() {
        SessionSeriesEntity november = series(group, "Novembre");
        sessions(group, november, 1, 3);
        SessionSeriesEntity empty = series(group, "Décembre");

        assertThatThrownBy(() -> payouts.previewPay(november.getId(), ask(standard)))
                .hasMessageContaining("n'est pas terminée : 2 séance(s) sur 3 restent à valider")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> payouts.previewPay(empty.getId(), ask(standard)))
                .hasMessageContaining("ne compte aucune séance")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("groupe sans enseignant, ou série sans groupe : 409 nommé")
    void noTeacherIsRefused() {
        group.setTeacher(null);
        assertThatThrownBy(() -> payouts.previewPay(october.getId(), ask(standard)))
                .hasMessageContaining("« Maths 4 AM A » n'a pas d'enseignant")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));

        SessionSeriesEntity loose = em.persist(SessionSeriesEntity.builder().name("Hors groupe")
                .serieTimeStart(new Date()).build());
        assertThatThrownBy(() -> payouts.previewPay(loose.getId(), ask(standard)))
                .hasMessageContaining("rattachée à aucun groupe")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("taux non choisi 400, désactivé 409, introuvable 404 ; série introuvable 404")
    void rateAndSeriesRefusals() {
        assertThatThrownBy(() -> payouts.previewPay(october.getId(), ask(null)))
                .hasMessageContaining("Choisissez le taux")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));

        rates.disable(expert.getId());
        assertThatThrownBy(() -> payouts.previewPay(october.getId(), ask(expert)))
                .hasMessageContaining("désactivé")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> payouts.previewPay(october.getId(), new PayRequest(9_999L, null, null)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> payouts.previewPay(9_999L, ask(standard)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> payouts.confirmPay(9_999L, new PayRequest(standard.getId(), null, "x")))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("taux désactivé entre l'aperçu et la confirmation : 409, rien n'est écrit")
    void rateDisabledBeforeConfirmation() {
        PayoutPreviewDTO preview = payouts.previewPay(october.getId(), ask(standard));
        rates.disable(standard.getId());

        assertThatThrownBy(() -> payouts.confirmPay(october.getId(), confirm(preview, null)))
                .hasMessageContaining("désactivé")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThat(payoutRepository.count()).isZero();
    }

    @Test
    @DisplayName("encaissé net nul : 409 « rien à partager »")
    void nothingToShareIsRefused() {
        GroupEntity empty = group("Anglais 2 AM", nadia, null);
        SessionSeriesEntity series = series(empty, "Octobre");
        sessions(empty, series, 1, 1);

        assertThatThrownBy(() -> payouts.previewPay(series.getId(), ask(standard)))
                .hasMessageContaining("Rien à partager")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    // ------------------------------------------------------------------
    // Régularisation (exigence 6)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("complément : 3 000 arrivés depuis → 1 800 à l'enseignante, 1 200 à l'école, rattaché à l'initiale")
    void complementAfterLatePayment() {
        PayoutDTO initial = payOctober();
        pay(ali, october, "3000");

        PayoutPreviewDTO preview = payouts.previewRegularize(october.getId());
        assertThat(preview.kind()).isEqualTo(PayoutKind.REGULARIZATION);
        assertThat(preview.rateId()).isEqualTo(standard.getId());
        assertThat(preview.rateLabel()).isEqualTo("Standard");
        assertMoney(preview.collectedNet(), "75000");
        assertMoney(preview.netCovered(), "72000");
        assertMoney(preview.baseDelta(), "3000");
        assertMoney(preview.teacherAmount(), "1800");
        assertMoney(preview.schoolAmount(), "1200");
        assertMoney(preview.teacherPaid(), "43200");

        PayoutDTO regularization = payouts.confirmRegularize(october.getId(), confirm(preview, "Retard d'Ali"));
        assertThat(regularization.payoutNumber()).isEqualTo("PAIE-" + YEAR + "-0002");
        assertThat(regularization.kind()).isEqualTo(PayoutKind.REGULARIZATION);
        assertThat(regularization.initialPayoutNumber()).isEqualTo(initial.payoutNumber());
        assertMoney(regularization.teacherAmount(), "1800");
        assertThat(regularization.note()).isEqualTo("Retard d'Ali");
        assertThat(payoutRepository.findById(regularization.id()).orElseThrow().getInitialPayout().getId())
                .isEqualTo(initial.id());

        // La série est de nouveau à jour.
        assertThat(payouts.payable(null, group.getId())).isEmpty();
    }

    @Test
    @DisplayName("retenue : 1 000 rendus depuis → −600 à l'enseignante, −400 à l'école")
    void deductionAfterRefund() {
        payOctober();
        refund(firstPaymentOf(ali), "1000");

        PayoutDTO deduction = regularizeOctober();
        assertMoney(deduction.baseDelta(), "-1000");
        assertMoney(deduction.teacherAmount(), "-600");
        assertMoney(deduction.schoolAmount(), "-400");
        assertMoney(deduction.collectedNet(), "71000");
    }

    @Test
    @DisplayName("deux régularisations : la seconde couvre depuis la précédente et compte tout ce qui a été versé")
    void secondRegularizationStartsFromTheLatest() {
        payOctober();
        pay(ali, october, "3000");
        regularizeOctober();
        pay(sara, october, "1000");

        PayoutPreviewDTO preview = payouts.previewRegularize(october.getId());
        assertMoney(preview.netCovered(), "75000");
        assertMoney(preview.baseDelta(), "1000");
        assertMoney(preview.teacherPaid(), "45000");
        assertMoney(preview.teacherAmount(), "600");
        assertMoney(preview.schoolAmount(), "400");
    }

    @Test
    @DisplayName("au pourcentage, à l'enseignant et au groupe figés de l'initiale, même taux modifié et groupe réaffecté")
    void regularizationKeepsTheInitialTerms() {
        PayoutDTO initial = payOctober();
        rates.update(standard.getId(), new TeacherPayRateRequest("Standard", new BigDecimal("70")));
        rates.disable(standard.getId());
        TeacherEntity karim = teacher("Karim", "Haddad");
        group.setTeacher(karim);
        pay(ali, october, "1000");

        PayoutPreviewDTO preview = payouts.previewRegularize(october.getId());
        assertMoney(preview.teacherPercent(), "60");
        assertThat(preview.rateLabel()).isEqualTo("Standard");
        assertThat(preview.teacherId()).isEqualTo(nadia.getId());
        assertMoney(preview.teacherAmount(), "600");

        PayoutDTO regularization = payouts.confirmRegularize(october.getId(), confirm(preview, null));
        assertThat(regularization.teacherId()).isEqualTo(initial.teacherId());
        assertThat(regularization.groupId()).isEqualTo(initial.groupId());
        assertMoney(regularization.teacherPercent(), "60");
        assertThat(regularization.rateLabel()).isEqualTo("Standard");
    }

    @Test
    @DisplayName("écart nul : 409 « rien à régulariser » ; série jamais payée : 409 ; introuvable : 404")
    void regularizationRefusals() {
        assertThatThrownBy(() -> payouts.previewRegularize(october.getId()))
                .hasMessageContaining("pas encore été payée")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));

        payOctober();
        assertThatThrownBy(() -> payouts.previewRegularize(october.getId()))
                .hasMessageContaining("Rien à régulariser")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> payouts.previewRegularize(9_999L))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(payoutRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("régularisation périmée : refusée avec le nouvel aperçu, aucun numéro consommé ; sans jeton : 400")
    void staleRegularizationIsRefused() {
        payOctober();
        pay(ali, october, "3000");
        PayoutPreviewDTO preview = payouts.previewRegularize(october.getId());
        pay(sara, october, "1000");

        assertThatThrownBy(() -> payouts.confirmRegularize(october.getId(), confirm(preview, null)))
                .isInstanceOfSatisfying(StalePayoutPreviewException.class,
                        e -> assertMoney(e.getPreview().teacherAmount(), "2400"));
        assertThatThrownBy(() -> payouts.confirmRegularize(october.getId(), new PayRequest(null, null, null)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(payoutRepository.count()).isEqualTo(1);
        assertThat(payouts.confirmRegularize(october.getId(),
                confirm(payouts.previewRegularize(october.getId()), null)).payoutNumber())
                .isEqualTo("PAIE-" + YEAR + "-0002");
    }

    @Test
    @DisplayName("une paie de plus, même série : le jeton d'avant ne vaut plus")
    void anotherPayoutInvalidatesTheToken() {
        payOctober();
        pay(ali, october, "3000");
        PayoutPreviewDTO preview = payouts.previewRegularize(october.getId());
        regularizeOctober();
        pay(ali, october, "3000");   // même écart de 1 800 qu'au premier aperçu

        assertThatThrownBy(() -> payouts.confirmRegularize(october.getId(), confirm(preview, null)))
                .isInstanceOf(StalePayoutPreviewException.class);
    }

    // ------------------------------------------------------------------
    // Consultation (exigence 9)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("recherche : la plus récente d'abord, totaux des seules paies actives")
    void searchTotalsExcludeCancelled() {
        PayoutDTO initial = payOctober();
        pay(ali, october, "3000");
        PayoutDTO regularization = regularizeOctober();
        TeacherPayoutEntity cancelled = payoutRepository.findById(regularization.id()).orElseThrow();
        cancelled.setStatus(PayoutStatus.CANCELLED);
        cancelled.setCancelledAt(new Date());
        cancelled.setCancelledBy("admin");
        cancelled.setCancelReasonType(CorrectionReasonType.DATA_ENTRY_ERROR);
        em.flush();

        PayoutListDTO all = payouts.search(null, null, null, null, null);
        assertThat(all.payouts()).extracting(PayoutDTO::id).containsExactly(regularization.id(), initial.id());
        assertMoney(all.teacherTotal(), "43200");
        assertMoney(all.schoolTotal(), "28800");

        PayoutListDTO onlyCancelled = payouts.search(null, null, PayoutStatus.CANCELLED, null, null);
        assertThat(onlyCancelled.payouts()).extracting(PayoutDTO::id).containsExactly(regularization.id());
        assertThat(onlyCancelled.payouts().get(0).cancelReasonType()).isEqualTo(CorrectionReasonType.DATA_ENTRY_ERROR);
        assertMoney(onlyCancelled.teacherTotal(), "0");
    }

    @Test
    @DisplayName("recherche : par enseignant, par groupe, et par période bornes incluses")
    void searchFilters() {
        PayoutDTO initial = payOctober();
        TeacherEntity karim = teacher("Karim", "Haddad");
        GroupEntity other = group("SVT 1 AS", karim, null);
        SessionSeriesEntity series = series(other, "Octobre");
        sessions(other, series, 1, 1);
        pay(sara, series, "10000");
        PayoutDTO karims = payouts.confirmPay(series.getId(),
                confirm(payouts.previewPay(series.getId(), ask(expert)), null));

        assertThat(payouts.search(karim.getId(), null, null, null, null).payouts())
                .extracting(PayoutDTO::id).containsExactly(karims.id());
        assertThat(payouts.search(null, group.getId(), null, null, null).payouts())
                .extracting(PayoutDTO::id).containsExactly(initial.id());

        Date today = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date tomorrow = Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date yesterday = Date.from(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        // « Jusqu'à aujourd'hui » couvre toute la journée, pas seulement minuit.
        assertThat(payouts.search(null, null, null, today, today).payouts()).hasSize(2);
        assertThat(payouts.search(null, null, null, tomorrow, null).payouts()).isEmpty();
        assertThat(payouts.search(null, null, null, null, yesterday).payouts()).isEmpty();
        PayoutListDTO both = payouts.search(null, null, PayoutStatus.ACTIVE, yesterday, tomorrow);
        assertMoney(both.teacherTotal(), "50200");
        assertMoney(both.schoolTotal(), "31800");
    }

    @Test
    @DisplayName("fiche enseignant : ses paies ; enseignant inconnu 404 ; paie inconnue 404")
    void teacherPayoutsAndLookups() {
        PayoutDTO initial = payOctober();

        PayoutListDTO list = payouts.forTeacher(nadia.getId());
        assertThat(list.payouts()).extracting(PayoutDTO::id).containsExactly(initial.id());
        assertMoney(list.teacherTotal(), "43200");
        assertThat(payouts.forTeacher(teacher("Karim", "Haddad").getId()).payouts()).isEmpty();

        assertThatThrownBy(() -> payouts.forTeacher(9_999L))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> payouts.get(9_999L))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(payouts.get(initial.id()).payoutNumber()).isEqualTo(initial.payoutNumber());
    }

    // ------------------------------------------------------------------
    // Bordereau (exigence 5)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("bordereau : rang 1 puis 2 (duplicata), auteur, nom de fichier stable ; annulée réimprimable")
    void slipsAreCounted() {
        PayoutDTO initial = payOctober();

        PayoutSlipDTO first = slips.issue(initial.id());
        PayoutSlipDTO second = slips.issue(initial.id());
        assertThat(first.issuanceRank()).isEqualTo(1);
        assertThat(second.issuanceRank()).isEqualTo(2);
        assertThat(first.issuedBy()).isEqualTo("system");
        assertThat(first.issuedAt()).isNotNull();
        assertThat(first.payout().payoutNumber()).isEqualTo(initial.payoutNumber());
        assertThat(first.fileName()).isEqualTo("paie-" + YEAR + "-0001_nadia_ait_ahmed.pdf")
                .isEqualTo(second.fileName());

        TeacherPayoutEntity cancelled = payoutRepository.findById(initial.id()).orElseThrow();
        cancelled.setStatus(PayoutStatus.CANCELLED);
        cancelled.setCancelledAt(new Date());
        cancelled.setCancelledBy("admin");
        cancelled.setCancelReasonType(CorrectionReasonType.WRONG_AMOUNT);
        PayoutSlipDTO third = slips.issue(initial.id());
        assertThat(third.issuanceRank()).isEqualTo(3);
        assertThat(third.payout().status()).isEqualTo(PayoutStatus.CANCELLED);

        assertThatThrownBy(() -> slips.issue(9_999L))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
