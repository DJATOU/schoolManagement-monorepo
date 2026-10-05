package com.school.management.util;

import com.school.management.service.correction.CorrectionPreview;
import org.springframework.http.HttpStatus;

/**
 * Corps du 409 d'un Aperçu périmé (spec admin-corrections, exigence 4.3) : le refus, et le nouvel
 * Aperçu à présenter à la place de l'ancien, avec le jeton qui permet de le confirmer.
 *
 * @param status       {@code CONFLICT}
 * @param message      motif du refus
 * @param errorCode    {@code STALE_PREVIEW}
 * @param preview      l'Aperçu recalculé sur les données actuelles
 * @param previewToken son jeton
 */
public record StalePreviewErrorResponse(HttpStatus status, String message, String errorCode,
                                        CorrectionPreview preview, String previewToken) {
}
