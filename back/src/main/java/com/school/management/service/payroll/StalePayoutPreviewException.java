package com.school.management.service.payroll;

import com.school.management.dto.payroll.PayoutPreviewDTO;
import com.school.management.service.exception.CustomServiceException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Confirmation de paie refusée : l'encaissé, le taux ou les paies de la série ont changé depuis
 * l'aperçu (spec teacher-payroll, exigence 4.3). Rien n'est écrit, aucun numéro n'est consommé.
 *
 * <p>Porte le nouvel aperçu, que l'écran présente à la place de l'ancien : l'administratrice
 * confirme de nouveau, en connaissance de cause.</p>
 */
@Getter
public class StalePayoutPreviewException extends CustomServiceException {

    private final transient PayoutPreviewDTO preview;

    public StalePayoutPreviewException(PayoutPreviewDTO preview) {
        super("Les montants ont changé depuis l'aperçu : relisez le nouveau calcul avant de confirmer.",
                HttpStatus.CONFLICT);
        this.preview = preview;
    }
}
