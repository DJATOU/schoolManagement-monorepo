package com.school.management.service.correction;

/**
 * Présence telle qu'une correction la laisse (spec admin-corrections, exigence 8).
 *
 * @param attendanceId la présence corrigée, ajoutée ou retirée
 * @param present      présent ({@code true}) ou absent
 * @param justified    absence justifiée ; toujours faux pour une présence
 * @param active       faux pour une présence retirée, qui reste en base désactivée (8.4)
 */
public record AttendanceCorrection(Long attendanceId, Long studentId, Long sessionId, boolean present,
                                   boolean justified, boolean active) {
}
