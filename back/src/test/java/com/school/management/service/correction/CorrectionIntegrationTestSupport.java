package com.school.management.service.correction;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.CorrectionAuditRepository;
import com.school.management.repository.EncashmentAllocationRepository;
import com.school.management.repository.EncashmentRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentIdempotencyRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.ReceiptCounterRepository;
import com.school.management.repository.RefundReceiptIssuanceRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.payment.PaymentAllocationResult;
import com.school.management.service.payment.PaymentProcessingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Socle des tests d'intégration des corrections : une base H2 réelle, partagée par toutes les
 * classes qui en héritent (même configuration, donc même contexte Spring), vidée avant chaque test.
 *
 * <p>Données : l'année 2029-2030 courante, le groupe « Math 1ère A » à 2 000 DA la séance, l'élève
 * Amine Belkacem inscrit, deux Séries de deux séances, « Janvier » et « Février ». Un administrateur
 * authentifié, au nom duquel les corrections sont tracées (exigence 11.4).</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:correction-runner;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
public abstract class CorrectionIntegrationTestSupport {

    protected static final double PRICE = 2000.0;
    protected static final String ADMIN = "admin-test";

    @Autowired protected PaymentProcessingService processing;
    @Autowired protected JdbcTemplate jdbc;

    @Autowired protected SchoolYearRepository schoolYearRepository;
    @Autowired protected PricingRepository pricingRepository;
    @Autowired protected GroupRepository groupRepository;
    @Autowired protected StudentRepository studentRepository;
    @Autowired protected StudentGroupRepository studentGroupRepository;
    @Autowired protected SessionSeriesRepository seriesRepository;
    @Autowired protected SessionRepository sessionRepository;
    @Autowired protected AttendanceRepository attendanceRepository;
    @Autowired protected PaymentRepository paymentRepository;
    @Autowired protected PaymentDetailRepository paymentDetailRepository;
    @Autowired protected PaymentCarryOverRepository carryOverRepository;
    @Autowired protected PaymentIdempotencyRepository idempotencyRepository;
    @Autowired protected EncashmentRepository encashmentRepository;
    @Autowired protected EncashmentAllocationRepository allocationRepository;
    @Autowired protected ReceiptCounterRepository receiptCounterRepository;
    @Autowired protected CorrectionAuditRepository auditRepository;
    @Autowired protected RefundRepository refundRepository;
    @Autowired protected RefundReceiptIssuanceRepository refundIssuanceRepository;

    protected SchoolYearEntity year;
    protected GroupEntity group;
    protected StudentEntity student;
    protected SessionSeriesEntity s1;
    protected SessionSeriesEntity s2;
    protected SessionEntity s1First;

    /** Base vidée avant chaque test : {@code @SpringBootTest} n'annule aucune transaction. */
    @BeforeEach
    protected void setUpCorrectionData() {
        authenticate(ADMIN);
        auditRepository.deleteAll();
        deleteDomainData();

        year = schoolYearRepository.save(SchoolYearEntity.builder()
                .label("2029-2030").startDate(date(2029, 9, 1)).endDate(date(2030, 6, 30))
                .isCurrent(true).build());
        PricingEntity pricing = pricingRepository.save(PricingEntity.builder().price(PRICE).build());
        group = groupRepository.save(GroupEntity.builder()
                .name("Math 1ère A").price(pricing).schoolYear(year).sessionNumberPerSerie(2).build());
        student = studentRepository.save(StudentEntity.builder().firstName("Amine").lastName("Belkacem").build());
        studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(group).build());
        s1 = persistSeries(group, "Janvier", date(2030, 1, 7), date(2030, 1, 14));
        s1First = sessionRepository.findAll().stream()
                .filter(s -> s.getSessionSeries().getId().equals(s1.getId()))
                .min((a, b) -> a.getSessionTimeStart().compareTo(b.getSessionTimeStart())).orElseThrow();
        s2 = persistSeries(group, "Février", date(2030, 2, 4), date(2030, 2, 11));
    }

    @AfterEach
    protected void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** Toutes les données métier, traces exceptées. */
    protected void deleteDomainData() {
        refundIssuanceRepository.deleteAll();
        refundRepository.deleteAll();
        idempotencyRepository.deleteAll();
        carryOverRepository.deleteAll();
        paymentDetailRepository.deleteAll();
        allocationRepository.deleteAll();
        encashmentRepository.deleteAll();
        receiptCounterRepository.deleteAll();
        paymentRepository.deleteAll();
        attendanceRepository.deleteAll();
        // Paie des enseignants : ses paies désignent séries, groupes et enseignants.
        jdbc.update("DELETE FROM payout_slip_issuance");
        jdbc.update("UPDATE teacher_payout SET replaced_by_id = NULL, replaces_id = NULL, initial_payout_id = NULL");
        jdbc.update("DELETE FROM teacher_payout");
        jdbc.update("DELETE FROM teacher_pay_rate");
        jdbc.update("DELETE FROM payout_counter");
        sessionRepository.deleteAll();
        seriesRepository.deleteAll();
        studentGroupRepository.deleteAll();
        groupRepository.deleteAll();
        jdbc.update("DELETE FROM teacher");
        studentRepository.deleteAll();
        pricingRepository.deleteAll();
        schoolYearRepository.deleteAll();
    }

    protected static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    protected PaymentAllocationResult pay(SessionSeriesEntity series, double amount) {
        return processing.processPayment(student.getId(), group.getId(), series.getId(), amount);
    }

    protected void attend(SessionEntity session) {
        attendanceRepository.save(AttendanceEntity.builder()
                .student(student).session(session).sessionSeries(session.getSessionSeries()).group(group)
                .isPresent(true).build());
    }

    protected SessionSeriesEntity persistSeries(GroupEntity g, String name, Date... sessionDates) {
        SessionSeriesEntity series = seriesRepository.save(SessionSeriesEntity.builder()
                .name(name).group(g).totalSessions(2).serieTimeStart(sessionDates[0]).build());
        for (int i = 0; i < sessionDates.length; i++) {
            sessionRepository.save(SessionEntity.builder()
                    .title(name + " — séance " + (i + 1)).group(g).sessionSeries(series)
                    .sessionTimeStart(sessionDates[i]).build());
        }
        return series;
    }

    protected static AmountSnapshot snapshot(String cost, String due, String paid, String remaining, boolean late) {
        return new AmountSnapshot(new BigDecimal(cost), new BigDecimal(due), new BigDecimal(paid),
                new BigDecimal(remaining), late);
    }

    /** Ce qu'une correction annulée ne doit pas toucher, relu en SQL, traces comprises. */
    protected record Ledger(long encashments, long activeEncashments, long activeAllocations, long activeLines,
                            long activeCarryOvers, BigDecimal cumuls, long payments, long receiptRank,
                            long traces) {
    }

    protected Ledger ledger() {
        return new Ledger(
                count("SELECT COUNT(*) FROM encashment"),
                count("SELECT COUNT(*) FROM encashment WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM encashment_allocation WHERE active = TRUE"),
                count("SELECT COUNT(*) FROM payment_detail WHERE active = TRUE"),
                count("SELECT COUNT(*) FROM payment_carry_over WHERE active = TRUE"),
                jdbc.queryForObject("SELECT COALESCE(SUM(amount_paid), 0) FROM payments", BigDecimal.class)
                        .setScale(2, RoundingMode.HALF_UP),
                count("SELECT COUNT(*) FROM payments"),
                lastRank(),
                count("SELECT COUNT(*) FROM correction_audit"));
    }

    protected long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    protected long lastRank() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(last_rank), 0) FROM receipt_counter", Long.class);
    }

    protected String statusOf(Long encashmentId) {
        return jdbc.queryForObject("SELECT status FROM encashment WHERE id = ?", String.class, encashmentId);
    }

    protected String receiptOf(Long encashmentId) {
        return jdbc.queryForObject("SELECT receipt_number FROM encashment WHERE id = ?", String.class, encashmentId);
    }

    /** Cumul de l'élève sur une Série, relu en SQL. */
    protected BigDecimal cumulOf(SessionSeriesEntity series) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount_paid), 0) FROM payments "
                        + "WHERE student_id = ? AND session_series_id = ?", BigDecimal.class,
                student.getId(), series.getId()).setScale(2, RoundingMode.HALF_UP);
    }

    protected static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }
}
