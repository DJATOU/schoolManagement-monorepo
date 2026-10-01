package com.school.management.service.correction;

import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Un Encaissement tel qu'il aurait dû être saisi (spec admin-corrections, exigence 3.1).
 *
 * <p>L'état voulu complet, et non une liste de champs à changer : l'écran part des valeurs
 * actuelles et l'administratrice corrige ce qui est faux. Rien n'est implicite — un champ absent
 * ne veut pas dire « inchangé ».</p>
 *
 * @param amount         montant reçu, strictement positif
 * @param studentId      étudiant qui a versé
 * @param groupId        groupe
 * @param targetSeriesId Série visée
 * @param paymentMethod  mode de paiement, vide si aucun
 * @param notes          note, vide si aucune
 */
public record EncashmentChanges(BigDecimal amount, Long studentId, Long groupId, Long targetSeriesId,
                                String paymentMethod, String notes) {

    public EncashmentChanges {
        if (amount == null || studentId == null || groupId == null || targetSeriesId == null) {
            throw new CustomServiceException(
                    "Correction incomplète : montant, étudiant, groupe et série sont obligatoires.",
                    HttpStatus.BAD_REQUEST);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
