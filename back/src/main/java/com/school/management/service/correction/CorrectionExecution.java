package com.school.management.service.correction;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ce qu'une correction rapporte de son exécution au {@link CorrectionRunner}.
 *
 * @param result  résultat métier, rendu à l'appelant à la confirmation seulement
 * @param effects effets autres que les écarts de montants, dans l'ordre où ils se sont produits
 * @param touched Séries dont la correction a écrit un montant ; chacune doit figurer dans la portée
 * @param <T>     type du résultat
 */
public record CorrectionExecution<T>(T result, List<CorrectionEffect> effects, Set<SeriesKey> touched) {

    public CorrectionExecution {
        effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
        touched = Set.copyOf(Objects.requireNonNull(touched, "touched"));
    }
}
