package com.school.management.repository;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration (H2) des agrégations d'encaissement d'un groupe.
 *
 * <p>Ce qui est verrouillé ici, ce sont les <strong>exclusions</strong> : un versement
 * désactivé, définitivement supprimé, ou rattaché à un paiement ANNULÉ ne doit jamais
 * entrer dans les recettes. Une erreur sur ce point gonfle le chiffre d'affaires
 * silencieusement.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class GroupRevenueQueriesIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PaymentDetailRepository paymentDetailRepository;

    @Autowired
    private RefundRepository refundRepository;

    private GroupEntity group;
    private SessionSeriesEntity series;
    private StudentEntity student;

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void baseFixture() {
        student = em.persist(StudentEntity.builder().firstName("Anis").lastName("Sebti").build());
        group = em.persist(GroupEntity.builder().name("Groupe physique").build());
        series = em.persist(SessionSeriesEntity.builder()
                .name("Série août")
                .group(group)
                .totalSessions(8)
                .serieTimeStart(new Date())
                .build());
    }

    private SessionEntity persistSession(String title, Date start) {
        SessionEntity session = SessionEntity.builder()
                .title(title)
                .group(group)
                .sessionSeries(series)
                .sessionTimeStart(start)
                .build();
        return em.persist(session);
    }

    private PaymentEntity persistPayment(String status) {
        return em.persist(PaymentEntity.builder()
                .student(student)
                .group(group)
                .sessionSeries(series)
                .amountPaid(0.0)
                .status(status)
                .build());
    }

    /**
     * Persiste un versement dans l'état demandé.
     *
     * <p>{@code BaseEntity.onCreate()} force {@code active = true} et
     * {@code PaymentDetailEntity.onCreate()} force {@code paymentDate = now()} : les
     * valeurs du builder sont écrasées à la persistance. On repasse donc par une mise à
     * jour en masse, qui ne déclenche pas les callbacks de cycle de vie, pour atteindre
     * les états « désactivé », « supprimé » et une date d'encaissement choisie.</p>
     */
    private void persistDetail(PaymentEntity payment, SessionEntity session, double amount,
            boolean active, Boolean permanentlyDeleted, Date paymentDate) {
        PaymentDetailEntity detail = PaymentDetailEntity.builder()
                .payment(payment)
                .session(session)
                .amountPaid(amount)
                .build();
        em.persist(detail);
        em.flush();

        em.getEntityManager()
                .createQuery("UPDATE PaymentDetailEntity pd SET pd.active = :active, "
                        + "pd.permanentlyDeleted = :deleted, pd.paymentDate = :paymentDate "
                        + "WHERE pd.id = :id")
                .setParameter("active", active)
                .setParameter("deleted", permanentlyDeleted)
                .setParameter("paymentDate", paymentDate)
                .setParameter("id", detail.getId())
                .executeUpdate();
        em.clear();
    }

    private Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 10, 0, 0);
        return calendar.getTime();
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Total du groupe : seuls les versements actifs et non annulés sont comptés")
    void totalExcludesInactiveDeletedAndCancelled() {
        baseFixture();
        SessionEntity session = persistSession("session 1", date(2026, 8, 19));

        PaymentEntity completed = persistPayment("COMPLETED");
        PaymentEntity cancelled = persistPayment("CANCELLED");

        persistDetail(completed, session, 6000.0, true, false, date(2026, 8, 19));
        persistDetail(completed, session, 500.0, false, false, date(2026, 8, 19));   // désactivé
        persistDetail(completed, session, 700.0, true, true, date(2026, 8, 19));     // supprimé
        persistDetail(cancelled, session, 900.0, true, false, date(2026, 8, 19));    // parent annulé
        em.flush();

        Double total = paymentDetailRepository.sumCollectedForGroup(group.getId());

        assertThat(total).isEqualTo(6000.0);
    }

    @Test
    @DisplayName("Ventilation par série : un montant par série, exclusions appliquées")
    void groupedBySeries() {
        baseFixture();
        SessionEntity session = persistSession("session 1", date(2026, 8, 19));
        PaymentEntity payment = persistPayment("COMPLETED");
        persistDetail(payment, session, 6000.0, true, false, date(2026, 8, 19));
        persistDetail(payment, session, 100.0, false, false, date(2026, 8, 19));
        em.flush();

        List<Object[]> rows = paymentDetailRepository.sumCollectedByGroupGroupedBySeries(group.getId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo(series.getId());
        assertThat(rows.get(0)[1]).isEqualTo("Série août");
        assertThat(((Number) rows.get(0)[2]).doubleValue()).isEqualTo(6000.0);
    }

    @Test
    @DisplayName("Ventilation par séance : un montant par séance, dans l'ordre chronologique")
    void groupedBySession() {
        baseFixture();
        SessionEntity first = persistSession("session 1", date(2026, 8, 10));
        SessionEntity second = persistSession("session 2", date(2026, 8, 17));
        PaymentEntity payment = persistPayment("COMPLETED");
        persistDetail(payment, first, 3000.0, true, false, date(2026, 8, 10));
        persistDetail(payment, second, 6000.0, true, false, date(2026, 8, 17));
        em.flush();

        List<Object[]> rows = paymentDetailRepository.sumCollectedByGroupGroupedBySession(group.getId());

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)[2]).isEqualTo("session 1");
        assertThat(((Number) rows.get(0)[4]).doubleValue()).isEqualTo(3000.0);
        assertThat(rows.get(1)[2]).isEqualTo("session 2");
        assertThat(((Number) rows.get(1)[4]).doubleValue()).isEqualTo(6000.0);
    }

    @Test
    @DisplayName("Ventilation par mois d'encaissement : deux mois distincts, même série")
    void groupedByMonthUsesPaymentDate() {
        baseFixture();
        SessionEntity session = persistSession("session 1", date(2026, 8, 10));
        PaymentEntity payment = persistPayment("COMPLETED");
        // Deux versements sur la même série, encaissés à deux mois différents : l'axe
        // mensuel doit les séparer là où l'axe série les cumule.
        persistDetail(payment, session, 3000.0, true, false, date(2026, 8, 20));
        persistDetail(payment, session, 3000.0, true, false, date(2026, 9, 3));
        em.flush();

        List<Object[]> rows = paymentDetailRepository.sumCollectedByGroupGroupedByMonth(group.getId());

        assertThat(rows).hasSize(2);
        assertThat(((Number) rows.get(0)[1]).intValue()).isEqualTo(8);
        assertThat(((Number) rows.get(0)[2]).doubleValue()).isEqualTo(3000.0);
        assertThat(((Number) rows.get(1)[1]).intValue()).isEqualTo(9);
        assertThat(((Number) rows.get(1)[2]).doubleValue()).isEqualTo(3000.0);
    }

    @Test
    @DisplayName("Remboursements du groupe : total et ventilation par série")
    void refundsForGroup() {
        baseFixture();
        PaymentEntity payment = persistPayment("COMPLETED");
        em.persist(RefundEntity.builder()
                .student(student)
                .payment(payment)
                .amount(new BigDecimal("500.00"))
                .refundDate(new Date())
                // Numéro de pièce obligatoire depuis la migration V2.
                .refundNumber("REMB-2026-0001")
                .build());
        em.flush();

        assertThat(refundRepository.sumRefundsForGroup(group.getId()))
                .isEqualByComparingTo(new BigDecimal("500.00"));

        List<Object[]> rows = refundRepository.sumRefundsByGroupGroupedBySeries(group.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo(series.getId());
        assertThat((BigDecimal) rows.get(0)[1]).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Aucun encaissement → 0 et non null")
    void emptyGroupReturnsZero() {
        baseFixture();
        em.flush();

        assertThat(paymentDetailRepository.sumCollectedForGroup(group.getId())).isEqualTo(0.0);
        assertThat(refundRepository.sumRefundsForGroup(group.getId()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(paymentDetailRepository.sumCollectedByGroupGroupedBySeries(group.getId())).isEmpty();
    }
}
