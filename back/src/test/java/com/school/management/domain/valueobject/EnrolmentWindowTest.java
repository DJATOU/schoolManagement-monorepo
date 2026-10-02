package com.school.management.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fenêtre_Inscription (D1, D5) : des jours calendaires, arrivée et départ compris.
 *
 * <p>Exemple qui sert tout au long : Amine arrive le 15 janvier 2030 et part le 31 janvier.</p>
 */
@DisplayName("EnrolmentWindow")
class EnrolmentWindowTest {

    private static final LocalDate ARRIVAL = LocalDate.of(2030, 1, 15);
    private static final LocalDate DEPARTURE = LocalDate.of(2030, 1, 31);

    private static Date at(LocalDate day, int hour, int minute) {
        return Date.from(day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static final EnrolmentWindow CLOSED = new EnrolmentWindow(ARRIVAL, DEPARTURE);
    private static final EnrolmentWindow OPEN = new EnrolmentWindow(ARRIVAL, null);

    @Nested
    @DisplayName("contient")
    class Contains {

        @Test
        @DisplayName("la séance du jour d'arrivée, quelle que soit l'heure — même avant celle de la saisie (5.4)")
        void arrivalDayAtAnyHour() {
            assertThat(CLOSED.contains(at(ARRIVAL, 0, 0))).isTrue();
            assertThat(CLOSED.contains(at(ARRIVAL, 8, 30))).isTrue();
            assertThat(CLOSED.contains(at(ARRIVAL, 23, 59))).isTrue();
        }

        @Test
        @DisplayName("pas la veille de l'arrivée, même à 23:59")
        void notTheDayBefore() {
            assertThat(CLOSED.contains(at(ARRIVAL.minusDays(1), 23, 59))).isFalse();
        }

        @Test
        @DisplayName("la séance du jour du départ, inclus jusqu'à 23:59")
        void departureDayIsIncluded() {
            assertThat(CLOSED.contains(at(DEPARTURE, 18, 0))).isTrue();
            assertThat(CLOSED.contains(at(DEPARTURE, 23, 59))).isTrue();
        }

        @Test
        @DisplayName("pas le lendemain du départ, dès 00:00")
        void notTheDayAfterDeparture() {
            assertThat(CLOSED.contains(at(DEPARTURE.plusDays(1), 0, 0))).isFalse();
        }

        @Test
        @DisplayName("fenêtre ouverte : tous les jours à partir de l'arrivée")
        void openWindowHasNoEnd() {
            assertThat(OPEN.contains(LocalDate.of(2040, 6, 1))).isTrue();
            assertThat(OPEN.contains(ARRIVAL.minusDays(1))).isFalse();
        }

        @Test
        @DisplayName("inscription non datée : aucun jour ; instant ou jour absent : non plus")
        void undatedOrMissing() {
            assertThat(new EnrolmentWindow(null, null).contains(ARRIVAL)).isFalse();
            assertThat(new EnrolmentWindow(null, DEPARTURE).contains(ARRIVAL)).isFalse();
            assertThat(CLOSED.contains((Date) null)).isFalse();
            assertThat(CLOSED.contains((LocalDate) null)).isFalse();
        }
    }

    @Test
    @DisplayName("lue depuis les colonnes : l'heure de stockage n'importe pas, le départ est facultatif")
    void ofColumns() {
        assertThat(EnrolmentWindow.of(at(ARRIVAL, 14, 20), at(DEPARTURE, 9, 0))).isEqualTo(CLOSED);
        assertThat(EnrolmentWindow.of(at(ARRIVAL, 0, 0), null)).isEqualTo(OPEN);
        assertThat(EnrolmentWindow.of(null, null)).isEqualTo(new EnrolmentWindow(null, null));
    }

    @Test
    @DisplayName("décrite en français pour les messages de refus")
    void describe() {
        assertThat(CLOSED.describe()).isEqualTo("du 15/01/2030 au 31/01/2030");
        assertThat(OPEN.describe()).isEqualTo("à partir du 15/01/2030");
        assertThat(new EnrolmentWindow(null, null).describe()).isEqualTo("non datée");
        assertThat(EnrolmentWindow.format(LocalDate.of(2030, 3, 5))).isEqualTo("05/03/2030");
    }

    @Nested
    @DisplayName("jour calendaire")
    class CalendarDay {

        @Test
        @DisplayName("d'un instant : lu dans le fuseau de la JVM")
        void ofAnInstant() {
            assertThat(EnrolmentWindow.dayOf(at(ARRIVAL, 23, 59))).isEqualTo(ARRIVAL);
            assertThat(EnrolmentWindow.dayOf(null)).isNull();
        }

        @Test
        @DisplayName("d'une colonne DATE : java.sql.Date porte déjà son jour, sans toInstant()")
        void ofASqlDate() {
            assertThat(EnrolmentWindow.dayOf(java.sql.Date.valueOf(ARRIVAL))).isEqualTo(ARRIVAL);
        }

        @Test
        @DisplayName("ramené à 00:00 : la forme stockée d'une date d'inscription")
        void startOfDay() {
            Date midnight = EnrolmentWindow.startOfDay(at(ARRIVAL, 17, 45));
            assertThat(LocalDateTime.ofInstant(midnight.toInstant(), ZoneId.systemDefault()))
                    .isEqualTo(ARRIVAL.atStartOfDay());
            assertThat(EnrolmentWindow.startOfDay(ARRIVAL)).isEqualTo(midnight);
            assertThat(EnrolmentWindow.startOfDay((Date) null)).isNull();
            assertThat(EnrolmentWindow.startOfDay((LocalDate) null)).isNull();
        }
    }
}
