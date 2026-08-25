package com.school.management.service;

import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.service.exception.CustomServiceException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour {@link SchoolYearService}.
 *
 * <p>Chaque propriété correspond à une propriété de correction du design (school-year).
 * Le repository est mocké (Mockito) afin que les 100+ itérations restent rapides et
 * indépendantes d'une base de données.</p>
 */
class SchoolYearServicePropertyTest {

    // ------------------------------------------------------------------
    // Property 11 — School Year listing order
    // ------------------------------------------------------------------

    // Feature: school-year, Property 11: For any set of School Years, listing them returns a permutation of the input ordered by start date descending.
    @Property(tries = 100)
    void property11_schoolYearListingOrder(@ForAll("schoolYearSets") List<SchoolYearEntity> input) {
        SchoolYearRepository repository = mock(SchoolYearRepository.class);

        // findAll délègue à la requête ordonnée du repository : on stube le repository pour
        // renvoyer l'entrée triée par date de début décroissante (contrat de la requête dérivée).
        List<SchoolYearEntity> ordered = new ArrayList<>(input);
        ordered.sort(Comparator.comparing(SchoolYearEntity::getStartDate).reversed());
        when(repository.findAllByOrderByStartDateDesc()).thenReturn(ordered);

        SchoolYearService service = new SchoolYearService(repository);

        List<SchoolYearEntity> result = service.findAll();

        // 1) Le résultat est une permutation de l'entrée : même taille et mêmes éléments.
        assertThat(result).hasSameSizeAs(input);
        assertThat(result).containsExactlyInAnyOrderElementsOf(input);

        // 2) Le résultat est ordonné par date de début décroissante.
        List<Date> dates = result.stream().map(SchoolYearEntity::getStartDate).toList();
        assertThat(dates).isSortedAccordingTo(Comparator.reverseOrder());
    }

    // ------------------------------------------------------------------
    // Property 12 — Date range validation
    // ------------------------------------------------------------------

    // Feature: school-year, Property 12: For any pair of start and end dates, creation of a School Year succeeds only when start is strictly before end and is rejected otherwise.
    @Property(tries = 100)
    void property12_dateRangeValidation(@ForAll("datePairs") DatePair pair) {
        SchoolYearRepository repository = mock(SchoolYearRepository.class);
        // count() -> 1 : on évite le chemin "première année" (isCurrent) pour ne tester que les dates.
        when(repository.count()).thenReturn(1L);
        // Libellé toujours unique afin d'isoler la validation des dates.
        when(repository.findByLabel(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(SchoolYearEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SchoolYearService service = new SchoolYearService(repository);

        SchoolYearEntity sy = SchoolYearEntity.builder()
                .label("2025-2026")
                .startDate(pair.start())
                .endDate(pair.end())
                .build();

        boolean startStrictlyBeforeEnd = pair.start().before(pair.end());

        if (startStrictlyBeforeEnd) {
            SchoolYearEntity saved = service.create(sy);
            assertThat(saved.getStartDate()).isEqualTo(pair.start());
            assertThat(saved.getEndDate()).isEqualTo(pair.end());
        } else {
            // start == end ou start > end : la création doit être rejetée.
            assertThatThrownBy(() -> service.create(sy))
                    .isInstanceOf(CustomServiceException.class);
        }
    }

    // ------------------------------------------------------------------
    // Générateurs
    // ------------------------------------------------------------------

    /**
     * Ensembles d'années scolaires (0 à 20 éléments), incluant l'ensemble vide et des dates
     * de début potentiellement égales pour couvrir les cas limites du tri.
     */
    @Provide
    Arbitrary<List<SchoolYearEntity>> schoolYearSets() {
        // Dates de début tirées dans une petite plage pour provoquer des égalités.
        Arbitrary<SchoolYearEntity> oneYear = Arbitraries.longs()
                .between(0L, 30L)
                .map(days -> {
                    long millis = days * 24L * 60L * 60L * 1000L;
                    Date start = new Date(millis);
                    Date end = new Date(millis + 365L * 24L * 60L * 60L * 1000L);
                    return SchoolYearEntity.builder()
                            .startDate(start)
                            .endDate(end)
                            .build();
                });
        return oneYear.list().ofMinSize(0).ofMaxSize(20);
    }

    record DatePair(Date start, Date end) {}

    /**
     * Paires de dates couvrant start &lt; end, start == end et start &gt; end (dates égales
     * incluses pour tester la borne stricte).
     */
    @Provide
    Arbitrary<DatePair> datePairs() {
        Arbitrary<Long> startDays = Arbitraries.longs().between(0L, 100L);
        Arbitrary<Long> endDays = Arbitraries.longs().between(0L, 100L);
        return Combinators.combine(startDays, endDays).as((s, e) -> {
            long day = 24L * 60L * 60L * 1000L;
            return new DatePair(new Date(s * day), new Date(e * day));
        });
    }
}
