package com.school.management.domain.valueobject;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Value Object représentant une plage de dates.
 * Immutable et valide à la construction.
 *
 * Garantit que la date de début est toujours avant ou égale à la date de fin.
 *
 * Usage:
 * <pre>
 * DateRange range = DateRange.of(startDate, endDate);
 * boolean contains = range.contains(someDate);
 * long days = range.getDurationInDays();
 * </pre>
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Embeddable
public class DateRange implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Date de début de la plage
     */
    private LocalDateTime startDate;

    /**
     * Date de fin de la plage
     */
    private LocalDateTime endDate;

    /**
     * Constructeur par défaut requis par JPA
     */
    protected DateRange() {
    }

    /**
     * Constructeur privé - utiliser les factory methods
     */
    private DateRange(LocalDateTime startDate, LocalDateTime endDate) {
        validateDates(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Crée un DateRange à partir de deux LocalDateTime
     *
     * @param startDate la date de début
     * @param endDate la date de fin
     * @return une instance DateRange
     * @throws IllegalArgumentException si les dates sont invalides
     */
    public static DateRange of(LocalDateTime startDate, LocalDateTime endDate) {
        return new DateRange(startDate, endDate);
    }

    /**
     * Crée un DateRange à partir de deux LocalDate (à minuit)
     *
     * @param startDate la date de début
     * @param endDate la date de fin
     * @return une instance DateRange
     * @throws IllegalArgumentException si les dates sont invalides
     */
    public static DateRange of(LocalDate startDate, LocalDate endDate) {
        return new DateRange(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        );
    }

    /**
     * Crée un DateRange pour une seule journée
     *
     * @param date la date
     * @return une instance DateRange du début à la fin de la journée
     */
    public static DateRange ofSingleDay(LocalDate date) {
        return new DateRange(
            date.atStartOfDay(),
            date.atTime(23, 59, 59)
        );
    }

    /**
     * Crée un DateRange pour le mois en cours
     *
     * @return une instance DateRange pour le mois actuel
     */
    public static DateRange ofCurrentMonth() {
        LocalDate now = LocalDate.now();
        LocalDate firstDay = now.withDayOfMonth(1);
        LocalDate lastDay = now.withDayOfMonth(now.lengthOfMonth());
        return DateRange.of(firstDay, lastDay);
    }

    /**
     * Crée un DateRange pour la semaine en cours
     *
     * @return une instance DateRange pour la semaine actuelle
     */
    public static DateRange ofCurrentWeek() {
        LocalDate now = LocalDate.now();
        LocalDate monday = now.minusDays(now.getDayOfWeek().getValue() - 1);
        LocalDate sunday = monday.plusDays(6);
        return DateRange.of(monday, sunday);
    }

    /**
     * Valide que les dates sont cohérentes
     *
     * @param startDate la date de début
     * @param endDate la date de fin
     * @throws IllegalArgumentException si les dates sont invalides
     */
    private void validateDates(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                String.format("Start date must be before or equal to end date. Start: %s, End: %s",
                    startDate, endDate));
        }
    }

    /**
     * Vérifie si une date est dans la plage
     *
     * @param date la date à vérifier
     * @return true si la date est dans la plage (bornes incluses)
     */
    public boolean contains(LocalDateTime date) {
        if (date == null) {
            return false;
        }
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * Vérifie si une date est dans la plage
     *
     * @param date la date à vérifier
     * @return true si la date est dans la plage
     */
    public boolean contains(LocalDate date) {
        if (date == null) {
            return false;
        }
        return contains(date.atStartOfDay());
    }

    /**
     * Vérifie si cette plage chevauche une autre
     *
     * @param other l'autre plage
     * @return true si les plages se chevauchent
     */
    public boolean overlaps(DateRange other) {
        if (other == null) {
            return false;
        }
        return !this.endDate.isBefore(other.startDate) && !this.startDate.isAfter(other.endDate);
    }

    /**
     * Retourne la durée en jours
     *
     * @return le nombre de jours entre start et end
     */
    public long getDurationInDays() {
        return ChronoUnit.DAYS.between(
            startDate.toLocalDate(),
            endDate.toLocalDate()) + 1; // +1 pour inclure les deux jours
    }

    /**
     * Retourne la durée en heures
     *
     * @return le nombre d'heures entre start et end
     */
    public long getDurationInHours() {
        return ChronoUnit.HOURS.between(startDate, endDate);
    }

    /**
     * Retourne la durée en minutes
     *
     * @return le nombre de minutes entre start et end
     */
    public long getDurationInMinutes() {
        return ChronoUnit.MINUTES.between(startDate, endDate);
    }

    /**
     * Vérifie si la plage est dans le passé
     *
     * @return true si la date de fin est dans le passé
     */
    public boolean isInPast() {
        return endDate.isBefore(LocalDateTime.now());
    }

    /**
     * Vérifie si la plage est dans le futur
     *
     * @return true si la date de début est dans le futur
     */
    public boolean isInFuture() {
        return startDate.isAfter(LocalDateTime.now());
    }

    /**
     * Vérifie si la plage inclut le moment actuel
     *
     * @return true si maintenant est dans la plage
     */
    public boolean isCurrentlyActive() {
        return contains(LocalDateTime.now());
    }

    /**
     * Retourne la date de début
     *
     * @return la date de début
     */
    public LocalDateTime getStartDate() {
        return startDate;
    }

    /**
     * Retourne la date de fin
     *
     * @return la date de fin
     */
    public LocalDateTime getEndDate() {
        return endDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DateRange dateRange = (DateRange) o;
        return Objects.equals(startDate, dateRange.startDate) &&
               Objects.equals(endDate, dateRange.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startDate, endDate);
    }

    @Override
    public String toString() {
        return String.format("DateRange[%s to %s]", startDate, endDate);
    }

    /**
     * Retourne une représentation formatée
     *
     * @return la plage formatée
     */
    public String toFormattedString() {
        return String.format("%s - %s (%d days)",
            startDate.toLocalDate(),
            endDate.toLocalDate(),
            getDurationInDays());
    }
}
