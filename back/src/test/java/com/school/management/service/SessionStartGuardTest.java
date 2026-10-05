package com.school.management.service;

import com.school.management.persistance.SessionEntity;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Une séance ne se valide pas avant son heure de début")
class SessionStartGuardTest {

    private static final Instant NOW = Instant.parse("2026-10-12T16:30:00Z");
    private final SessionStartGuard guard = new SessionStartGuard(Clock.fixed(NOW, ZoneId.of("UTC")));

    private static SessionEntity startingAt(Instant start) {
        return SessionEntity.builder().sessionTimeStart(start == null ? null : Date.from(start)).build();
    }

    @Test
    @DisplayName("une seconde avant le début : refusée, 409, jour et heure nommés")
    void beforeStartIsRefused() {
        Instant start = NOW.plusSeconds(1);
        SessionEntity session = startingAt(start);

        assertThat(guard.notStarted(session)).isTrue();
        String expectedStart = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm")
                .format(start.atZone(ZoneId.systemDefault()));
        assertThatThrownBy(() -> guard.assertStarted(session))
                .isInstanceOf(CustomServiceException.class)
                .hasMessage("La séance du " + expectedStart + " n'a pas encore eu lieu : elle ne peut être "
                        + "validée qu'à partir de son heure de début.")
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("à l'heure de début pile, ou après : acceptée")
    void fromStartIsAccepted() {
        assertThatCode(() -> guard.assertStarted(startingAt(NOW))).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertStarted(startingAt(NOW.minus(Duration.ofDays(3)))))
                .doesNotThrowAnyException();
        assertThat(guard.notStarted(startingAt(NOW))).isFalse();
    }

    @Test
    @DisplayName("sans heure de début, ou sans séance : rien à borner, acceptée")
    void unknownStartIsNotRefused() {
        assertThatCode(() -> guard.assertStarted(startingAt(null))).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertStarted(null)).doesNotThrowAnyException();
    }
}
