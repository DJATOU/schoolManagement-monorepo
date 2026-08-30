package com.school.management.dto.catchup;

/**
 * Corps de la correction d'un rattrapage déjà résolu.
 *
 * <p>Les deux champs sont <strong>facultatifs et nullables</strong>, contrairement à la résolution :
 * ici {@code null} signifie « ne pas toucher à cette décision ». Corriger la seule séance manquée
 * sans réaffirmer la décision de facturation est un cas courant, et obliger à renvoyer les deux
 * ferait ressaisir une valeur inchangée — donc écrire une trace qui n'apprend rien.</p>
 *
 * <p>Une correction sans aucun champ renseigné est refusée : elle laisserait croire qu'une
 * modification a eu lieu.</p>
 *
 * @param missedSessionId nouvelle séance manquée, ou {@code null} pour la conserver
 * @param alreadyPaid     nouvelle décision de facturation, ou {@code null} pour la conserver
 * @param comment         motif de la correction, conservé dans la trace d'audit
 */
public record CorrectCatchUpRequestDTO(
        Long missedSessionId,
        Boolean alreadyPaid,
        String comment) {
}
