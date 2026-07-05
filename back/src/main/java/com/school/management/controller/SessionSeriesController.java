package com.school.management.controller;

import com.school.management.dto.SessionSeriesDto;
import com.school.management.mapper.SessionSeriesMapper;
import com.school.management.service.PatchService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.service.SessionSeriesService;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/series")
public class SessionSeriesController {

    private final SessionSeriesService sessionSeriesService;
    private final SessionSeriesMapper sessionSeriesMapper;
    private final PatchService patchService;

    @Autowired
    public SessionSeriesController(SessionSeriesService sessionSeriesService, PatchService patchService,
            SessionSeriesMapper sessionSeriesMapper) {
        this.sessionSeriesService = sessionSeriesService;
        this.patchService = patchService;
        this.sessionSeriesMapper = sessionSeriesMapper;
    }

    @GetMapping
    public ResponseEntity<List<SessionSeriesEntity>> getAllSessionSeries() {
        return ResponseEntity.ok(sessionSeriesService.getAllSessionSeries());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionSeriesEntity> getSessionSeriesById(@PathVariable Long id) {
        return ResponseEntity.ok(sessionSeriesService.getSessionSeriesById(id));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<SessionSeriesDto>> getSessionSeriesByGroupId(@PathVariable Long groupId) {
        List<SessionSeriesDto> sessionSeries = sessionSeriesService.getSessionSeriesByGroupId(groupId);
        return ResponseEntity.ok(sessionSeries);
    }

    @PostMapping
    public ResponseEntity<SessionSeriesDto> createSessionSeries(@Valid @RequestBody SessionSeriesDto sessionSeriesDto) {
        if (sessionSeriesDto.getSerieTimeStart() == null) {
            sessionSeriesDto.setSerieTimeStart(new Date());
        }
        if (sessionSeriesDto.getSerieTimeEnd() == null) {
            Date startDate = sessionSeriesDto.getSerieTimeStart() != null ? sessionSeriesDto.getSerieTimeStart()
                    : new Date();
            sessionSeriesDto.setSerieTimeEnd(new Date(startDate.getTime() + 30L * 24 * 60 * 60 * 1000)); // Un mois plus
                                                                                                         // tard
        }
        // PHASE 1 REFACTORING: Utilise MappingContext au lieu de
        // ApplicationContextProvider
        SessionSeriesEntity sessionSeriesEntity = sessionSeriesMapper.toEntity(sessionSeriesDto,
                sessionSeriesService.getMappingContext());
        SessionSeriesEntity createdSessionSeries = sessionSeriesService
                .createOrUpdateSessionSeries(sessionSeriesEntity);
        SessionSeriesDto createdSessionSeriesDto = sessionSeriesMapper.toDto(createdSessionSeries);
        return ResponseEntity.ok(createdSessionSeriesDto);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SessionSeriesEntity> patchSessionSeries(@PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        SessionSeriesEntity sessionSeries = sessionSeriesService.getSessionSeriesById(id);
        patchService.applyPatch(sessionSeries, updates);
        return ResponseEntity.ok(sessionSeriesService.createOrUpdateSessionSeries(sessionSeries));
    }

    // get series by student id
}
