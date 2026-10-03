package com.school.management.service.correction;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Le Journal d'un élève : tout ce qui a été corrigé à son sujet, en clair, du plus récent au plus
 * ancien (spec admin-corrections, exigences 12.1 à 12.3 et 12.5 ; D8).
 *
 * <p>Il fusionne trois tables de traces, chacune lue par sa source :</p>
 * <ul>
 *   <li>{@code correction_audit} : Encaissements annulés ou remplacés, dates d'inscription,
 *       présences, rattrapages retirés — phrase et effet sur le dû écrits avec la correction ;</li>
 *   <li>{@code attendance_justification_audit} : justification d'une absence, sans effet sur le dû ;</li>
 *   <li>{@code catch_up_billing_audit} : séance manquée désignée et décision « déjà payée ».</li>
 * </ul>
 *
 * <p>{@code payment_detail_audit} n'est pas lue : plus rien ne l'écrit depuis A.6, qui a retiré la
 * correction d'une ligne de ventilation à l'unité, et l'installation part d'une base vide. Une
 * ventilation déplacée l'est par une correction, dont la Trace dit l'effet.</p>
 *
 * <p>Lecture seule, ouverte sur une année close (12.5) : aucun garde d'année ici.</p>
 */
@Service
public class CorrectionJournalService {

    /** Le plus récent d'abord ; à horodatage égal, la dernière écrite. */
    private static final Comparator<JournalSource.Item> MOST_RECENT_FIRST = Comparator
            .comparing((JournalSource.Item item) -> item.entry().performedAt())
            .thenComparingLong(JournalSource.Item::rank)
            .reversed();

    private final StudentRepository studentRepository;
    private final List<JournalSource> sources;

    CorrectionJournalService(StudentRepository studentRepository, List<JournalSource> sources) {
        this.studentRepository = studentRepository;
        this.sources = List.copyOf(sources);
    }

    /**
     * Journal d'un élève sur une période, bornes incluses.
     *
     * @param studentId l'élève
     * @param from      premier jour, ou nul pour « depuis le début »
     * @param to        dernier jour, ou nul pour « jusqu'à aujourd'hui »
     * @throws CustomServiceException 400 si la période finit avant de commencer ; 404 si l'élève
     *                                n'existe pas
     */
    @Transactional(readOnly = true)
    public CorrectionJournal journalOf(Long studentId, LocalDate from, LocalDate to) {
        Objects.requireNonNull(studentId, "studentId");
        if (from != null && to != null && from.isAfter(to)) {
            throw new CustomServiceException("Période invalide : du " + EnrolmentWindow.format(from) + " au "
                    + EnrolmentWindow.format(to) + ", la fin précède le début.", HttpStatus.BAD_REQUEST);
        }
        StudentEntity student = studentRepository.findById(studentId)
                .orElseThrow(() -> new CustomServiceException("Étudiant introuvable : " + studentId,
                        HttpStatus.NOT_FOUND));
        List<JournalEntry> entries = sources.stream()
                .flatMap(source -> source.itemsOf(studentId).stream())
                .filter(item -> within(item.entry().performedAt().toLocalDate(), from, to))
                .sorted(MOST_RECENT_FIRST)
                .map(JournalSource.Item::entry)
                .toList();
        return new CorrectionJournal(studentId, fullName(student), from, to, entries);
    }

    private static boolean within(LocalDate day, LocalDate from, LocalDate to) {
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }

    private static String fullName(StudentEntity student) {
        return Stream.of(student.getFirstName(), student.getLastName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }
}
