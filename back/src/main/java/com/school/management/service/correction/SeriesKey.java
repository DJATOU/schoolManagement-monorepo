package com.school.management.service.correction;

import java.util.Comparator;
import java.util.Objects;

/**
 * Une Série vue par un étudiant : l'unité dont l'Aperçu compare les montants (exigence 4.1).
 *
 * <p>Les montants d'une Série dépendent de l'étudiant (inscription, présences, réduction) : la
 * Série seule ne suffit pas à les désigner.</p>
 *
 * @param studentId étudiant
 * @param seriesId  Série
 */
public record SeriesKey(Long studentId, Long seriesId) implements Comparable<SeriesKey> {

    private static final Comparator<SeriesKey> ORDER =
            Comparator.comparing(SeriesKey::studentId).thenComparing(SeriesKey::seriesId);

    public SeriesKey {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(seriesId, "seriesId");
    }

    /** Par étudiant puis par Série : l'ordre de l'Aperçu, donc de son empreinte. */
    @Override
    public int compareTo(SeriesKey other) {
        return ORDER.compare(this, other);
    }
}
