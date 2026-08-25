package com.school.management.service;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de propriété (jqwik) pour {@link SchoolYearLabels#deriveNextLabel(String)}.
 *
 * <p>Valide la logique pure de dérivation du libellé de l'année scolaire suivante : les deux
 * années sont incrémentées de un et la seconde reste toujours égale à la première plus un.</p>
 */
class SchoolYearLabelsPropertyTest {

    // ------------------------------------------------------------------
    // Property 5 — Next-year label derivation
    // ------------------------------------------------------------------

    // Feature: school-year, Property 5: For any School_Year_Label of the form YYYY-(YYYY+1), deriveNextLabel returns (YYYY+1)-(YYYY+2); both years incremented by one and the second always equals the first plus one.
    @Property(tries = 100)
    void property5_nextYearLabelDerivation(@ForAll("firstYears") int firstYear) {
        // Libellé courant bien formé : "YYYY-(YYYY+1)".
        String currentLabel = firstYear + "-" + (firstYear + 1);

        String nextLabel = SchoolYearLabels.deriveNextLabel(currentLabel);

        // Les deux années sont incrémentées de un : "(YYYY+1)-(YYYY+2)".
        String expected = (firstYear + 1) + "-" + (firstYear + 2);
        assertThat(nextLabel).isEqualTo(expected);

        // La seconde année reste toujours égale à la première plus un.
        String[] parts = nextLabel.split("-");
        int nextFirst = Integer.parseInt(parts[0]);
        int nextSecond = Integer.parseInt(parts[1]);
        assertThat(nextFirst).isEqualTo(firstYear + 1);
        assertThat(nextSecond).isEqualTo(nextFirst + 1);
    }

    // ------------------------------------------------------------------
    // Générateurs
    // ------------------------------------------------------------------

    /**
     * Premières années sur quatre chiffres, bornées de sorte que la seconde année reste sur
     * quatre chiffres (jusqu'à 9998 → "9998-9999").
     */
    @Provide
    Arbitrary<Integer> firstYears() {
        return Arbitraries.integers().between(1000, 9998);
    }
}
