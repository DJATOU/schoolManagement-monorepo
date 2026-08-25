package com.school.management.repository;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration (Spring Boot Test, H2 en mémoire) des requêtes de dépôt
 * ajoutées/corrigées pour la fonctionnalité payment-attendance-rules.
 *
 * <p>Couvre :</p>
 * <ul>
 *   <li>{@link PaymentRepository#sumAmountPaidForStudentAndSeries(Long, Long)} :
 *       ensemble vide → 0, tous CANCELLED → 0, mélange annulé/non-annulé → somme
 *       des seuls non-annulés (Exigences 5.1, 5.2, 5.3) ;</li>
 *   <li>{@link AttendanceRepository#countPresentForStudentAndSeries(Long, Long)} :
 *       présences comptées tous groupes confondus dans une même série, seules les
 *       présences (isPresent = true) comptent (Exigences 1.3, 6.5) ;</li>
 *   <li>{@link RefundRepository#sumRefundsForStudentAndSeries(Long, Long)} :
 *       aucun remboursement → 0, plusieurs remboursements → somme (Exigence 13.3).</li>
 * </ul>
 *
 * <p>{@code @DataJpaTest} remplace la source de données par une base H2 embarquée.
 * Le {@code @TestPropertySource} force le dialecte H2 et {@code create-drop} pour
 * que Hibernate génère le schéma à partir des entités, sans conflit avec la
 * configuration PostgreSQL du module principal.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class RepositoryQueriesIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private RefundRepository refundRepository;

    // ------------------------------------------------------------------
    // Fabriques de fixtures minimales
    // ------------------------------------------------------------------

    private StudentEntity persistStudent(String firstName) {
        StudentEntity student = StudentEntity.builder()
                .firstName(firstName)
                .lastName("Test")
                .build();
        return em.persist(student);
    }

    private GroupEntity persistGroup(String name) {
        GroupEntity group = GroupEntity.builder()
                .name(name)
                .build();
        return em.persist(group);
    }

    private SessionSeriesEntity persistSeries(GroupEntity group, int totalSessions) {
        SessionSeriesEntity series = SessionSeriesEntity.builder()
                .name("Série " + (group != null ? group.getName() : "?"))
                .group(group)
                .totalSessions(totalSessions)
                .serieTimeStart(new Date())
                .build();
        return em.persist(series);
    }

    private PaymentEntity persistPayment(StudentEntity student,
                                         SessionSeriesEntity series,
                                         double amountPaid,
                                         String status) {
        PaymentEntity payment = PaymentEntity.builder()
                .student(student)
                .sessionSeries(series)
                .amountPaid(amountPaid)
                .status(status)
                .build();
        return em.persist(payment);
    }

    private AttendanceEntity persistAttendance(StudentEntity student,
                                               SessionSeriesEntity series,
                                               GroupEntity group,
                                               boolean present) {
        AttendanceEntity attendance = AttendanceEntity.builder()
                .student(student)
                .sessionSeries(series)
                .group(group)
                .isPresent(present)
                .build();
        return em.persist(attendance);
    }

    private RefundEntity persistRefund(StudentEntity student,
                                       PaymentEntity payment,
                                       BigDecimal amount) {
        RefundEntity refund = RefundEntity.builder()
                .student(student)
                .payment(payment)
                .amount(amount)
                .refundDate(new Date())
                .build();
        return em.persist(refund);
    }

    // ==================================================================
    // PaymentRepository.sumAmountPaidForStudentAndSeries
    // Exigences 5.1, 5.2, 5.3
    // ==================================================================
    @Nested
    @DisplayName("PaymentRepository.sumAmountPaidForStudentAndSeries")
    class SumAmountPaid {

        @Test
        @DisplayName("Aucun paiement → 0 (Exigence 5.2)")
        void emptySetReturnsZero() {
            StudentEntity student = persistStudent("Amine");
            GroupEntity group = persistGroup("G-empty");
            SessionSeriesEntity series = persistSeries(group, 8);
            em.flush();

            BigDecimal sum = paymentRepository.sumAmountPaidForStudentAndSeries(
                    student.getId(), series.getId());

            assertThat(sum).isNotNull();
            assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Tous les paiements CANCELLED → 0 (Exigence 5.3)")
        void allCancelledReturnsZero() {
            StudentEntity student = persistStudent("Bilal");
            GroupEntity group = persistGroup("G-cancelled");
            SessionSeriesEntity series = persistSeries(group, 8);
            persistPayment(student, series, 100.0, "CANCELLED");
            persistPayment(student, series, 50.0, "CANCELLED");
            em.flush();

            BigDecimal sum = paymentRepository.sumAmountPaidForStudentAndSeries(
                    student.getId(), series.getId());

            assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Mélange annulé / non annulé → somme des seuls non annulés (Exigence 5.1)")
        void mixedCancelledAndActiveSumsOnlyActive() {
            StudentEntity student = persistStudent("Chaima");
            GroupEntity group = persistGroup("G-mixed");
            SessionSeriesEntity series = persistSeries(group, 8);
            persistPayment(student, series, 120.0, "COMPLETED"); // compté
            persistPayment(student, series, 30.0, "PENDING");    // compté
            persistPayment(student, series, 200.0, "CANCELLED"); // exclu
            em.flush();

            BigDecimal sum = paymentRepository.sumAmountPaidForStudentAndSeries(
                    student.getId(), series.getId());

            assertThat(sum).isEqualByComparingTo(new BigDecimal("150"));
        }

        @Test
        @DisplayName("Les paiements d'une autre série ne sont pas comptés")
        void otherSeriesPaymentsExcluded() {
            StudentEntity student = persistStudent("Driss");
            GroupEntity group = persistGroup("G-scope");
            SessionSeriesEntity series = persistSeries(group, 8);
            SessionSeriesEntity otherSeries = persistSeries(group, 8);
            persistPayment(student, series, 80.0, "COMPLETED");
            persistPayment(student, otherSeries, 300.0, "COMPLETED"); // autre série
            em.flush();

            BigDecimal sum = paymentRepository.sumAmountPaidForStudentAndSeries(
                    student.getId(), series.getId());

            assertThat(sum).isEqualByComparingTo(new BigDecimal("80"));
        }
    }

    // ==================================================================
    // AttendanceRepository.countPresentForStudentAndSeries
    // Exigences 1.3, 6.5
    // ==================================================================
    @Nested
    @DisplayName("AttendanceRepository.countPresentForStudentAndSeries")
    class CountPresent {

        @Test
        @DisplayName("Aucune présence → 0")
        void noAttendanceReturnsZero() {
            StudentEntity student = persistStudent("Emna");
            GroupEntity group = persistGroup("G-none");
            SessionSeriesEntity series = persistSeries(group, 8);
            em.flush();

            long count = attendanceRepository.countPresentForStudentAndSeries(
                    student.getId(), series.getId());

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("Comptage tous groupes confondus, seules les présences comptent (Exigences 1.3, 6.5)")
        void crossGroupPresentOnly() {
            StudentEntity student = persistStudent("Farid");
            GroupEntity groupA = persistGroup("G-A");
            GroupEntity groupB = persistGroup("G-B");
            SessionSeriesEntity series = persistSeries(groupA, 8);

            // Groupe A : 2 présences, 1 absence
            persistAttendance(student, series, groupA, true);
            persistAttendance(student, series, groupA, true);
            persistAttendance(student, series, groupA, false);
            // Groupe B (même série) : 1 présence, 1 absence
            persistAttendance(student, series, groupB, true);
            persistAttendance(student, series, groupB, false);
            em.flush();

            long count = attendanceRepository.countPresentForStudentAndSeries(
                    student.getId(), series.getId());

            // 3 présences au total sur les deux groupes ; les 2 absences sont exclues
            assertThat(count).isEqualTo(3L);
        }

        @Test
        @DisplayName("Les présences d'un autre étudiant ne sont pas comptées")
        void otherStudentAttendanceExcluded() {
            StudentEntity student = persistStudent("Ghita");
            StudentEntity other = persistStudent("Hamza");
            GroupEntity group = persistGroup("G-shared");
            SessionSeriesEntity series = persistSeries(group, 8);

            persistAttendance(student, series, group, true);
            persistAttendance(other, series, group, true); // autre étudiant
            em.flush();

            long count = attendanceRepository.countPresentForStudentAndSeries(
                    student.getId(), series.getId());

            assertThat(count).isEqualTo(1L);
        }
    }

    // ==================================================================
    // RefundRepository.sumRefundsForStudentAndSeries
    // Exigence 13.3
    // ==================================================================
    @Nested
    @DisplayName("RefundRepository.sumRefundsForStudentAndSeries")
    class SumRefunds {

        @Test
        @DisplayName("Aucun remboursement → 0")
        void noRefundReturnsZero() {
            StudentEntity student = persistStudent("Imane");
            GroupEntity group = persistGroup("G-norefund");
            SessionSeriesEntity series = persistSeries(group, 8);
            persistPayment(student, series, 100.0, "COMPLETED");
            em.flush();

            BigDecimal sum = refundRepository.sumRefundsForStudentAndSeries(
                    student.getId(), series.getId());

            assertThat(sum).isNotNull();
            assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Plusieurs remboursements → somme agrégée (Exigence 13.3)")
        void multipleRefundsSummed() {
            StudentEntity student = persistStudent("Jalil");
            GroupEntity group = persistGroup("G-refunds");
            SessionSeriesEntity series = persistSeries(group, 8);
            PaymentEntity payment = persistPayment(student, series, 240.0, "COMPLETED");
            persistRefund(student, payment, new BigDecimal("30.00"));
            persistRefund(student, payment, new BigDecimal("20.50"));
            em.flush();

            BigDecimal sum = refundRepository.sumRefundsForStudentAndSeries(
                    student.getId(), series.getId());

            assertThat(sum).isEqualByComparingTo(new BigDecimal("50.50"));
        }

        @Test
        @DisplayName("Les remboursements liés à une autre série ne sont pas comptés")
        void otherSeriesRefundsExcluded() {
            StudentEntity student = persistStudent("Karim");
            GroupEntity group = persistGroup("G-refund-scope");
            SessionSeriesEntity series = persistSeries(group, 8);
            SessionSeriesEntity otherSeries = persistSeries(group, 8);
            PaymentEntity payment = persistPayment(student, series, 240.0, "COMPLETED");
            PaymentEntity otherPayment = persistPayment(student, otherSeries, 240.0, "COMPLETED");
            persistRefund(student, payment, new BigDecimal("40.00"));
            persistRefund(student, otherPayment, new BigDecimal("99.99")); // autre série
            em.flush();

            BigDecimal sum = refundRepository.sumRefundsForStudentAndSeries(
                    student.getId(), series.getId());

            assertThat(sum).isEqualByComparingTo(new BigDecimal("40.00"));
        }
    }
}
