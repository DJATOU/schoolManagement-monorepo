package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Garde transversal qui rejette les opérations de création, de modification ou de suppression
 * portant sur des données appartenant à une année scolaire qui n'est pas l'année courante
 * (Exigence 9.2).
 *
 * <p>Une donnée n'est modifiable que si son année scolaire résolue est l'année courante.
 * L'année d'une série, d'une séance, d'un paiement ou d'une présence est toujours résolue en
 * remontant jusqu'à son groupe ; aucune colonne d'année n'est lue sur ces enregistrements
 * (Exigence 3.4/3.5).</p>
 *
 * <p>Les opérations de lecture ne consultent jamais ce garde : les données de n'importe quelle
 * année restent entièrement consultables (Exigence 9.3).</p>
 */
@Service
public class ReadOnlyYearGuard {

    private final CurrentSchoolYearService currentSchoolYearService;

    @Autowired
    public ReadOnlyYearGuard(CurrentSchoolYearService currentSchoolYearService) {
        this.currentSchoolYearService = currentSchoolYearService;
    }

    /**
     * Vérifie que l'année scolaire fournie est modifiable, c'est-à-dire qu'elle est l'année
     * courante. Dans le cas contraire, lève une {@link ReadOnlySchoolYearException} (HTTP 409).
     *
     * @param year l'année scolaire à contrôler
     * @throws ReadOnlySchoolYearException si {@code year} n'est pas l'année courante
     */
    public void assertMutable(SchoolYearEntity year) {
        SchoolYearEntity current = currentSchoolYearService.requireCurrent();
        if (year == null || !current.getId().equals(year.getId())) {
            throw new ReadOnlySchoolYearException();
        }
    }

    /**
     * Vérifie que le groupe est modifiable en résolvant son année scolaire
     * ({@code group.schoolYear}).
     *
     * @param group le groupe à contrôler
     * @throws ReadOnlySchoolYearException si l'année du groupe n'est pas l'année courante
     */
    public void assertGroupMutable(GroupEntity group) {
        SchoolYearEntity year = Optional.ofNullable(group)
                .map(GroupEntity::getSchoolYear)
                .orElse(null);
        assertMutable(year);
    }

    /**
     * Vérifie que la série de séances est modifiable en résolvant l'année scolaire de son groupe
     * ({@code series.group.schoolYear}).
     *
     * @param series la série de séances à contrôler
     * @throws ReadOnlySchoolYearException si l'année résolue n'est pas l'année courante
     */
    public void assertSeriesMutable(SessionSeriesEntity series) {
        GroupEntity group = Optional.ofNullable(series)
                .map(SessionSeriesEntity::getGroup)
                .orElse(null);
        assertGroupMutable(group);
    }

    /**
     * Vérifie que la séance est modifiable en résolvant l'année scolaire de son groupe
     * ({@code session.series.group.schoolYear}, avec repli sur {@code session.group}).
     *
     * @param session la séance à contrôler
     * @throws ReadOnlySchoolYearException si l'année résolue n'est pas l'année courante
     */
    public void assertSessionMutable(SessionEntity session) {
        if (session == null) {
            assertMutable(null);
            return;
        }
        // L'année d'une séance est résolue via sa série (series.group.schoolYear).
        SessionSeriesEntity series = session.getSessionSeries();
        if (series != null) {
            assertSeriesMutable(series);
            return;
        }
        // Repli : certaines séances portent directement un groupe sans série.
        assertGroupMutable(session.getGroup());
    }
}
