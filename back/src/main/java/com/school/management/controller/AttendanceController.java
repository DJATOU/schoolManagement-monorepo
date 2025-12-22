package com.school.management.controller;

import com.school.management.dto.AttendanceDTO;
import com.school.management.mapper.AttendanceMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.service.AttendanceService;
import com.school.management.service.PatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendances")
public class AttendanceController {

    private final AttendanceService attendanceService;

    private final PatchService patchService;

    private final AttendanceMapper attendanceMapper;

    @Autowired
    public AttendanceController(AttendanceService attendanceService, PatchService patchService,
            AttendanceMapper attendanceMapper) {
        this.attendanceService = attendanceService;
        this.patchService = patchService;
        this.attendanceMapper = attendanceMapper;
    }

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<List<AttendanceDTO>> getAllAttendances() {
        return ResponseEntity.ok(attendanceService.getAllAttendances());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttendanceEntity> getAttendanceById(@PathVariable Long id) {
        return ResponseEntity.ok(attendanceService.getAttendanceById(id));
    }

    @GetMapping("/student/{studentId}/series/{sessionSeriesId}")
    public ResponseEntity<List<AttendanceDTO>> getAttendancesByStudentAndSeries(@PathVariable Long studentId,
            @PathVariable Long sessionSeriesId) {
        List<AttendanceDTO> attendances = attendanceService.getAttendanceByStudentAndSeries(studentId, sessionSeriesId);
        return ResponseEntity.ok(attendances);
    }

    @PostMapping
    public ResponseEntity<AttendanceDTO> createAttendance(@RequestBody AttendanceDTO attendanceDto) {
        // PHASE 1 REFACTORING: Utilise MappingContext au lieu de
        // ApplicationContextProvider
        AttendanceEntity attendance = attendanceMapper.attendanceDTOToAttendance(attendanceDto,
                attendanceService.getMappingContext());
        AttendanceEntity savedAttendance = attendanceService.createAttendance(attendance);
        return new ResponseEntity<>(attendanceMapper.attendanceToAttendanceDTO(savedAttendance), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AttendanceEntity> updateAttendance(@PathVariable Long id) {
        return ResponseEntity.ok(attendanceService.updateAttendance(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AttendanceDTO> patchAttendance(@PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        AttendanceEntity attendance = attendanceService.getAttendanceById(id);
        patchService.applyPatch(attendance, updates);
        AttendanceEntity updatedAttendance = attendanceService.save(attendance);
        AttendanceDTO attendanceDTO = attendanceMapper.attendanceToAttendanceDTO(updatedAttendance);
        return ResponseEntity.ok(attendanceDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttendance(@PathVariable Long id) {
        attendanceService.deleteAttendance(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<?> submitAttendance(@RequestBody List<AttendanceDTO> attendanceDTOs) {
        // Log incoming request
        System.out.println("Received bulk attendance request with " + attendanceDTOs.size() + " items");

        if (attendanceDTOs.isEmpty()) {
            return ResponseEntity.badRequest().body("No attendance records provided");
        }

        try {
            // PHASE 1 REFACTORING: Utilise MappingContext au lieu de
            // ApplicationContextProvider
            List<AttendanceEntity> attendanceEntities = attendanceDTOs.stream()
                    .map(dto -> {
                        System.out.println(
                                "Mapping DTO: studentId=" + dto.getStudentId() + ", sessionId=" + dto.getSessionId());
                        return attendanceMapper.attendanceDTOToAttendance(dto, attendanceService.getMappingContext());
                    })
                    .toList();

            List<AttendanceEntity> savedAttendances = attendanceService.saveAll(attendanceEntities);
            List<AttendanceDTO> savedAttendanceDTOs = savedAttendances.stream()
                    .map(attendanceMapper::attendanceToAttendanceDTO)
                    .toList();

            return ResponseEntity.ok(savedAttendanceDTOs);
        } catch (Exception e) {
            System.err.println("Error in bulk attendance: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving attendance: " + e.getMessage());
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<AttendanceDTO>> getAttendancesBySessionId(@PathVariable Long sessionId) {
        List<AttendanceDTO> attendances = attendanceService.getAttendanceBySessionId(sessionId);
        return ResponseEntity.ok(attendances);
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> deleteAttendanceBySessionId(@PathVariable Long sessionId) {
        attendanceService.deleteBySessionId(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/deactivate/{sessionId}")
    public ResponseEntity<Void> deactivateAttendanceBySessionId(@PathVariable Long sessionId) {
        attendanceService.deactivateBySessionId(sessionId);
        return ResponseEntity.noContent().build();
    }

}
