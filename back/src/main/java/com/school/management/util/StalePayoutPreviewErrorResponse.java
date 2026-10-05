package com.school.management.util;

import com.school.management.dto.payroll.PayoutPreviewDTO;
import org.springframework.http.HttpStatus;

/**
 * Réponse 409 d'une paie confirmée sur un aperçu périmé : le nouvel aperçu, avec son jeton, que
 * l'écran présente à la place de l'ancien (spec teacher-payroll, exigence 4.3).
 *
 * @param status       409
 * @param message      cause, en français
 * @param errorCode    {@code STALE_PREVIEW}, le même code que les corrections
 * @param preview      le nouvel aperçu
 * @param previewToken son jeton
 */
public record StalePayoutPreviewErrorResponse(HttpStatus status, String message, String errorCode,
                                              PayoutPreviewDTO preview, String previewToken) {
}
