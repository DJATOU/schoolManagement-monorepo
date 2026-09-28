package com.school.management.dto.payment;

import com.school.management.dto.PaymentDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Réponse d'un encaissement réparti : ce qui a été imputé sur la série visée et ce qui a été
 * reporté sur les séries suivantes (exigence 6.3).
 *
 * <p>Un versement ne crédite plus forcément une seule série : au-delà du montant dû de la série
 * visée, le surplus est reporté. Le client doit donc recevoir le détail de la répartition pour
 * l'afficher avant validation et l'imprimer sur le reçu — la ligne {@code payment} porte, elle,
 * le <strong>cumul</strong> de la série et ne suffit pas à décrire le versement du jour.</p>
 *
 * @param studentId        l'étudiant qui a versé
 * @param groupId          le groupe concerné
 * @param seriesId         la série visée à la saisie, source des reports
 * @param amountReceived   le montant total reçu
 * @param amountAllocated  la part imputée sur la série visée, nulle si celle-ci était soldée
 * @param amountCarriedOver la somme des parts reportées, nulle en l'absence de report
 * @param carryOvers       le détail des reports, par identifiant de série croissant
 * @param payment          la ligne de paiement principale créditée, pour compatibilité avec les
 *                         écrans qui lisent la date d'encaissement faisant foi
 */
public record PaymentAllocationResultDTO(
        Long studentId,
        Long groupId,
        Long seriesId,
        BigDecimal amountReceived,
        BigDecimal amountAllocated,
        BigDecimal amountCarriedOver,
        List<CarriedOverAmountDTO> carryOvers,
        PaymentDTO payment) {

    /**
     * Une part reportée et sa série destinataire.
     *
     * @param seriesId   identifiant de la série créditée par report
     * @param seriesName nom de la série, pour que le reçu la nomme explicitement (exigence 7.2)
     * @param amount     montant reporté sur cette série
     */
    public record CarriedOverAmountDTO(Long seriesId, String seriesName, BigDecimal amount) {
    }
}
