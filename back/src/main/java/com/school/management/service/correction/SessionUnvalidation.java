package com.school.management.service.correction;

/**
 * Séance telle qu'une dévalidation la laisse (spec admin-corrections, exigence 10).
 *
 * @param sessionId    la séance, de nouveau à valider
 * @param removedLines lignes de présence désactivées avec elle
 */
public record SessionUnvalidation(Long sessionId, int removedLines) {
}
