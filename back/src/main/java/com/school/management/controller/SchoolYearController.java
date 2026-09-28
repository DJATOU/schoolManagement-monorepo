package com.school.management.controller;

import com.school.management.dto.SchoolYearDTO;
import com.school.management.mapper.SchoolYearMapper;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.service.CurrentSchoolYearService;
import com.school.management.service.SchoolYearService;
import com.school.management.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST (mince) pour la gestion des années scolaires.
 *
 * <p>Toute la logique métier est déléguée à {@link SchoolYearService} (création, liste,
 * récupération) et à {@link CurrentSchoolYearService} (année courante, désignation de
 * l'année courante). La conversion DTO ↔ Entity passe par {@link SchoolYearMapper} et le
 * {@link com.school.management.shared.mapper.MappingContext} exposé par le service
 * (convention du projet).</p>
 */
@RestController
@RequestMapping("/api/school-years")
public class SchoolYearController {

    private final SchoolYearService schoolYearService;
    private final CurrentSchoolYearService currentSchoolYearService;
    private final SchoolYearMapper schoolYearMapper;

    @Autowired
    public SchoolYearController(SchoolYearService schoolYearService,
                                CurrentSchoolYearService currentSchoolYearService,
                                SchoolYearMapper schoolYearMapper) {
        this.schoolYearService = schoolYearService;
        this.currentSchoolYearService = currentSchoolYearService;
        this.schoolYearMapper = schoolYearMapper;
    }

    /**
     * Crée une année scolaire (Exigences 1.5, 1.6). La validation (libellé, dates, unicité)
     * et la désignation de la première année comme courante sont assurées par le service.
     */
    @PostMapping
    public ResponseEntity<SchoolYearDTO> createSchoolYear(@Valid @RequestBody SchoolYearDTO schoolYearDto) {
        SchoolYearEntity entity = schoolYearMapper.schoolYearDTOToSchoolYear(
                schoolYearDto, schoolYearService.getMappingContext());
        SchoolYearEntity saved = schoolYearService.create(entity);
        return new ResponseEntity<>(schoolYearMapper.schoolYearToSchoolYearDTO(saved), HttpStatus.CREATED);
    }

    /**
     * Liste toutes les années scolaires, triées par date de début décroissante (Exigence 1.6).
     */
    @GetMapping
    public ResponseEntity<List<SchoolYearDTO>> getAllSchoolYears() {
        List<SchoolYearDTO> schoolYears = schoolYearService.findAll().stream()
                .map(schoolYearMapper::schoolYearToSchoolYearDTO)
                .toList();
        return ResponseEntity.ok(schoolYears);
    }

    /**
     * Récupère l'année scolaire courante (Exigences 2.5, 13.1).
     * En l'absence d'année courante, {@code requireCurrent()} lève une
     * {@code NoCurrentSchoolYearException} traduite en réponse claire (HTTP 404,
     * « Aucune année scolaire courante définie. ») par le {@code GlobalExceptionHandler}.
     */
    @GetMapping("/current")
    public ResponseEntity<SchoolYearDTO> getCurrentSchoolYear() {
        SchoolYearEntity current = currentSchoolYearService.requireCurrent();
        return ResponseEntity.ok(schoolYearMapper.schoolYearToSchoolYearDTO(current));
    }

    /**
     * Récupère une année scolaire par son identifiant (Exigence 1.5).
     */
    @GetMapping("/{id}")
    public ResponseEntity<SchoolYearDTO> getSchoolYearById(@PathVariable Long id) {
        SchoolYearEntity schoolYear = schoolYearService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolYear", id));
        return ResponseEntity.ok(schoolYearMapper.schoolYearToSchoolYearDTO(schoolYear));
    }

    /**
     * Désigne l'année scolaire comme courante (Exigences 2.1, 2.2, 2.4). Le service bascule
     * les drapeaux de façon atomique et rejette toute opération qui laisserait aucune année
     * courante.
     */
    @PatchMapping("/{id}/set-current")
    public ResponseEntity<SchoolYearDTO> setCurrentSchoolYear(@PathVariable Long id) {
        SchoolYearEntity target = schoolYearService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolYear", id));
        currentSchoolYearService.makeCurrent(target);
        return ResponseEntity.ok(schoolYearMapper.schoolYearToSchoolYearDTO(target));
    }
}
