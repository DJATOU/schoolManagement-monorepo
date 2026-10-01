package com.school.management.repository;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lignes de ventilation sur une vraie base (H2) : part déjà ventilée d'une séance, et date d'une
 * ligne (spec admin-corrections, A.5).
 *
 * <p>Une séance porte désormais une ligne par Encaissement. Ce qu'elle a déjà reçu est la somme de
 * ses lignes <b>actives</b> : une ligne désactivée, ou supprimée définitivement, ne compte plus et
 * ne bloque plus rien. Et une ligne garde la date de son Encaissement : une mise à jour ne la
 * redate plus.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("Ventilation : part déjà ventilée et date des lignes")
class PaymentDetailVentilationIntegrationTest {

    @Autowired private TestEntityManager em;
    @Autowired private PaymentDetailRepository repository;

    private SessionEntity first;
    private SessionEntity second;
    private PaymentEntity payment;
    private PaymentEntity otherPayment;

    private static Date at(int month, int day) {
        return Date.from(LocalDateTime.of(2030, month, day, 10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    @BeforeEach
    void setUp() {
        StudentEntity student = em.persist(StudentEntity.builder().firstName("Amine").lastName("Belkacem").build());
        StudentEntity other = em.persist(StudentEntity.builder().firstName("Lina").lastName("Hamdani").build());
        GroupEntity group = em.persist(GroupEntity.builder().name("Math 1ère A").build());
        SessionSeriesEntity series = em.persist(SessionSeriesEntity.builder().name("Série 1").group(group).build());
        first = em.persist(SessionEntity.builder().title("Séance 1").group(group).sessionSeries(series)
                .sessionTimeStart(at(1, 7)).build());
        second = em.persist(SessionEntity.builder().title("Séance 2").group(group).sessionSeries(series)
                .sessionTimeStart(at(1, 14)).build());
        payment = em.persist(PaymentEntity.builder().student(student).group(group).sessionSeries(series)
                .amountPaid(0.0).build());
        otherPayment = em.persist(PaymentEntity.builder().student(other).group(group).sessionSeries(series)
                .amountPaid(0.0).build());
    }

    private PaymentDetailEntity line(PaymentEntity owner, SessionEntity session, double amount) {
        return em.persist(PaymentDetailEntity.builder()
                .payment(owner).session(session).amountPaid(amount).paymentDate(at(1, 10)).build());
    }

    @Test
    @DisplayName("part ventilée = somme des lignes actives de la séance, une par Encaissement")
    void sumOfActiveLinesOfTheSession() {
        line(payment, first, 1000.0);
        line(payment, first, 500.0);
        line(payment, second, 700.0);
        line(otherPayment, first, 2000.0);

        assertThat(repository.sumActiveAmountForPaymentAndSession(payment.getId(), first.getId()))
                .isEqualTo(1500.0);
    }

    @Test
    @DisplayName("ligne désactivée ou supprimée définitivement : ne compte plus, ne bloque plus")
    void inactiveAndPermanentlyDeletedLinesDoNotCount() {
        line(payment, first, 1000.0);
        PaymentDetailEntity deactivated = line(payment, first, 2000.0);
        PaymentDetailEntity deleted = line(payment, first, 300.0);
        em.flush();
        deactivated.setActive(false);
        deleted.setActive(false);
        deleted.setPermanentlyDeleted(true);
        em.flush();

        assertThat(repository.sumActiveAmountForPaymentAndSession(payment.getId(), first.getId()))
                .isEqualTo(1000.0);
    }

    @Test
    @DisplayName("séance sans ligne : zéro, pas de résultat vide")
    void noLineIsZero() {
        assertThat(repository.sumActiveAmountForPaymentAndSession(payment.getId(), second.getId()))
                .isZero();
    }

    @Test
    @DisplayName("une mise à jour ne redate pas la ligne : elle reste au mois de son Encaissement")
    void anUpdateDoesNotRedateTheLine() {
        // Avant A.5, chaque écriture redatait la ligne à l'instant : une simple désactivation, ou
        // le complément d'une séance un autre mois, déplaçait l'argent dans les recettes par mois.
        PaymentDetailEntity detail = line(payment, first, 1000.0);
        em.flush();
        detail.setActive(false);
        detail.setAmountPaid(900.0);
        em.flush();
        em.clear();

        PaymentDetailEntity reloaded = em.find(PaymentDetailEntity.class, detail.getId());
        assertThat(reloaded.getPaymentDate().getTime()).isEqualTo(at(1, 10).getTime());
    }

    @Test
    @DisplayName("ligne créée sans date : datée de l'instant")
    void aLineWithoutDateIsDatedNow() {
        Date before = new Date();
        PaymentDetailEntity detail = em.persist(PaymentDetailEntity.builder()
                .payment(payment).session(first).amountPaid(100.0).build());
        em.flush();
        em.clear();

        assertThat(em.find(PaymentDetailEntity.class, detail.getId()).getPaymentDate().getTime())
                .isGreaterThanOrEqualTo(before.getTime() - 1);
    }
}
