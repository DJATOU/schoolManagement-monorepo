package com.school.management.controller;

import com.school.management.dto.importcsv.ImportResultDTO;
import com.school.management.service.importcsv.CsvImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contrôleur (mince) d'import CSV : élèves, enseignants et groupes.
 *
 * <p>Chaque endpoint reçoit un fichier {@code multipart/form-data} (paramètre {@code file}) et
 * délègue au {@link CsvImportService}, qui renvoie un résumé (nombre importés + erreurs par
 * ligne). Conforme aux conventions : upload en multipart, contrôleur mince.</p>
 */
@RestController
@RequestMapping("/api/import")
public class CsvImportController {

    private final CsvImportService csvImportService;

    @Autowired
    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping(value = "/students", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importStudents(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importStudents(file));
    }

    @PostMapping(value = "/teachers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importTeachers(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importTeachers(file));
    }

    @PostMapping(value = "/groups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importGroups(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importGroups(file));
    }

    @PostMapping(value = "/levels", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importLevels(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importLevels(file));
    }

    @PostMapping(value = "/subjects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importSubjects(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importSubjects(file));
    }

    @PostMapping(value = "/rooms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importRooms(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importRooms(file));
    }

    @PostMapping(value = "/group-types", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importGroupTypes(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importGroupTypes(file));
    }

    @PostMapping(value = "/pricing", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importPricing(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(csvImportService.importPricing(file));
    }
}
