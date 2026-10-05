package com.school.management.service.correction;

/**
 * Une Série dont un montant change, avant et après la correction (exigence 4.1).
 *
 * @param studentId   étudiant
 * @param studentName prénom et nom de l'étudiant
 * @param seriesId    Série
 * @param seriesName  nom de la Série
 * @param groupName   nom du groupe de la Série
 * @param before      montants avant la correction
 * @param after       montants après la correction
 */
public record SeriesAmountChange(Long studentId, String studentName, Long seriesId, String seriesName,
                                 String groupName, AmountSnapshot before, AmountSnapshot after) {
}
