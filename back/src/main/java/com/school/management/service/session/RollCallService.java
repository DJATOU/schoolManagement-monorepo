package com.school.management.service.session;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.dto.session.RollCallDTO;
import com.school.management.dto.session.RollCallDTO.ConcernedStudent;
import com.school.management.dto.session.RollCallDTO.NotConcernedStudent;
import com.school.management.dto.session.RollCallDTO.Window;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Feuille_Appel d'une Séance (exigences 6.2, 7.1, 7.2, décision D5).
 *
 * <p>Une Séance concerne un étudiant si l'une de ses inscriptions au groupe, <strong>active ou
 * close</strong>, a une fenêtre qui contient le jour de la Séance. La clôture ne retire donc pas
 * l'étudiant des Séances de sa fenêtre : parti le 30/11, il figure sur la feuille de la Séance du
 * 28/11 validée le 2/12. À l'inverse, une inscription active ne le fait pas attendre avant son
 * arrivée.</p>
 *
 * <p>La feuille n'est jamais complétée par le reste du groupe. L'écran le faisait quand elle
 * revenait vide : un étudiant arrivé plus tard était noté absent, donc retenu à tort comme ayant
 * manqué une séance de sa période. Une feuille vide renvoie à la place les étudiants non concernés
 * et leurs fenêtres, ce qui l'explique.</p>
 */
@Service
public class RollCallService {

    private static final Comparator<StudentEntity> BY_NAME = Comparator
            .comparing((StudentEntity s) -> s.getLastName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(StudentEntity::getFirstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(StudentEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final SessionRepository sessionRepository;
    private final StudentGroupRepository studentGroupRepository;

    public RollCallService(SessionRepository sessionRepository, StudentGroupRepository studentGroupRepository) {
        this.sessionRepository = sessionRepository;
        this.studentGroupRepository = studentGroupRepository;
    }

    /**
     * Feuille_Appel de la Séance.
     *
     * @throws ResourceNotFoundException si la Séance n'existe pas
     */
    @Transactional(readOnly = true)
    public RollCallDTO rollCall(Long sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Séance", sessionId));
        LocalDate day = EnrolmentWindow.dayOf(session.getSessionTimeStart());
        GroupEntity group = groupOf(session);
        if (group == null) {
            return new RollCallDTO(sessionId, null, day, List.of(), List.of());
        }

        List<ConcernedStudent> concerned = new ArrayList<>();
        List<NotConcernedStudent> notConcerned = new ArrayList<>();
        for (StudentEnrolments member : enrolmentsByStudent(group)) {
            StudentEntity student = member.student();
            List<StudentGroupEntity> enrolments = member.enrolments();
            Optional<EnrolmentWindow> covering = enrolments.stream()
                    .map(StudentGroupEntity::window)
                    .filter(window -> window.contains(day))
                    .findFirst();
            if (covering.isPresent()) {
                concerned.add(new ConcernedStudent(student.getId(), student.getFirstName(), student.getLastName(),
                        student.getGender(), covering.get().arrival(), covering.get().departure()));
            } else {
                notConcerned.add(new NotConcernedStudent(student.getId(), student.getFirstName(),
                        student.getLastName(), windows(enrolments)));
            }
        }
        return new RollCallDTO(sessionId, group.getId(), day, List.copyOf(concerned), List.copyOf(notConcerned));
    }

    /** Groupe de la Séance : le sien, sinon celui de sa Série. */
    private static GroupEntity groupOf(SessionEntity session) {
        if (session.getGroup() != null) {
            return session.getGroup();
        }
        return session.getSessionSeries() == null ? null : session.getSessionSeries().getGroup();
    }

    /**
     * Inscriptions du groupe, closes comprises, regroupées par étudiant et rangées par nom : un
     * étudiant revenu dans le groupe a plusieurs inscriptions, et ne doit figurer qu'une fois.
     */
    private List<StudentEnrolments> enrolmentsByStudent(GroupEntity group) {
        Map<Long, StudentEnrolments> byId = new LinkedHashMap<>();
        for (StudentGroupEntity enrolment : studentGroupRepository.findByGroupId(group.getId())) {
            StudentEntity student = enrolment.getStudent();
            if (student == null || student.getId() == null) {
                continue;
            }
            byId.computeIfAbsent(student.getId(), id -> new StudentEnrolments(student, new ArrayList<>()))
                    .enrolments().add(enrolment);
        }
        return byId.values().stream()
                .sorted(Comparator.comparing(StudentEnrolments::student, BY_NAME))
                .toList();
    }

    /** Un étudiant et ses inscriptions au groupe. */
    private record StudentEnrolments(StudentEntity student, List<StudentGroupEntity> enrolments) {
    }

    /** Fenêtres d'un étudiant dans le groupe, de la plus ancienne à la plus récente. */
    private static List<Window> windows(List<StudentGroupEntity> enrolments) {
        return enrolments.stream()
                .map(StudentGroupEntity::window)
                .sorted(Comparator.comparing(EnrolmentWindow::arrival, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(window -> new Window(window.arrival(), window.departure()))
                .toList();
    }
}
