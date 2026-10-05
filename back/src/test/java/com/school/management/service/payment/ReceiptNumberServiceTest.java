package com.school.management.service.payment;

import com.school.management.persistance.ReceiptCounterEntity;
import com.school.management.repository.ReceiptCounterRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Numérotation des reçus (spec admin-corrections, exigence 1.2).
 *
 * <p>Sur H2 : séquence, changement d'année, largeur, transaction obligatoire. Le verrou entre deux
 * encaissements simultanés, et l'absence de numéro consommé par une transaction annulée, sont
 * éprouvés sur PostgreSQL par {@code MigrationSchemaPostgresIntegrationTest}.</p>
 */
@DataJpaTest
@Import(ReceiptNumberService.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("Numérotation des reçus")
class ReceiptNumberServiceTest {

    @Autowired
    private ReceiptNumberService numbers;

    @Autowired
    private ReceiptCounterRepository counterRepository;

    /** Instant local : l'année d'un reçu est celle du fuseau du serveur, celui de l'école. */
    private static Date local(int year, int month, int day, int hour, int minute) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("premier reçu de l'année : RECU-AAAA-0001, puis séquence continue")
    void numbersFollowEachOther() {
        assertThat(numbers.next(local(2030, 1, 7, 10, 0))).isEqualTo("RECU-2030-0001");
        assertThat(numbers.next(local(2030, 1, 7, 10, 5))).isEqualTo("RECU-2030-0002");
        assertThat(numbers.next(local(2030, 3, 1, 9, 0))).isEqualTo("RECU-2030-0003");
    }

    @Test
    @DisplayName("nouvelle année civile : la séquence repart à 0001")
    void newYearRestartsTheSequence() {
        numbers.next(local(2030, 12, 31, 18, 0));
        numbers.next(local(2030, 12, 31, 18, 5));

        assertThat(numbers.next(local(2031, 1, 2, 9, 0))).isEqualTo("RECU-2031-0001");
        assertThat(numbers.next(local(2031, 1, 2, 9, 5))).isEqualTo("RECU-2031-0002");
    }

    @Test
    @DisplayName("année prise dans le fuseau du serveur : 31 décembre 23h30 reste dans l'année")
    void yearIsTheLocalYear() {
        assertThat(numbers.next(local(2030, 12, 31, 23, 30))).startsWith("RECU-2030-");
        assertThat(numbers.next(local(2031, 1, 1, 0, 30))).startsWith("RECU-2031-");
    }

    @Test
    @DisplayName("horloge revenue en arrière d'une année : refus, aucun numéro réattribué")
    void clockGoingBackIsRefused() {
        numbers.next(local(2031, 1, 2, 9, 0));

        assertThatThrownBy(() -> numbers.next(local(2030, 12, 31, 18, 0)))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("2030")
                .extracting(e -> ((CustomServiceException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        // Le compteur n'a pas bougé : le prochain reçu de 2031 suit le précédent.
        assertThat(numbers.next(local(2031, 1, 2, 9, 5))).isEqualTo("RECU-2031-0002");
    }

    @Test
    @DisplayName("au-delà de 9999, le rang s'écrit en entier plutôt que tronqué")
    void rankAboveFourDigitsIsNotTruncated() {
        counterRepository.saveAndFlush(new ReceiptCounterEntity(ReceiptCounterEntity.SINGLETON_ID, 2030, 9999));

        assertThat(numbers.next(local(2030, 6, 1, 10, 0))).isEqualTo("RECU-2030-10000");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("hors transaction : refusé, un numéro n'existe pas sans son encaissement")
    void requiresTheEncashmentTransaction() {
        assertThatThrownBy(() -> numbers.next(local(2030, 1, 7, 10, 0)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
