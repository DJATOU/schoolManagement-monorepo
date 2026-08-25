package com.school.management.dto.payment;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Un report restitué dans l'historique d'un étudiant : d'où vient l'argent, où il est allé, et
 * combien (exigence 6.2).
 *
 * <p>Un report répond à une question que {@code payment_detail} ne sait pas poser. Un
 * {@code payment_detail} dit « ce montant couvre telle séance » ; un report dit « ces 2 000 DA
 * versés sur la série de novembre ont payé celle de décembre ». Le second se ventile couramment
 * en plusieurs {@code payment_detail}, si bien qu'aucune ligne de ventilation ne peut restituer le
 * report en une seule ligne, ce que l'exigence 6.2 demande.</p>
 *
 * <p>Les deux séries sont nommées et non seulement identifiées : un historique qui affiche
 * « série 42 vers série 43 » n'est pas justifiable devant une famille.</p>
 *
 * @param id                identifiant de la trace de report
 * @param studentId         l'étudiant dont le versement a produit le report
 * @param amount            le montant reporté
 * @param sourceSeriesId    la série visée à la saisie, d'où provient le surplus
 * @param sourceSeriesName  le nom de cette série
 * @param targetSeriesId    la série effectivement créditée
 * @param targetSeriesName  le nom de cette série
 * @param targetPaymentId   la ligne de paiement créditée, qui rattache le report à l'historique
 * @param originPaymentDate la date du versement d'origine
 */
public record PaymentCarryOverDTO(
        Long id,
        Long studentId,
        BigDecimal amount,
        Long sourceSeriesId,
        String sourceSeriesName,
        Long targetSeriesId,
        String targetSeriesName,
        Long targetPaymentId,
        Date originPaymentDate) {
}
