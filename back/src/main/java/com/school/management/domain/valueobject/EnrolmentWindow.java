package com.school.management.domain.valueobject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Fenêtre_Inscription : les jours où une inscription concerne l'étudiant, de son arrivée à son
 * départ, <strong>bornes incluses</strong> (décision D5).
 *
 * <p>Une inscription porte des dates calendaires (décision D1). Le jour d'un instant est lu dans
 * le fuseau de la JVM, celui qui a écrit l'heure murale des colonnes {@code TIMESTAMP} : une
 * séance tenue le jour de l'arrivée concerne l'étudiant quelle que soit l'heure de la saisie
 * (exigence 5.4), et une séance tenue le jour du départ le concerne encore.</p>
 *
 * <p>Une fenêtre sans arrivée — inscription non datée — ne contient aucun jour : sans date, on ne
 * peut affirmer qu'une séance concernait l'étudiant. Une fenêtre sans départ est ouverte.</p>
 *
 * @param arrival   jour d'arrivée, {@code null} si l'inscription n'est pas datée
 * @param departure jour de départ inclus, {@code null} tant que l'inscription est ouverte
 */
public record EnrolmentWindow(LocalDate arrival, LocalDate departure) {

    private static final DateTimeFormatter FRENCH_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Fenêtre d'une inscription à partir de ses deux colonnes, l'une et l'autre facultatives. */
    public static EnrolmentWindow of(Date dateAssigned, Date dateLeft) {
        return new EnrolmentWindow(dayOf(dateAssigned), dayOf(dateLeft));
    }

    /** Vrai si le jour de cet instant appartient à la fenêtre. */
    public boolean contains(Date instant) {
        return contains(dayOf(instant));
    }

    /** Vrai si ce jour appartient à la fenêtre, arrivée et départ compris. */
    public boolean contains(LocalDate day) {
        return day != null
                && arrival != null
                && !day.isBefore(arrival)
                && (departure == null || !day.isAfter(departure));
    }

    /** La fenêtre en français, pour nommer une inscription dans un message de refus. */
    public String describe() {
        if (arrival == null) {
            return "non datée";
        }
        if (departure == null) {
            return "à partir du " + format(arrival);
        }
        return "du " + format(arrival) + " au " + format(departure);
    }

    /** Jour au format de l'école, {@code dd/MM/yyyy}. */
    public static String format(LocalDate day) {
        return FRENCH_DAY.format(day);
    }

    /**
     * Jour calendaire d'un instant, dans le fuseau de la JVM ; {@code null} pour {@code null}.
     *
     * <p>Une colonne {@code DATE} revient de JDBC en {@link java.sql.Date}, dont
     * {@code toInstant()} lève une exception : elle porte déjà un jour, lu tel quel.</p>
     */
    public static LocalDate dayOf(Date instant) {
        if (instant == null) {
            return null;
        }
        if (instant instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.ofInstant(instant.toInstant(), ZoneId.systemDefault());
    }

    /** 00:00 de ce jour dans le fuseau de la JVM ; {@code null} pour {@code null}. */
    public static Date startOfDay(LocalDate day) {
        return day == null ? null : Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** 00:00 du jour de cet instant : la forme sous laquelle une date d'inscription est stockée. */
    public static Date startOfDay(Date instant) {
        return startOfDay(dayOf(instant));
    }
}
