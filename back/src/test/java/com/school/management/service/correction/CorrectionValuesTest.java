package com.school.management.service.correction;

import com.school.management.service.payment.PaymentCostResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valeurs de l'Aperçu : chacune refuse les états qui le feraient mentir.
 */
@DisplayName("Valeurs de l'Aperçu")
class CorrectionValuesTest {

    private static final AmountSnapshot ZERO = new AmountSnapshot(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);

    @Test
    @DisplayName("deux photographies des mêmes montants sont égales, quelle que soit l'échelle")
    void snapshotsCompareRegardlessOfScale() {
        AmountSnapshot a = new AmountSnapshot(new BigDecimal("4000"), new BigDecimal("2000.0"),
                new BigDecimal("3000.000"), new BigDecimal("1000"), false);
        AmountSnapshot b = new AmountSnapshot(new BigDecimal("4000.00"), new BigDecimal("2000"),
                new BigDecimal("3000"), new BigDecimal("1000.00"), false);

        assertThat(a).isEqualTo(b);
        assertThat(a.canonical()).isEqualTo("4000.00;2000.00;3000.00;1000.00;false");
    }

    @Test
    @DisplayName("le reste à payer d'une Série trop versée est nul, jamais négatif")
    void remainingIsNeverNegative() {
        AmountSnapshot overpaid = AmountSnapshot.of(new PaymentCostResolver.PaymentStatusResult(
                new BigDecimal("4000"), new BigDecimal("2000"), new BigDecimal("4500"), false, true));
        AmountSnapshot partial = AmountSnapshot.of(new PaymentCostResolver.PaymentStatusResult(
                new BigDecimal("4000"), new BigDecimal("2000"), new BigDecimal("1500"), true, false));

        assertThat(overpaid.remaining()).isEqualByComparingTo("0");
        assertThat(partial.remaining()).isEqualByComparingTo("2500");
        assertThat(partial.late()).isTrue();
    }

    @Test
    @DisplayName("un montant absent est refusé")
    void aMissingAmountIsRefused() {
        assertThatThrownBy(() -> new AmountSnapshot(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false))
                .isInstanceOf(NullPointerException.class).hasMessage("cost");
    }

    @Test
    @DisplayName("« aucun montant ne change » ne peut pas contredire la liste des Séries")
    void unchangedFlagMustMatchTheSeries() {
        SeriesAmountChange change = new SeriesAmountChange(1L, "A", 2L, "S", "G", ZERO, ZERO);

        assertThat(CorrectionPreview.of(List.of(), List.of()).amountsUnchanged()).isTrue();
        assertThat(CorrectionPreview.of(List.of(change), List.of()).amountsUnchanged()).isFalse();
        assertThatThrownBy(() -> new CorrectionPreview(List.of(), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorrectionPreview(List.of(change), List.of(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("l'Aperçu et l'exécution ne suivent pas les listes de l'appelant après coup")
    void listsAreCopied() {
        List<CorrectionEffect> effects = new ArrayList<>();
        Set<SeriesKey> touched = new HashSet<>();
        CorrectionExecution<String> execution = new CorrectionExecution<>("r", effects, touched);
        CorrectionPreview preview = CorrectionPreview.of(new ArrayList<>(), effects);

        effects.add(new CorrectionEffect(CorrectionEffectType.ALLOCATION_CREATED, "Imputation"));
        touched.add(new SeriesKey(1L, 2L));

        assertThat(execution.effects()).isEmpty();
        assertThat(execution.touched()).isEmpty();
        assertThat(preview.effects()).isEmpty();
    }

    @Test
    @DisplayName("un effet doit être décrit")
    void anEffectMustBeDescribed() {
        assertThatThrownBy(() -> new CorrectionEffect(CorrectionEffectType.ABSENCE_REMOVED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorrectionEffect(CorrectionEffectType.ABSENCE_REMOVED, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorrectionEffect(null, "Absence retirée"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("les Séries s'ordonnent par étudiant, puis par Série")
    void seriesKeysOrderByStudentThenSeries() {
        List<SeriesKey> keys = new ArrayList<>(List.of(
                new SeriesKey(2L, 1L), new SeriesKey(1L, 9L), new SeriesKey(1L, 3L)));

        keys.sort(null);

        assertThat(keys).containsExactly(new SeriesKey(1L, 3L), new SeriesKey(1L, 9L), new SeriesKey(2L, 1L));
        assertThatThrownBy(() -> new SeriesKey(null, 1L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SeriesKey(1L, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("une portée ne retient chaque Série et chaque groupe qu'une fois")
    void scopeDeduplicates() {
        CorrectionScope scope = CorrectionScope.empty()
                .group(1L, 5L).group(1L, 5L)
                .series(1L, 7L).series(1L, 7L);

        assertThat(scope.groups()).containsExactly(new CorrectionScope.StudentGroup(1L, 5L));
        assertThat(scope.series()).containsExactly(new SeriesKey(1L, 7L));
        assertThatThrownBy(() -> scope.group(null, 5L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> scope.group(1L, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("l'empreinte sépare les champs sans ambiguïté")
    void fingerprintFieldsAreUnambiguous() {
        CorrectionPreview withEffect = CorrectionPreview.of(List.of(),
                List.of(new CorrectionEffect(CorrectionEffectType.ALLOCATION_CREATED, "b")));

        // « a » suivi de l'effet « b » ne doit pas valoir la commande « a\nE|… » sans effet.
        String split = CorrectionRunner.fingerprint(command("a"), withEffect);
        String merged = CorrectionRunner.fingerprint(
                command("a\nE|ALLOCATION_CREATED|1:b"), CorrectionPreview.of(List.of(), List.of()));

        assertThat(split).isNotEqualTo(merged);
    }

    private static CorrectionCommand<Void> command(String fingerprint) {
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return fingerprint;
            }

            @Override
            public CorrectionScope scope() {
                return CorrectionScope.empty();
            }

            @Override
            public CorrectionExecution<Void> execute() {
                return new CorrectionExecution<>(null, List.of(), Set.of());
            }
        };
    }
}
