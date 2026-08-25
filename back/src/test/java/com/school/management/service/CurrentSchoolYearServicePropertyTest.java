package com.school.management.service;

import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.SchoolYearRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test de propriété (jqwik) pour {@link CurrentSchoolYearService}.
 *
 * <p>Le repository est mocké : une liste en mémoire d'années scolaires sert de modèle.
 * {@code findByIsCurrentTrue()} renvoie l'année dont {@code isCurrent == true} (le cas
 * échéant) et {@code save(..)} met simplement à jour l'entité en place (les mutations de
 * drapeau ont lieu sur les objets partagés). On pilote ainsi des séquences aléatoires
 * d'appels à {@code makeCurrent} et on vérifie l'invariant après chaque opération.</p>
 */
class CurrentSchoolYearServicePropertyTest {

    // ------------------------------------------------------------------
    // Property 1 — Single current School Year invariant
    // ------------------------------------------------------------------

    // Feature: school-year, Property 1: For any sequence of School Year creations and set-current / year-end operations, at most one School Year is current at any point, and after any successful set-current/year-end the target is the only current year while the previous is no longer current.
    @Property(tries = 100)
    void property1_singleCurrentSchoolYearInvariant(
            @ForAll("operationSequences") OperationSequence sequence) {

        // Modèle en mémoire : les années scolaires initiales.
        List<SchoolYearEntity> store = sequence.years();

        SchoolYearRepository repository = mock(SchoolYearRepository.class);
        // findByIsCurrentTrue renvoie l'année dont isCurrent == true (au plus une attendue).
        when(repository.findByIsCurrentTrue()).thenAnswer(inv ->
                store.stream().filter(y -> Boolean.TRUE.equals(y.getIsCurrent())).findFirst());
        // save met à jour l'entité en place (les drapeaux sont déjà mutés sur l'objet partagé).
        when(repository.save(org.mockito.ArgumentMatchers.any(SchoolYearEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CurrentSchoolYearService service = new CurrentSchoolYearService(repository);

        // Invariant initial : au plus une année courante.
        assertThat(countCurrent(store)).isLessThanOrEqualTo(1);

        // Piloter la séquence d'opérations set-current.
        for (int targetIndex : sequence.targetIndexes()) {
            SchoolYearEntity target = store.get(targetIndex);

            service.makeCurrent(target);

            // Après toute opération réussie : la cible est la seule année courante.
            assertThat(target.getIsCurrent()).isTrue();
            assertThat(countCurrent(store)).isEqualTo(1);
            List<SchoolYearEntity> currents = store.stream()
                    .filter(y -> Boolean.TRUE.equals(y.getIsCurrent()))
                    .toList();
            assertThat(currents).containsExactly(target);
        }
    }

    private static long countCurrent(List<SchoolYearEntity> store) {
        return store.stream().filter(y -> Boolean.TRUE.equals(y.getIsCurrent())).count();
    }

    // ------------------------------------------------------------------
    // Générateurs
    // ------------------------------------------------------------------

    /** Un ensemble d'années scolaires et une séquence d'indices cibles pour makeCurrent. */
    record OperationSequence(List<SchoolYearEntity> years, List<Integer> targetIndexes) {}

    @Provide
    Arbitrary<OperationSequence> operationSequences() {
        // Entre 1 et 6 années scolaires (au moins une pour pouvoir cibler makeCurrent).
        Arbitrary<Integer> yearCount = Arbitraries.integers().between(1, 6);

        return yearCount.flatMap(count -> {
            // Au plus une année initialement courante : on choisit un index courant, ou -1 pour aucune.
            Arbitrary<Integer> initialCurrent = Arbitraries.integers().between(-1, count - 1);
            // Séquence de 0 à 15 opérations set-current, chaque cible étant un index valide.
            Arbitrary<List<Integer>> targets = Arbitraries.integers()
                    .between(0, count - 1)
                    .list().ofMinSize(0).ofMaxSize(15);

            return net.jqwik.api.Combinators.combine(initialCurrent, targets)
                    .as((currentIndex, targetIndexes) -> {
                        List<SchoolYearEntity> years = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            long day = 24L * 60L * 60L * 1000L;
                            Date start = new Date(i * 365L * day);
                            Date end = new Date((i * 365L + 300L) * day);
                            years.add(SchoolYearEntity.builder()
                                    .label("Y" + i)
                                    .startDate(start)
                                    .endDate(end)
                                    .isCurrent(i == currentIndex)
                                    .build());
                        }
                        return new OperationSequence(years, targetIndexes);
                    });
        });
    }
}
