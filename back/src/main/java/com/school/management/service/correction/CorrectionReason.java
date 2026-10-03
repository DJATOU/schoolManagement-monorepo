package com.school.management.service.correction;

import com.school.management.persistance.CorrectionReasonType;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;

/**
 * Motif d'une correction : un type choisi dans une liste, et un texte libre facultatif, obligatoire
 * pour {@link CorrectionReasonType#OTHER} (spec admin-corrections, exigences 11.1 et 11.2).
 *
 * <p>La validation est faite dans le constructeur canonique : aucun motif invalide ne peut exister,
 * quel que soit le chemin de création. Le texte est débarrassé de ses espaces de tête et de fin, et
 * un texte vide devient absent — sans quoi « Autre » suivi d'espaces passerait pour motivé.</p>
 *
 * @param type type de motif, obligatoire
 * @param text explication libre, au plus {@value #MAX_TEXT_LENGTH} caractères ; {@code null} si absente
 */
public record CorrectionReason(CorrectionReasonType type, String text) {

    public static final int MAX_TEXT_LENGTH = 500;

    public CorrectionReason {
        if (type == null) {
            throw new CustomServiceException(
                    "Motif obligatoire : choisissez la raison de la correction.", HttpStatus.BAD_REQUEST);
        }
        text = text == null || text.isBlank() ? null : text.strip();
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            throw new CustomServiceException(
                    "Motif trop long : " + text.length() + " caractères pour un maximum de "
                            + MAX_TEXT_LENGTH + ".", HttpStatus.BAD_REQUEST);
        }
        if (type == CorrectionReasonType.OTHER && text == null) {
            throw new CustomServiceException(
                    "Le motif « Autre » doit être expliqué en quelques mots.", HttpStatus.BAD_REQUEST);
        }
    }

    /** Motif sans texte libre. */
    public static CorrectionReason of(CorrectionReasonType type) {
        return new CorrectionReason(type, null);
    }

    /**
     * Motif tel qu'une requête le transmet : type en texte, casse indifférente. Un type absent est
     * refusé par le constructeur ; un type inconnu est une erreur de saisie, dite en français.
     *
     * @throws CustomServiceException 400
     */
    public static CorrectionReason parse(String type, String text) {
        if (type == null || type.isBlank()) {
            return new CorrectionReason(null, text);
        }
        try {
            return new CorrectionReason(CorrectionReasonType.valueOf(type.strip().toUpperCase(java.util.Locale.ROOT)), text);
        } catch (IllegalArgumentException unknown) {
            throw new CustomServiceException("Motif inconnu : « " + type + " ».", HttpStatus.BAD_REQUEST);
        }
    }
}
