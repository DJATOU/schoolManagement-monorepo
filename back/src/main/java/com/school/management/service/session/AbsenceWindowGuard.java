package com.school.management.service.session;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.dto.session.RejectedAbsenceDTO;
import com.school.management.dto.session.RejectedAbsenceDTO.Reason;
import com.school.management.dto.session.RollCallDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.StudentGroupRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aucune absence hors Fenêtre_Inscription (exigences 7.3 à 7.5, propriété P5).
 *
 * <p>Une absence n'a de sens que pour un étudiant attendu : noter absent un étudiant arrivé plus
 * tard, déjà parti, ou jamais inscrit au groupe, le présenterait comme ayant manqué une séance
 * qui ne le concernait pas. Une <strong>présence</strong>, elle, est toujours admise : hors
 * fenêtre, c'est une séance consommée (7.4), et les règles de rattrapage en décident (6.4).</p>
 *
 * <p>Est une absence toute ligne qui n'est pas une présence, {@code isPresent} nul compris : ce
 * qui n'est pas « présent » ne compte pas comme suivi, et doit donc être attendu.</p>
 *
 * <p>Le garde est appelé par tous les points d'entrée qui écrivent une absence, ou qui déplacent
 * une séance qui en porte : feuille de présence, présence unitaire, modification d'une séance.</p>
 */
@Service
public class AbsenceWindowGuard {

    private final StudentGroupRepository studentGroupRepository;

    public AbsenceWindowGuard(StudentGroupRepository studentGroupRepository) {
        this.studentGroupRepository = studentGroupRepository;
    }

    /** Refuse une feuille de présence qui contient une absence hors fenêtre, en nommant chaque ligne. */
    public void assertSubmittedAbsencesConcerned(Collection<AttendanceEntity> attendances) {
        List<RejectedAbsenceDTO> rejected = rejectedAbsences(attendances);
        if (!rejected.isEmpty()) {
            throw AbsenceOutsideWindowException.onSubmission(rejected);
        }
    }

    /** Refuse le déplacement d'une séance dont les absences actives sortiraient des fenêtres. */
    public void assertMovedSessionAbsencesConcerned(Collection<AttendanceEntity> activeAttendances) {
        List<RejectedAbsenceDTO> rejected = rejectedAbsences(activeAttendances);
        if (!rejected.isEmpty()) {
            throw AbsenceOutsideWindowException.onSessionMove(rejected);
        }
    }

    /**
     * Les absences de ces lignes que leur séance ne concerne pas, dans l'ordre des lignes.
     *
     * <p>Une ligne sans étudiant ou sans séance n'est pas jugée ici : on ne peut rien en dire, et
     * ce n'est pas une question de fenêtre.</p>
     */
    List<RejectedAbsenceDTO> rejectedAbsences(Collection<AttendanceEntity> attendances) {
        List<RejectedAbsenceDTO> rejected = new ArrayList<>();
        for (AttendanceEntity attendance : attendances) {
            StudentEntity student = attendance.getStudent();
            SessionEntity session = attendance.getSession();
            if (Boolean.TRUE.equals(attendance.getIsPresent()) || student == null || session == null) {
                continue;
            }
            GroupEntity group = RollCallService.groupOf(session);
            LocalDate day = EnrolmentWindow.dayOf(session.getSessionTimeStart());
            List<EnrolmentWindow> windows = windowsOf(student, group);
            if (windows.stream().noneMatch(window -> window.contains(day))) {
                rejected.add(rejection(student, session, group, day, windows));
            }
        }
        return rejected;
    }

    /** Fenêtres de l'étudiant dans le groupe, closes comprises, de la plus ancienne à la plus récente. */
    private List<EnrolmentWindow> windowsOf(StudentEntity student, GroupEntity group) {
        if (group == null || group.getId() == null || student.getId() == null) {
            return List.of();
        }
        return studentGroupRepository.findByGroupIdAndStudentId(group.getId(), student.getId()).stream()
                .map(StudentGroupEntity::window)
                .sorted(Comparator.comparing(EnrolmentWindow::arrival, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private static RejectedAbsenceDTO rejection(StudentEntity student, SessionEntity session, GroupEntity group,
                                                LocalDate day, List<EnrolmentWindow> windows) {
        String name = ((student.getFirstName() == null ? "" : student.getFirstName()) + " "
                + (student.getLastName() == null ? "" : student.getLastName())).trim();
        String head = "Absence de " + name + " " + (day == null ? "à une séance non datée" : "le " + EnrolmentWindow.format(day));
        Reason reason = windows.isEmpty() ? Reason.NOT_ENROLLED : Reason.OUTSIDE_WINDOW;
        String message;
        if (group == null) {
            message = head + " : la séance n'est rattachée à aucun groupe.";
        } else if (reason == Reason.NOT_ENROLLED) {
            message = head + " : aucune inscription au groupe « " + group.getName() + " ».";
        } else {
            message = head + " : hors de son inscription au groupe « " + group.getName() + " » ("
                    + windows.stream().map(EnrolmentWindow::describe).collect(Collectors.joining(" ; ")) + ").";
        }
        return new RejectedAbsenceDTO(student.getId(), student.getFirstName(), student.getLastName(),
                session.getId(), day, reason,
                windows.stream().map(window -> new RollCallDTO.Window(window.arrival(), window.departure())).toList(),
                message);
    }
}
