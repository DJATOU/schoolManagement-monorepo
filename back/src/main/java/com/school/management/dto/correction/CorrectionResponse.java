package com.school.management.dto.correction;

import com.school.management.service.correction.CorrectionOutcome;
import com.school.management.service.correction.CorrectionPreview;

/**
 * Réponse d'une correction, en Aperçu comme en confirmation (spec admin-corrections, exigence 4).
 *
 * @param preview      ce que la correction change : Séries avant / après, autres effets
 * @param previewToken jeton à renvoyer pour confirmer exactement cet Aperçu
 * @param result       résultat de la correction confirmée ; {@code null} en Aperçu, où rien n'est
 *                     écrit
 * @param <T>          type du résultat
 */
public record CorrectionResponse<T>(CorrectionPreview preview, String previewToken, T result) {

    public static <T> CorrectionResponse<T> of(CorrectionOutcome<T> outcome) {
        return new CorrectionResponse<>(outcome.preview(), outcome.previewToken(), outcome.result());
    }
}
