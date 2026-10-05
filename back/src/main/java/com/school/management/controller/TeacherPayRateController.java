package com.school.management.controller;

import com.school.management.dto.payroll.TeacherPayRateDTO;
import com.school.management.dto.payroll.TeacherPayRateRequest;
import com.school.management.service.payroll.TeacherPayRateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogue des taux de rémunération des enseignants (spec teacher-payroll, exigence 1). Réservé au
 * rôle ADMIN, lecture comprise ({@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/teacher-pay-rates")
public class TeacherPayRateController {

    private final TeacherPayRateService rateService;

    public TeacherPayRateController(TeacherPayRateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping
    public ResponseEntity<List<TeacherPayRateDTO>> list() {
        return ResponseEntity.ok(rateService.list());
    }

    @PostMapping
    public ResponseEntity<TeacherPayRateDTO> create(@RequestBody TeacherPayRateRequest request) {
        return new ResponseEntity<>(rateService.create(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeacherPayRateDTO> update(@PathVariable Long id, @RequestBody TeacherPayRateRequest request) {
        return ResponseEntity.ok(rateService.update(id, request));
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<TeacherPayRateDTO> disable(@PathVariable Long id) {
        return ResponseEntity.ok(rateService.disable(id));
    }
}
