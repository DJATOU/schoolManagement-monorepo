package com.school.management.service;

import com.school.management.dto.AttendanceDTO;
import com.school.management.mapper.AttendanceMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.repository.*;
import com.school.management.shared.mapper.MappingContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class AttendanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final AttendanceMapper attendanceMapper;

    // PHASE 1 REFACTORING: Repositories pour MappingContext
    private final StudentRepository studentRepository;
    private final SessionRepository sessionRepository;
    private final SessionSeriesRepository sessionSeriesRepository;
    private final GroupRepository groupRepository;
    private final StudentGroupRepository studentGroupRepository;

    // MappingContext pour AttendanceMapper
    private MappingContext mappingContext;

    @Autowired
    public AttendanceService(AttendanceRepository attendanceRepository, AttendanceMapper attendanceMapper,
            StudentRepository studentRepository, SessionRepository sessionRepository,
            SessionSeriesRepository sessionSeriesRepository, GroupRepository groupRepository,
            StudentGroupRepository studentGroupRepository) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceMapper = attendanceMapper;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.groupRepository = groupRepository;
        this.studentGroupRepository = studentGroupRepository;
    }

    /**
     * PHASE 1 REFACTORING: Initialise le MappingContext après injection des
     * dépendances
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.of(
                null, // LevelRepository
                null, // TutorRepository
                null, // GroupTypeRepository
                null, // SubjectRepository
                null, // PricingRepository
                null, // TeacherRepository
                null, // SchoolYearRepository
                null, // RoomRepository
                groupRepository,
                sessionSeriesRepository,
                studentRepository,
                sessionRepository);
        LOGGER.debug("MappingContext initialized for AttendanceService");
    }

    /**
     * Retourne le MappingContext pour utilisation par les controllers
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    public List<AttendanceDTO> getAllAttendances() {
        List<AttendanceEntity> attendances = attendanceRepository.findAll();
        return attendances.stream()
                .map(attendanceMapper::attendanceToAttendanceDTO)
                .toList();
    }

    public AttendanceEntity getAttendanceById(Long id) {
        return attendanceRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new RuntimeException("Attendance not found")); // Customize this exception
    }

    public AttendanceEntity createAttendance(AttendanceEntity attendance) {
        return attendanceRepository.save(Objects.requireNonNull(attendance));
    }

    public AttendanceEntity updateAttendance(Long id) {
        AttendanceEntity existingAttendance = getAttendanceById(id);
        // Update properties of existingAttendance using values from updatedAttendance
        // ...
        return attendanceRepository.save(Objects.requireNonNull(existingAttendance));
    }

    public void deleteAttendance(Long id) {
        attendanceRepository.deleteById(Objects.requireNonNull(id));
    }

    // Save attendance
    public AttendanceEntity save(AttendanceEntity attendance) {
        return attendanceRepository.save(Objects.requireNonNull(attendance));
    }

    public List<AttendanceEntity> saveAll(List<AttendanceEntity> attendances) {
        for (AttendanceEntity attendance : attendances) {
            if (attendanceRepository.existsByStudentIdAndSessionIdAndActiveTrue(attendance.getStudent().getId(),
                    attendance.getSession().getId())) {
                throw new IllegalArgumentException("Attendance already exists for student ID "
                        + attendance.getStudent().getId() + " and session ID " + attendance.getSession().getId());
            }
            normalizeCatchUpFlag(attendance);
        }
        return attendanceRepository.saveAll(Objects.requireNonNull(attendances));
    }

    /**
     * Un rattrapage est une séance suivie dans un groupe dont l'étudiant n'est pas membre.
     * Si l'étudiant est inscrit (affectation active) au groupe de la séance, la présence ne
     * peut donc pas être un rattrapage, quoi qu'annonce le client.
     *
     * <p>Sans cette normalisation, un étudiant ajouté à la main sur une feuille de présence
     * de son propre groupe (cas d'une inscription postérieure au début de la séance) était
     * enregistré comme rattrapage. Toute la série basculait alors en mode rattrapage dans le
     * calcul de paiement, avec un coût total et un seuil de retard différents.</p>
     *
     * <p>On ne fait que retirer le drapeau à tort : promouvoir une présence en rattrapage
     * reste du ressort du flux rattrapage dédié.</p>
     */
    private void normalizeCatchUpFlag(AttendanceEntity attendance) {
        if (!Boolean.TRUE.equals(attendance.getIsCatchUp())
                || attendance.getGroup() == null
                || attendance.getStudent() == null) {
            return;
        }

        Long groupId = attendance.getGroup().getId();
        Long studentId = attendance.getStudent().getId();
        if (groupId == null || studentId == null) {
            return;
        }

        if (studentGroupRepository.existsByGroupIdAndStudentIdAndActiveTrue(groupId, studentId)) {
            LOGGER.info("Présence marquée rattrapage alors que l'étudiant {} est inscrit au groupe {} : "
                    + "drapeau isCatchUp remis à false.", studentId, groupId);
            attendance.setIsCatchUp(false);
        }
    }

    @Transactional
    public void deleteBySessionId(Long sessionId) {
        attendanceRepository.deleteBySessionId(sessionId);
    }

    public void deactivateBySessionId(Long sessionId) {
        List<AttendanceEntity> attendances = attendanceRepository.findBySessionId(sessionId);
        for (AttendanceEntity attendance : attendances) {
            attendance.setActive(false);
        }
        attendanceRepository.saveAll(Objects.requireNonNull(attendances));
    }

    public List<AttendanceDTO> getAttendanceBySessionId(Long sessionId) {
        List<AttendanceEntity> activeAttendances = attendanceRepository.findBySessionIdAndActiveTrue(sessionId);
        return activeAttendances.stream()
                .map(attendanceMapper::attendanceToAttendanceDTO)
                .toList();
    }

    public List<AttendanceDTO> getAttendanceByStudentAndSeries(Long studentId, Long sessionSeriesId) {
        List<AttendanceEntity> attendanceEntities = attendanceRepository
                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, sessionSeriesId);
        return attendanceEntities.stream()
                .map(attendanceMapper::attendanceToAttendanceDTO)
                .toList();
    }

    // Additional methods as needed...
}
