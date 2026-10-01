package com.school.management.service.correction;

/**
 * Issue d'une correction exécutée par le {@link CorrectionRunner}.
 *
 * @param mode         mode d'exécution
 * @param preview      ce que la correction change
 * @param previewToken empreinte de l'Aperçu, à renvoyer pour confirmer
 * @param result       résultat métier à la confirmation ; {@code null} en Aperçu, où rien n'a été
 *                     écrit et où toute entité produite a disparu avec la transaction
 * @param <T>          type du résultat
 */
public record CorrectionOutcome<T>(CorrectionMode mode, CorrectionPreview preview, String previewToken,
                                   T result) {
}
