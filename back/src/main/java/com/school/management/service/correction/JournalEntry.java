package com.school.management.service.correction;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.school.management.persistance.CorrectionReasonType;

import java.time.LocalDateTime;

/**
 * Une entrée du Journal d'un élève, rédigée en français et sans identifiant technique (exigence
 * 12.2).
 *
 * @param performedAt  quand, à l'heure de l'école
 * @param category     ce qui a été corrigé
 * @param description  ce qui a changé, par exemple « Séance du 14/01/2030 (Math 1ère A) : Amine
 *                     Belkacem absent → présent »
 * @param amountEffect effet sur le dû, par exemple « Janvier (Math 1ère A) : dû à ce jour 0,00 →
 *                     2 000,00 DA » ; nul quand il n'y en a pas ou qu'il n'a pas été mesuré (12.3)
 * @param reasonType   Motif d'une correction ; nul pour une justification ou une décision de
 *                     rattrapage, qui n'en ont pas
 * @param reasonText   texte du Motif, ou commentaire saisi avec la justification ou la décision
 * @param performedBy  qui
 */
public record JournalEntry(@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
                           LocalDateTime performedAt, JournalCategory category, String description,
                           String amountEffect, CorrectionReasonType reasonType, String reasonText,
                           String performedBy) {
}
