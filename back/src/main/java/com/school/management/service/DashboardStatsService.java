package com.school.management.service;

import com.school.management.dto.DashboardStatsDTO;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SessionRepository;
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

    public DashboardStatsService(StudentRepository studentRepository,
                                 TeacherRepository teacherRepository,
                                 GroupRepository groupRepository,
                                 SessionRepository sessionRepository,
                                 AttendanceRepository attendanceRepository) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
    }

    /**
     * @param from date de début (incluse) ; si null, début de l'année courante
     * @param to   date de fin (incluse) ; si null, aujourd'hui
     */
    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate effFrom = from != null ? from : today.withDayOfYear(1);
        LocalDate effTo = to != null ? to : today;

        LocalDateTime fromDt = effFrom.atStartOfDay();
        LocalDateTime toDt = effTo.atTime(LocalTime.MAX);
        Date fromDate = toDate(fromDt);
        Date toDate = toDate(toDt);

        return DashboardStatsDTO.builder()
                .from(effFrom)
                .to(effTo)
                // Étudiants
                .totalStudents(studentRepository.countByActiveTrue())
                .newStudentsInPeriod(studentRepository.countByActiveTrueAndDateCreationBetween(fromDt, toDt))
                .leavingStudents(studentRepository.countByActiveFalse())
                .maleStudents(studentRepository.countActiveByGenderIn(java.util.List.of("male", "m", "homme", "h")))
                .femaleStudents(studentRepository.countActiveByGenderIn(java.util.List.of("female", "f", "femme")))
                // Effectifs
                .totalTeachers(teacherRepository.count())
                .totalGroups(groupRepository.count())
                // Sessions
                .sessionsValidated(sessionRepository.countValidated(fromDate, toDate))
                .sessionsScheduled(sessionRepository.countScheduled(fromDate, toDate))
                .sessionsDeactivated(sessionRepository.countDeactivated(fromDate, toDate))
                .catchUpSessions(attendanceRepository.countCatchUp(fromDate, toDate))
                // Présences
                .presentCount(attendanceRepository.countPresent(fromDate, toDate))
                .justifiedAbsences(attendanceRepository.countJustifiedAbsences(fromDate, toDate))
                .unjustifiedAbsences(attendanceRepository.countUnjustifiedAbsences(fromDate, toDate))
                .build();
    }

    private Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }
}
