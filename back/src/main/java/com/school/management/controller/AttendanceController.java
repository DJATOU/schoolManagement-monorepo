package com.school.management.controller;

import com.school.management.dto.AttendanceDTO;
import com.school.management.dto.JustificationAuditDTO;
import com.school.management.dto.JustificationUpdateRequest;
import com.school.management.dto.JustificationUpdateResult;
import com.school.management.mapper.AttendanceMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.service.AttendanceJustificationService;
import com.school.management.service.AttendanceService;
import com.school.management.service.JustificationRetryTemplate;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendances")
public class AttendanceController {

    private final AttendanceService attendanceService;

    private final AttendanceMapper attendanceMapper;

    private final AttendanceJustificationService attendanceJustificationService;

    private final JustificationRetryTemplate justificationRetryTemplate;

    @Autowired
    public AttendanceController(AttendanceService attendanceService,
                                AttendanceMapper attendanceMapper,
                                AttendanceJustificationService attendanceJustificationService,
                                JustificationRetryTemplate justificationRetryTemplate) {
        this.attendanceService = attendanceService;
        this.attendanceMapper = attendanceMapper;
        this.attendanceJustificationService = attendanceJustificationService;
        this.justificationRetryTemplate = justificationRetryTemplate;
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

    // PUT /{id} retiré : il n'acceptait aucun corps de requête et rechargeait puis ré-enregistrait
    // la présence sans rien modifier. Un appel qui réussit sans effet induit l'appelant en erreur —
    // il croit avoir enregistré une correction qui n'a jamais eu lieu.
    //
    // PATCH /{id} générique retiré également : il projetait une Map arbitraire du client sur
    // l'entité (ModelMapper), donc n'importe quel champ d'une présence était écrasable. La
    // désactivation passe par PATCH /deactivate/{sessionId}, et la justification par le point
    // d'entrée dédié ci-dessous, dont le corps est fermé à deux champs.

    /**
     * Modifie la justification d'une absence (exigences 4.1, 4.2).
     *
     * <p>Réservé à ADMIN par la règle PATCH de {@code SecurityConfig} : aucune annotation de
     * sécurité n'est posée ici, pour ne pas dupliquer la règle en deux endroits susceptibles de
     * diverger.</p>
     *
     * <p>Le rejeu sur échec passager est porté par {@link JustificationRetryTemplate} et non par le
     * service : une méthode transactionnelle qui se rappelle elle-même ne réessaierait rien.</p>
     */
    @PatchMapping("/{id}/justification")
    public ResponseEntity<JustificationUpdateResult> updateJustification(
            @PathVariable Long id,
            @Valid @RequestBody JustificationUpdateRequest request) {
        return ResponseEntity.ok(justificationRetryTemplate.updateJustification(
                id, request.justified(), request.comment()));
    }

    /**
     * Piste d'audit de la justification d'une absence (exigence 5.7).
     *
     * <p>Ouverte aux deux rôles par la règle GET existante, et c'est voulu : un consultant doit
     * pouvoir constater qui a modifié quoi sans pouvoir le modifier lui-même.</p>
     */
    @GetMapping("/{id}/justification-audit")
    public ResponseEntity<List<JustificationAuditDTO>> justificationAudit(@PathVariable Long id) {
        return ResponseEntity.ok(attendanceJustificationService.auditTrail(id));
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
