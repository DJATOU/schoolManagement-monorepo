package com.school.management.service;

import com.school.management.repository.RefundRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriété de la séquence des numéros de pièce (exigences 6.6, 6.7, 6.12).
 *
 * <p>Le cas que cent tirages trouvent : le passage d'année. Une séquence globale ferait repartir le
 * rang de 1 sans changer d'année, ou continuerait au-delà en changeant d'année ; l'un et l'autre
 * casse la numérotation comptable. Les années doivent être strictement indépendantes.</p>
 *
 * <p>Le magasin est simulé de façon fidèle : le mock du maximum par préfixe lit les numéros déjà
 * attribués. Sans cela, la propriété testerait un compteur figé et ne prouverait rien.</p>
 */
class RefundNumberPropertyTest {

    private static final Pattern NUMBER = Pattern.compile("^REMB-(\\d{4})-(\\d{4,})$");

    // Feature: absence-justification-and-refund-receipts, Property 9: For any set of issued refund
    // numbers, the numbers are pairwise distinct, never change after issuance, and their rank is
    // strictly increasing within a single calendar year.
    @Property(tries = 100)
    void property9_numbersAreDistinctAndRankIncreasesPerYear(
            @ForAll("years") List<Integer> annees,
            @ForAll @IntRange(min = 1, max = 5) int parAnnee) {

        List<String> attribues = new ArrayList<>();

        RefundRepository refundRepository = mock(RefundRepository.class);
        when(refundRepository.findMaxRankForPrefix(anyString())).thenAnswer(inv -> {
            String prefix = inv.getArgument(0);
            return attribues.stream()
                    .filter(n -> n.startsWith(prefix))
                    .map(n -> Integer.parseInt(n.substring(prefix.length())))
                    .max(Integer::compareTo)
                    .orElse(0);
        });

        RefundNumberService service = new RefundNumberService(refundRepository);

        for (int annee : annees) {
            int rangPrecedent = 0;
            for (String deja : attribues) {
                Matcher m = NUMBER.matcher(deja);
                if (m.matches() && Integer.parseInt(m.group(1)) == annee) {
                    rangPrecedent = Math.max(rangPrecedent, Integer.parseInt(m.group(2)));
                }
            }

            for (int i = 0; i < parAnnee; i++) {
                String numero = service.nextNumber(annee);

                Matcher m = NUMBER.matcher(numero);
                assertThat(m.matches())
                        .as("numéro %s hors du format REMB-AAAA-NNNN", numero).isTrue();
                assertThat(Integer.parseInt(m.group(1)))
                        .as("l'année du numéro %s ne suit pas l'année demandée %d", numero, annee)
                        .isEqualTo(annee);

                int rang = Integer.parseInt(m.group(2));
                assertThat(rang)
                        .as("rang %d non strictement croissant après %d pour l'année %d",
                                rang, rangPrecedent, annee)
                        .isGreaterThan(rangPrecedent);
                rangPrecedent = rang;

                attribues.add(numero);
            }
        }

        // Unicité globale : deux pièces comptables ne doivent jamais porter le même numéro.
        Set<String> distincts = new HashSet<>(attribues);
        assertThat(distincts)
                .as("numéros en doublon parmi %s", attribues)
                .hasSameSizeAs(attribues);

        // Le premier numéro d'une année vierge porte toujours le rang 1 (exigence 6.12).
        for (int annee : new HashSet<>(annees)) {
            String premier = "REMB-" + annee + "-0001";
            assertThat(attribues).as("l'année %d ne commence pas au rang 1", annee)
                    .contains(premier);
        }
    }

    /**
     * Suites d'années comportant volontairement des répétitions et des retours en arrière : le
     * changement d'année doit être indépendant de l'ordre dans lequel les années surviennent, un
     * remboursement pouvant être daté d'une année antérieure.
     */
    @Provide
    Arbitrary<List<Integer>> years() {
        return Arbitraries.of(2024, 2025, 2026, 2027)
                .list().ofMinSize(1).ofMaxSize(4);
    }
}
