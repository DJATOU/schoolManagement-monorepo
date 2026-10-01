package com.school.management.service.correction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Effet d'une correction sur les montants, en français (spec admin-corrections, exigence 12.3).
 */
@DisplayName("AmountEffectWriter — effet sur les montants")
class AmountEffectWriterTest {

    @Test
    @DisplayName("aucun changement : aucun effet")
    void noChangeNoEffect() {
        assertThat(AmountEffectWriter.describe(List.of())).isNull();
    }

    @Test
    @DisplayName("chaque montant qui change, et seulement eux, avec le statut")
    void everyChangedAmountAndOnlyThem() {
        AmountSnapshot before = new AmountSnapshot(money("6000"), money("4000"), money("6000"), money("0"), false);
        AmountSnapshot after = new AmountSnapshot(money("4000"), money("4000"), money("2000"), money("2000"), true);

        String effect = AmountEffectWriter.describe(List.of(change("Janvier", "Math 1ère A", before, after)));

        assertThat(effect).isEqualTo("Janvier (Math 1ère A) : coût 6 000,00 → 4 000,00 DA, "
                + "versé 6 000,00 → 2 000,00 DA, reste 0,00 → 2 000,00 DA, à jour → en retard");
    }

    @Test
    @DisplayName("le dû à ce jour et un retour à jour sont nommés")
    void dueSoFarAndBackOnTrack() {
        AmountSnapshot before = new AmountSnapshot(money("4000"), money("4000"), money("2000"), money("2000"), true);
        AmountSnapshot after = new AmountSnapshot(money("4000"), money("2000"), money("2000"), money("2000"), false);

        assertThat(AmountEffectWriter.describe(List.of(change("Janvier", "Math", before, after))))
                .isEqualTo("Janvier (Math) : dû à ce jour 4 000,00 → 2 000,00 DA, en retard → à jour");
    }

    @Test
    @DisplayName("plusieurs Séries, séparées ; une Série sans nom ni groupe reste désignée")
    void severalSeriesAndUnnamedSeries() {
        AmountSnapshot before = new AmountSnapshot(money("0"), money("0"), money("0"), money("0"), false);
        AmountSnapshot after = new AmountSnapshot(money("0"), money("0"), money("1234567.5"), money("0"), false);

        String effect = AmountEffectWriter.describe(List.of(
                change("Janvier", null, before, after),
                new SeriesAmountChange(1L, "A", 99L, null, null, before, after)));

        assertThat(effect).isEqualTo("Janvier : versé 0,00 → 1 234 567,50 DA ; Série 99 : versé 0,00 → 1 234 567,50 DA");
    }

    @Test
    @DisplayName("les milliers sont séparés par une espace ordinaire, imprimable en PDF")
    void thousandsUseAPlainSpace() {
        assertThat(AmountEffectWriter.money(new BigDecimal("20000"))).isEqualTo("20 000,00");
        assertThat(AmountEffectWriter.money(new BigDecimal("0.005"))).isEqualTo("0,01");
    }

    private static SeriesAmountChange change(String series, String group, AmountSnapshot before, AmountSnapshot after) {
        return new SeriesAmountChange(1L, "Amine", 10L, series, group, before, after);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
