package com.school.management.service.correction;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Séries qu'une correction est susceptible de toucher, déclarées <strong>avant</strong> son
 * exécution (spec admin-corrections, D7, étape 1).
 *
 * <p>Les montants « avant » ne peuvent être photographiés qu'avant d'écrire : une Série touchée
 * mais absente de la portée n'aurait pas d'état antérieur, et l'Aperçu la tairait. Le
 * {@link CorrectionRunner} refuse donc toute correction qui touche une Série hors de sa portée.</p>
 *
 * <p>Un groupe se déclare entier quand la correction peut reporter de l'argent sur des Séries
 * qu'on ne connaît pas encore : le report va aux Séries suivantes du même groupe.</p>
 */
public final class CorrectionScope {

    /** Toutes les Séries d'un groupe, pour un étudiant. */
    record StudentGroup(Long studentId, Long groupId) {
        StudentGroup {
            Objects.requireNonNull(studentId, "studentId");
            Objects.requireNonNull(groupId, "groupId");
        }
    }

    private final Set<StudentGroup> groups = new LinkedHashSet<>();
    private final Set<SeriesKey> series = new LinkedHashSet<>();

    private CorrectionScope() {
    }

    /** Portée vide : une correction qui ne touche aucun montant. */
    public static CorrectionScope empty() {
        return new CorrectionScope();
    }

    /** Ajoute toutes les Séries du groupe, vues par cet étudiant. */
    public CorrectionScope group(Long studentId, Long groupId) {
        groups.add(new StudentGroup(studentId, groupId));
        return this;
    }

    /** Ajoute une Série, vue par cet étudiant. */
    public CorrectionScope series(Long studentId, Long seriesId) {
        series.add(new SeriesKey(studentId, seriesId));
        return this;
    }

    Set<StudentGroup> groups() {
        return Collections.unmodifiableSet(groups);
    }

    Set<SeriesKey> series() {
        return Collections.unmodifiableSet(series);
    }
}
