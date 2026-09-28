package com.school.management.service;

import com.school.management.dto.DashboardStatsDTO;
import com.school.management.persistance.StudentStatus;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Calcule les statistiques agrégées du tableau de bord sur une période donnée.
 */
@Service
public class DashboardStatsService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final GroupRepository groupRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final CurrentSchoolYearService currentSchoolYearService;

    public DashboardStatsService(StudentRepository studentRepository,
                                 TeacherRepository teacherRepository,
                                 GroupRepository groupRepository,
                                 SessionRepository sessionRepository,
                                 AttendanceRepository attendanceRepository,
                                 StudentGroupRepository studentGroupRepository,
                                 CurrentSchoolYearService currentSchoolYearService) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.currentSchoolYearService = currentSchoolYearService;
    }

    /**
     * @param from date de début (incluse) ; si null, début de l'année courante
     * @param to   date de fin (incluse) ; si null, aujourd'hui
     */
    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(LocalDate from, LocalDate to) {
        return getStats(from, to, null);
    }

    /**
     * Statistiques du tableau de bord, éventuellement restreintes à une année scolaire.
     *
     * <p>Lorsqu'un {@code schoolYearId} est fourni, les statistiques rattachables à une année
     * (groupes, sessions, présences, rattrapages, élèves inscrits) sont filtrées sur cette
     * année via le groupe. Les statistiques globales par nature (enseignants, effectif total
     * d'élèves de l'établissement) ne dépendent pas de l'année.</p>
     *
     * @param from         date de début (incluse) ; si null, début de l'année courante
     * @param to           date de fin (incluse) ; si null, aujourd'hui
     * @param schoolYearId année scolaire à filtrer (optionnel)
     */
    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(LocalDate from, LocalDate to, Long schoolYearId) {
        LocalDate today = LocalDate.now();
        LocalDate effFrom = from != null ? from : today.withDayOfYear(1);
        LocalDate effTo = to != null ? to : today;

        LocalDateTime fromDt = effFrom.atStartOfDay();
        LocalDateTime toDt = effTo.atTime(LocalTime.MAX);
        Date fromDate = toDate(fromDt);
        Date toDate = toDate(toDt);

        boolean byYear = schoolYearId != null;

        // Année courante ? (même sémantique que la liste des élèves : année courante = tous les
        // actifs ; année passée = élèves inscrits dans un groupe de cette année).
        boolean isCurrentYear = byYear && currentSchoolYearService.findCurrent()
                .map(current -> schoolYearId.equals(current.getId()))
                .orElse(false);

        // Effectif d'élèves : pour l'année courante (ou sans filtre) = actifs globaux ;
        // pour une année passée = inscrits dans un groupe de cette année.
        long totalStudents = (!byYear || isCurrentYear)
                ? studentRepository.countByEnrollmentStatus(StudentStatus.ACTIVE)
                : studentGroupRepository.findDistinctStudentsBySchoolYearId(schoolYearId).size();

        // Groupes : de l'année si filtrage, sinon total. On exclut les groupes désactivés,
        // la suppression étant logique (active = false).
        long totalGroups = byYear
                ? groupRepository.countActiveBySchoolYearId(schoolYearId)
                : groupRepository.countActive();

        // Enseignants : même règle que l'effectif d'élèves. Pour l'année courante (ou sans
        // filtre) = effectif actuel de l'établissement ; pour une année passée = historique
        // figé, soit les enseignants ayant encadré un groupe de cette année. Sans cette
        // distinction, sélectionner N-1 affichait l'effectif d'aujourd'hui.
        long totalTeachers = (!byYear || isCurrentYear)
                ? teacherRepository.countActive()
                : teacherRepository.countDistinctBySchoolYearId(schoolYearId);

        // Sessions et présences : variantes filtrées par année si demandé.
        long sessionsValidated = byYear
                ? sessionRepository.countValidatedByYear(fromDate, toDate, schoolYearId)
                : sessionRepository.countValidated(fromDate, toDate);
        long sessionsScheduled = byYear
                ? sessionRepository.countScheduledByYear(fromDate, toDate, schoolYearId)
                : sessionRepository.countScheduled(fromDate, toDate);
        long sessionsDeactivated = byYear
                ? sessionRepository.countDeactivatedByYear(fromDate, toDate, schoolYearId)
                : sessionRepository.countDeactivated(fromDate, toDate);
        long catchUps = byYear
                ? attendanceRepository.countCatchUpByYear(fromDate, toDate, schoolYearId)
                : attendanceRepository.countCatchUp(fromDate, toDate);
        long present = byYear
                ? attendanceRepository.countPresentByYear(fromDate, toDate, schoolYearId)
                : attendanceRepository.countPresent(fromDate, toDate);
        long justified = byYear
                ? attendanceRepository.countJustifiedAbsencesByYear(fromDate, toDate, schoolYearId)
                : attendanceRepository.countJustifiedAbsences(fromDate, toDate);
        long unjustified = byYear
                ? attendanceRepository.countUnjustifiedAbsencesByYear(fromDate, toDate, schoolYearId)
                : attendanceRepository.countUnjustifiedAbsences(fromDate, toDate);

        return DashboardStatsDTO.builder()
                .from(effFrom)
                .to(effTo)
                // Étudiants
                .totalStudents(totalStudents)
                .newStudentsInPeriod(studentRepository.countByEnrollmentStatusAndDateCreationBetween(
                        StudentStatus.ACTIVE, fromDt, toDt))
                // Sortants = étudiants archivés (statut INACTIVE), c'est-à-dire ce que produit
                // la désactivation depuis l'interface.
                .leavingStudents(studentRepository.countByEnrollmentStatus(StudentStatus.INACTIVE))
                .maleStudents(studentRepository.countByEnrollmentStatusAndGenderIn(
                        StudentStatus.ACTIVE, java.util.List.of("male", "m", "homme", "h")))
                .femaleStudents(studentRepository.countByEnrollmentStatusAndGenderIn(
                        StudentStatus.ACTIVE, java.util.List.of("female", "f", "femme")))
                // Effectifs
                .totalTeachers(totalTeachers)
                .totalGroups(totalGroups)
                // Sessions
                .sessionsValidated(sessionsValidated)
                .sessionsScheduled(sessionsScheduled)
                .sessionsDeactivated(sessionsDeactivated)
                .catchUpSessions(catchUps)
                // Présences
                .presentCount(present)
                .justifiedAbsences(justified)
                .unjustifiedAbsences(unjustified)
                .build();
    }

    private Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }
}
