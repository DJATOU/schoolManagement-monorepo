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

    // MappingContext pour AttendanceMapper
    private MappingContext mappingContext;

    @Autowired
    public AttendanceService(AttendanceRepository attendanceRepository, AttendanceMapper attendanceMapper,
                           StudentRepository studentRepository, SessionRepository sessionRepository,
                           SessionSeriesRepository sessionSeriesRepository, GroupRepository groupRepository) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceMapper = attendanceMapper;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.groupRepository = groupRepository;
    }

    /**
     * PHASE 1 REFACTORING: Initialise le MappingContext après injection des dépendances
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
                null, // RoomRepository
                groupRepository,
                sessionSeriesRepository,
                studentRepository,
                sessionRepository
        );
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
        return attendanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attendance not found")); // Customize this exception
    }

    public AttendanceEntity createAttendance(AttendanceEntity attendance) {
        return attendanceRepository.save(attendance);
    }

    public AttendanceEntity updateAttendance(Long id) {
        AttendanceEntity existingAttendance = getAttendanceById(id);
        // Update properties of existingAttendance using values from updatedAttendance
        // ...
        return attendanceRepository.save(existingAttendance);
    }

    public void deleteAttendance(Long id) {
        attendanceRepository.deleteById(id);
    }

    //Save attendance
    public AttendanceEntity save(AttendanceEntity attendance) {
        return attendanceRepository.save(attendance);
    }

    public List<AttendanceEntity> saveAll(List<AttendanceEntity> attendances) {
        for (AttendanceEntity attendance : attendances) {
            if (attendanceRepository.existsByStudentIdAndSessionIdAndActiveTrue(attendance.getStudent().getId(), attendance.getSession().getId())) {
                throw new IllegalArgumentException("Attendance already exists for student ID " + attendance.getStudent().getId() + " and session ID " + attendance.getSession().getId());
            }
        }
        return attendanceRepository.saveAll(attendances);
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
        attendanceRepository.saveAll(attendances);
    }

    public List<AttendanceDTO> getAttendanceBySessionId(Long sessionId) {
        List<AttendanceEntity> activeAttendances = attendanceRepository.findBySessionIdAndActiveTrue(sessionId);
        return activeAttendances.stream()
                .map(attendanceMapper::attendanceToAttendanceDTO)
                .toList();
    }

    public List<AttendanceDTO> getAttendanceByStudentAndSeries(Long studentId, Long sessionSeriesId) {
        List<AttendanceEntity> attendanceEntities = attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, sessionSeriesId);
        return attendanceEntities.stream()
                .map(attendanceMapper::attendanceToAttendanceDTO)
                .toList();
    }

    // Additional methods as needed...
}
