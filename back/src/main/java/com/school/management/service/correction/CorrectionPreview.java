package com.school.management.service.correction;

import java.util.List;
import java.util.Objects;

/**
 * Aperçu d'une correction : ce qu'elle change, mesuré en l'exécutant (spec admin-corrections,
 * exigences 4.1 à 4.4, D7).
 *
 * @param series           Séries dont un montant change, par étudiant puis par Série
 * @param effects          autres effets, dans l'ordre où ils se sont produits
 * @param amountsUnchanged vrai si aucun montant ne change : l'écran doit le dire en toutes lettres
 *                         (exigence 4.4), plutôt que de présenter un tableau vide
 */
public record CorrectionPreview(List<SeriesAmountChange> series, List<CorrectionEffect> effects,
                                boolean amountsUnchanged) {

    public CorrectionPreview {
        series = List.copyOf(Objects.requireNonNull(series, "series"));
        effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
        if (amountsUnchanged != series.isEmpty()) {
            throw new IllegalArgumentException("« Aucun montant ne change » doit refléter la liste des Séries.");
        }
    }

    /** Aperçu construit à partir des Séries changées et des effets. */
    public static CorrectionPreview of(List<SeriesAmountChange> series, List<CorrectionEffect> effects) {
        return new CorrectionPreview(series, effects, series.isEmpty());
    }
}
