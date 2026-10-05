package com.school.management.service.correction;

import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionDomain;
import lombok.Builder;

import java.util.Map;
import java.util.Objects;

/**
 * Trace d'une correction telle que la correction la rédige (spec admin-corrections, D8).
 *
 * <p>L'effet sur les montants n'en fait pas partie : il n'est connu qu'une fois la correction
 * mesurée, et c'est le {@link CorrectionRunner} qui l'ajoute à l'écriture. L'auteur et l'heure non
 * plus : ils sont fixés par le serveur.</p>
 *
 * @param domain    domaine de la donnée corrigée
 * @param action    nature de la correction
 * @param entityId  identifiant de la donnée corrigée, dans son domaine
 * @param studentId étudiant concerné ; l'effet sur les montants est restreint à ses Séries
 * @param groupId   groupe concerné, facultatif
 * @param sessionId séance concernée, facultative
 * @param seriesId  Série concernée, facultative
 * @param oldValue  valeur avant, structurée ; {@code null} pour une création
 * @param newValue  valeur après, structurée ; {@code null} pour un retrait
 * @param summary   phrase en français, lisible sans la donnée : « Reçu RECU-2030-0042 de
 *                  3 000,00 DA annulé »
 * @param reason    motif choisi par l'administratrice
 */
@Builder
public record AuditDraft(CorrectionDomain domain, CorrectionAction action, Long entityId,
                         Long studentId, Long groupId, Long sessionId, Long seriesId,
                         Map<String, ?> oldValue, Map<String, ?> newValue,
                         String summary, CorrectionReason reason) {

    public AuditDraft {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(reason, "reason");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("Une trace doit dire en clair ce qui a été corrigé.");
        }
    }
}
