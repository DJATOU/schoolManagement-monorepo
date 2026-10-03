package com.school.management.service.correction;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Journal d'un élève sur une période : ses entrées, de la plus récente à la plus ancienne
 * (exigence 12.1). L'en-tête suffit à l'imprimer (12.4).
 *
 * @param studentId   l'élève
 * @param studentName « Amine Belkacem »
 * @param from        premier jour inclus, nul pour « depuis le début »
 * @param to          dernier jour inclus, nul pour « jusqu'à aujourd'hui »
 * @param entries     les entrées, de la plus récente à la plus ancienne
 */
public record CorrectionJournal(Long studentId, String studentName,
                                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate from,
                                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate to,
                                List<JournalEntry> entries) {
}
