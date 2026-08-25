package com.school.management.controller;

import com.school.management.dto.SessionSeriesDto;
import com.school.management.dto.serie.SeriesRenameRequest;
import com.school.management.mapper.SessionSeriesMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.service.SeriesNamingService;
import com.school.management.service.SessionSeriesService;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/series")
public class SessionSeriesController {

    private final SessionSeriesService sessionSeriesService;
    private final SessionSeriesMapper sessionSeriesMapper;
    private final SeriesNamingService seriesNamingService;

    @Autowired
    public SessionSeriesController(SessionSeriesService sessionSeriesService, SessionSeriesMapper sessionSeriesMapper, SeriesNamingService seriesNamingService) {
        this.sessionSeriesService = sessionSeriesService;
        this.sessionSeriesMapper = sessionSeriesMapper;
        this.seriesNamingService = seriesNamingService;
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

        // Le nom est calculé par le serveur, à partir de la date de début de la série
        // (c'est-à-dire la date de la première séance) : c'est la seule autorité sur le
        // format et sur le numéro de séquence. Le client ne compose plus le nom lui-même,
        // ce qui produisait un mois issu de la locale du navigateur et un décalage avec le
        // format du backend.
        if (!StringUtils.hasText(sessionSeriesEntity.getName()) && sessionSeriesEntity.getGroup() != null) {
            sessionSeriesEntity.setName(seriesNamingService.buildName(
                    sessionSeriesEntity.getGroup(), sessionSeriesEntity.getSerieTimeStart()));
        }

        SessionSeriesEntity createdSessionSeries = sessionSeriesService
                .createOrUpdateSessionSeries(sessionSeriesEntity);
        SessionSeriesDto createdSessionSeriesDto = sessionSeriesMapper.toDto(createdSessionSeries);
        return ResponseEntity.ok(createdSessionSeriesDto);
    }

    // PATCH /{id} générique retiré : il projetait une Map arbitraire du client sur l'entité
    // (ModelMapper), donc groupe, dates, nombre de séances et champs d'audit étaient tous
    // écrasables. Aucun écran ne l'utilisait ; le renommage passe par PATCH /{id}/name.

    /**
     * Renomme une série.
     *
     * <p>Point d'entrée dédié plutôt qu'un passage par {@link #patchSessionSeries} : ce dernier
     * projette une {@code Map} arbitraire sur l'entité, ce qui laisserait le client modifier
     * bien plus que le nom. Ici seul le nom est lu.</p>
     *
     * <p>Réservé à ADMIN par la règle générale sur les requêtes PATCH (voir SecurityConfig).</p>
     */
    @PatchMapping("/{id}/name")
    public ResponseEntity<SessionSeriesDto> renameSessionSeries(@PathVariable Long id,
            @RequestBody SeriesRenameRequest request) {
        SessionSeriesEntity renamed = sessionSeriesService.renameSeries(id, request.name());
        return ResponseEntity.ok(sessionSeriesMapper.toDto(renamed));
    }

    // get series by student id
}
