package com.school.management.repository;

import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.StudentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration (Spring Boot Test, H2 en mémoire) de la persistance de la note de
 * paiement (requirement 11.1, 11.2, 11.3).
 *
 * <p>Vérifie l'aller-retour réel en base : un paiement enregistré avec une note la restitue
 * telle quelle à la relecture ; un paiement enregistré sans note stocke {@code null}. Complète
 * la propriété 19 (aller-retour via le mapper) par une preuve de persistance JPA effective.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class PaymentNotePersistenceIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PaymentRepository paymentRepository;

    private StudentEntity persistStudent() {
        StudentEntity student = StudentEntity.builder()
                .firstName("Note")
                .lastName("Test")
                .build();
        return em.persist(student);
    }

    @Test
    void paymentWithNote_roundTripsThroughDatabase() {
        StudentEntity student = persistStudent();
        PaymentEntity payment = PaymentEntity.builder()
                .student(student)
                .amountPaid(120.0)
                .notes("versement partiel de janvier")
                .build();

        PaymentEntity saved = paymentRepository.save(payment);
        em.flush();
        em.clear();

        Optional<PaymentEntity> reloaded = paymentRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getNotes()).isEqualTo("versement partiel de janvier");
    }

    @Test
    void paymentWithEmptyNote_roundTripsThroughDatabase() {
        StudentEntity student = persistStudent();
        PaymentEntity payment = PaymentEntity.builder()
                .student(student)
                .amountPaid(60.0)
                .notes("")
                .build();

        PaymentEntity saved = paymentRepository.save(payment);
        em.flush();
        em.clear();

        Optional<PaymentEntity> reloaded = paymentRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getNotes()).isEqualTo("");
    }

    @Test
    void paymentWithoutNote_persistsNull() {
        StudentEntity student = persistStudent();
        PaymentEntity payment = PaymentEntity.builder()
                .student(student)
                .amountPaid(80.0)
                .build();

        PaymentEntity saved = paymentRepository.save(payment);
        em.flush();
        em.clear();

        Optional<PaymentEntity> reloaded = paymentRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getNotes()).isNull();
    }
}
