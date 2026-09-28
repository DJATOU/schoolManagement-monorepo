package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour {@link ReadOnlyYearGuard}.
 *
 * <p>Vérifie que, pour toute donnée (groupe, série, séance) dont l'année scolaire résolue via son
 * groupe n'est pas l'année courante, une mutation est rejetée, et qu'elle est autorisée lorsque
 * l'année résolue est l'année courante (Exigence 9.2). Le {@link CurrentSchoolYearService} est
 * simulé (Mockito) pour fixer l'année courante.</p>
 */
class ReadOnlyYearGuardPropertyTest {

    /** Identifiant fixe de l'année courante utilisée dans toutes les itérations. */
    private static final Long CURRENT_ID = 1L;

    // ------------------------------------------------------------------
    // Property 7 — Read-only past years reject mutations
    // ------------------------------------------------------------------

    // Feature: school-year, Property 7: For any Group/Session/payment/attendance whose resolved School Year is not current, create/update/delete is rejected; when current, the operation is permitted by the guard.
    @Property(tries = 100)
    void property7_readOnlyPastYearsRejectMutations(
            @ForAll("targetYearIds") Long targetYearId,
            @ForAll("resolutionPaths") ResolutionPath path) {

        // Année courante (identifiant fixe) renvoyée par le service simulé.
        CurrentSchoolYearService currentSchoolYearService = mock(CurrentSchoolYearService.class);
        SchoolYearEntity currentYear = SchoolYearEntity.builder()
                .label("2025-2026").isCurrent(true).build();
        currentYear.setId(CURRENT_ID);
        when(currentSchoolYearService.requireCurrent()).thenReturn(currentYear);

        ReadOnlyYearGuard guard = new ReadOnlyYearGuard(currentSchoolYearService);

        // Année cible du groupe : soit l'année courante (même id), soit une année passée (id
        // différent). Une donnée est modifiable si et seulement si son année résolue est courante.
        boolean isCurrent = CURRENT_ID.equals(targetYearId);
        SchoolYearEntity targetYear = SchoolYearEntity.builder()
                .label(isCurrent ? "2025-2026" : "2024-2025")
                .isCurrent(isCurrent)
                .build();
        targetYear.setId(targetYearId);

        GroupEntity group = GroupEntity.builder().schoolYear(targetYear).build();

        // Sélectionne l'entité à contrôler selon le chemin de résolution (groupe, série, séance).
        Runnable mutation = switch (path) {
            case GROUP -> () -> guard.assertGroupMutable(group);
            case SERIES -> {
                SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();
                yield () -> guard.assertSeriesMutable(series);
            }
            case SESSION_VIA_SERIES -> {
                SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();
                SessionEntity session = SessionEntity.builder().sessionSeries(series).build();
                yield () -> guard.assertSessionMutable(session);
            }
            case SESSION_VIA_GROUP -> {
                SessionEntity session = SessionEntity.builder().group(group).build();
                yield () -> guard.assertSessionMutable(session);
            }
        };

        if (isCurrent) {
            // Année courante : la mutation est permise par le garde.
            assertThatCode(mutation::run)
                    .as("mutation autorisée quand l'année résolue est l'année courante (chemin=%s)", path)
                    .doesNotThrowAnyException();
        } else {
            // Année passée : la mutation est rejetée.
            assertThatThrownBy(mutation::run)
                    .as("mutation rejetée quand l'année résolue n'est pas l'année courante (chemin=%s)", path)
                    .isInstanceOf(ReadOnlySchoolYearException.class);
        }
    }

    // ------------------------------------------------------------------
    // Générateurs
    // ------------------------------------------------------------------

    /**
     * Identifiants d'année cible : soit l'année courante ({@link #CURRENT_ID}), soit une année
     * passée dont l'identifiant est distinct de l'année courante. Couvre les deux branches.
     */
    @Provide
    Arbitrary<Long> targetYearIds() {
        Arbitrary<Long> current = Arbitraries.just(CURRENT_ID);
        Arbitrary<Long> past = Arbitraries.longs().between(2L, 1_000_000L);
        return Arbitraries.oneOf(current, past);
    }

    /** Chemins de résolution de l'année : groupe, série, séance via série, séance via groupe. */
    @Provide
    Arbitrary<ResolutionPath> resolutionPaths() {
        return Arbitraries.of(ResolutionPath.class);
    }

    /** Le chemin par lequel l'année scolaire de la donnée mutée est résolue. */
    private enum ResolutionPath {
        GROUP,
        SERIES,
        SESSION_VIA_SERIES,
        SESSION_VIA_GROUP
    }
}
