package com.school.management.service;

import com.school.management.persistance.LevelEntity;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de propriété (jqwik) pour {@link LevelSequenceService}.
 *
 * <p>Valide la logique pure d'ordonnancement des niveaux (next-level / highest-level) sans
 * base de données : les listes de niveaux sont générées puis passées directement au service.</p>
 */
class LevelSequenceServicePropertyTest {

    // ------------------------------------------------------------------
    // Property 2 — Level sequence next-level and highest-level
    // ------------------------------------------------------------------

    // Feature: school-year, Property 2: For any set of Levels with distinct levelSequence values, nextLevel(L) returns the Level with the smallest levelSequence strictly greater than L's when one exists and empty otherwise, and isHighest(L) is true iff nextLevel(L) is empty.
    @Property(tries = 100)
    void property2_levelSequenceNextAndHighest(@ForAll("distinctSequences") List<Integer> sequences) {
        LevelSequenceService service = new LevelSequenceService(null);

        // Construit une liste de niveaux avec les rangs (distincts) générés, dans un ordre
        // volontairement mélangé pour éprouver le tri interne du service.
        List<LevelEntity> levels = new ArrayList<>();
        for (int i = 0; i < sequences.size(); i++) {
            levels.add(level((long) i, sequences.get(i)));
        }
        Collections.shuffle(levels);

        int maxSequence = sequences.stream().mapToInt(Integer::intValue).max().orElseThrow();

        for (LevelEntity current : levels) {
            int currentSeq = current.getLevelSequence();

            // Attendu : le plus petit rang strictement supérieur au rang courant, s'il existe.
            OptionalInt expectedNextSeq = sequences.stream()
                    .filter(s -> s > currentSeq)
                    .mapToInt(Integer::intValue)
                    .min();

            Optional<LevelEntity> next = service.nextLevel(current, levels);

            if (expectedNextSeq.isPresent()) {
                assertThat(next)
                        .as("nextLevel doit exister pour le rang %d", currentSeq)
                        .isPresent();
                assertThat(next.get().getLevelSequence())
                        .as("nextLevel doit être le plus petit rang strictement supérieur")
                        .isEqualTo(expectedNextSeq.getAsInt());
                assertThat(service.isHighest(current, levels))
                        .as("isHighest doit être faux quand un niveau suivant existe")
                        .isFalse();
            } else {
                // Aucun rang supérieur : le niveau courant est le plus élevé.
                assertThat(next)
                        .as("nextLevel doit être vide pour le rang maximal %d", currentSeq)
                        .isEmpty();
                assertThat(currentSeq).isEqualTo(maxSequence);
                assertThat(service.isHighest(current, levels))
                        .as("isHighest doit être vrai pour le niveau le plus élevé")
                        .isTrue();
            }

            // Invariant : isHighest(L) vrai si et seulement si nextLevel(L) vide.
            assertThat(service.isHighest(current, levels)).isEqualTo(next.isEmpty());
        }
    }

    /**
     * Génère des ensembles de rangs {@code levelSequence} distincts et non nuls.
     *
     * <p>Inclut les systèmes à un seul niveau (taille 1) ainsi que des rangs pouvant être
     * négatifs, nuls ou positifs afin d'éprouver les niveaux les plus bas et les plus hauts.</p>
     */
    @Provide
    Arbitrary<List<Integer>> distinctSequences() {
        return Arbitraries.integers().between(-50, 50)
                .set().ofMinSize(1).ofMaxSize(10)
                .map(ArrayList::new);
    }

    private static LevelEntity level(Long id, int sequence) {
        return LevelEntity.builder()
                .id(id)
                .name("Niveau " + sequence)
                .levelSequence(sequence)
                .build();
    }
}
