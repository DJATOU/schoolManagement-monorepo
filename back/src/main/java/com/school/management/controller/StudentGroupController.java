package com.school.management.controller;

import com.school.management.dto.EnrolmentDTO;
import com.school.management.dto.GroupDTO;
import com.school.management.dto.StudentGroupDTO;
import com.school.management.service.StudentGroupService;
import com.school.management.service.exception.GroupAlreadyAssociatedException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student-groups")
public class StudentGroupController {

    private static final Logger logger = LoggerFactory.getLogger(StudentGroupController.class);
    private static final String ERROR_MESSAGE = "error";
    private static final String MESSAGE = "message";

    private final StudentGroupService studentGroupService;

    @Autowired
    public StudentGroupController(StudentGroupService studentGroupService) {
        this.studentGroupService = studentGroupService;
    }

    @PostMapping("/{studentId}/addGroups")
    public ResponseEntity<Map<String, Object>> addGroupsToStudent(@PathVariable Long studentId,
                                                                  @RequestBody StudentGroupDTO studentGroupDto) {
        studentGroupDto.setStudentId(studentId);
        return handleGroupAssociation(() -> studentGroupService.manageStudentGroupAssociations(studentGroupDto));
    }

    @PostMapping("/{groupId}/addStudents")
    public ResponseEntity<Map<String, Object>> addStudentsToGroup(@PathVariable Long groupId,
                                                                  @RequestBody StudentGroupDTO studentGroupDto) {
        studentGroupDto.setGroupId(groupId);
        return handleGroupAssociation(() -> studentGroupService.manageStudentGroupAssociations(studentGroupDto));
    }

    // La Feuille_Appel d'une Séance est servie par GET /api/sessions/{id}/roll-call. Les deux
    // lectures qui la précédaient ici ont été retirées : « /{groupId}/studentsForSession »
    // ignorait les inscriptions closes et comparait des instants, et « /{groupId}/students »,
    // toutes inscriptions confondues sans fenêtre, ne servait qu'à compléter une feuille vide.


    /**
     * Groupes d'un étudiant, éventuellement restreints à une année scolaire.
     *
     * <p>Sans {@code schoolYearId}, tous les groupes sont renvoyés (le parcours en a besoin).
     * Avec, seuls ceux de l'année demandée le sont : la fiche étudiante affichait les groupes
     * de toutes les années, y compris ceux d'une année révolue introuvables dans la liste des
     * groupes, filtrée sur l'année sélectionnée (Exigence 10.4).</p>
     *
     * @param studentId    identifiant de l'étudiant
     * @param schoolYearId année scolaire à filtrer (optionnel)
     */
    @GetMapping("/{studentId}/groups")
    public ResponseEntity<List<GroupDTO>> getGroupsOfStudent(@PathVariable Long studentId,
            @RequestParam(required = false) Long schoolYearId) {
        List<GroupDTO> groups = studentGroupService.getGroupsOfStudent(studentId, schoolYearId);
        return ResponseEntity.ok(groups);
    }

    /**
     * Inscriptions d'un étudiant, ouvertes et closes, avec arrivée et départ : ce que la fiche élève
     * affiche et permet de corriger (spec admin-corrections, exigences 5 et 6).
     *
     * @param studentId    identifiant de l'étudiant
     * @param schoolYearId année scolaire à filtrer (optionnel)
     */
    @GetMapping("/{studentId}/enrolments")
    public ResponseEntity<List<EnrolmentDTO>> getEnrolmentsOfStudent(@PathVariable Long studentId,
            @RequestParam(required = false) Long schoolYearId) {
        return ResponseEntity.ok(studentGroupService.getEnrolmentsOfStudent(studentId, schoolYearId));
    }

    private ResponseEntity<Map<String, Object>> handleGroupAssociation(Runnable associationTask) {
        try {
            associationTask.run();
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE, "Operation completed successfully");
            return ResponseEntity.ok(response);
        } catch (GroupAlreadyAssociatedException e) {
            logger.error("GroupAlreadyAssociatedException: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE, "Some entities were already associated");
            response.put("alreadyAssociatedEntities", e.getGroupNames());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (EntityNotFoundException e) {
            logger.error("EntityNotFoundException: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put(ERROR_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        // Les autres refus (niveau, année close, arrivée hors de l'année, réinscription qui
        // recouvre un départ) remontent au gestionnaire global avec leur statut : les intercepter
        // ici les rendait tous en 500, comme une panne.
    }

    // « DELETE /{groupId}/students/{studentId} » a été retiré (C.8) : il clôturait l'inscription au
    // jour même, sans Motif ni Aperçu. Un départ passe par POST /api/enrolments/{id}/departure/…
    // (exigence 6.1).
}