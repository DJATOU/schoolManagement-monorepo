package com.school.management.service.correction;

import com.school.management.service.exception.CustomServiceException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Confirmation refusée : les données ont changé depuis l'Aperçu lu par l'administratrice
 * (exigence 4.3). Rien n'est écrit.
 *
 * <p>Porte le nouvel Aperçu et son jeton : l'écran le présente à la place de l'ancien, et
 * l'administratrice confirme de nouveau, en connaissance de cause.</p>
 */
@Getter
public class StalePreviewException extends CustomServiceException {

    private final transient CorrectionPreview preview;
    private final String previewToken;

    public StalePreviewException(CorrectionPreview preview, String previewToken) {
        super("Les données ont changé depuis l'aperçu : relisez le nouvel aperçu avant de confirmer.",
                HttpStatus.CONFLICT);
        this.preview = preview;
        this.previewToken = previewToken;
    }
}
