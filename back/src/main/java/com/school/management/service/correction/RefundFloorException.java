package com.school.management.service.correction;

import com.school.management.service.exception.CustomServiceException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Correction refusée : le montant versé d'une Série passerait sous ce qui a déjà été remboursé
 * sur elle (spec admin-corrections, exigence 2.4). Rien n'est écrit.
 *
 * <p>Porte les remboursements en cause : l'écran les nomme, et l'administratrice sait quelle pièce
 * remise à la famille rend la correction impossible.</p>
 */
@Getter
public class RefundFloorException extends CustomServiceException {

    /**
     * Un remboursement qui borne la correction.
     *
     * @param refundNumber numéro de la pièce remise à la famille
     * @param refundDate   date du remboursement
     * @param amount       montant remboursé
     * @param seriesName   Série sur laquelle il porte
     */
    public record BlockingRefund(String refundNumber, Date refundDate, BigDecimal amount, String seriesName) {
    }

    private final transient List<BlockingRefund> blockingRefunds;

    public RefundFloorException(String message, List<BlockingRefund> blockingRefunds) {
        super(message, HttpStatus.CONFLICT);
        this.blockingRefunds = List.copyOf(blockingRefunds);
    }
}
