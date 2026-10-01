package com.school.management.service.correction;

import com.school.management.dto.payment.EncashmentDTO;

/**
 * Issue d'une correction d'Encaissement (spec admin-corrections, exigences 3.2, 3.6, 3.7).
 *
 * @param original    l'Encaissement corrigé : annulé et relié à son remplacement, ou, si seuls le
 *                    mode et la note changeaient, l'Encaissement à jour
 * @param replacement l'Encaissement de remplacement, dont le reçu est à imprimer ; {@code null}
 *                    sans Remplacement
 */
public record EncashmentCorrection(EncashmentDTO original, EncashmentDTO replacement) {
}
