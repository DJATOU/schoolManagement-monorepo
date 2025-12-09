package com.school.management.service;

import com.school.management.dto.SessionSeriesDto;
import com.school.management.mapper.SessionSeriesMapper;
import com.school.management.repository.GroupRepository;
import com.school.management.shared.mapper.MappingContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionSeriesRepository;

import java.util.List;

@Service
public class SessionSeriesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionSeriesService.class);

    private final SessionSeriesRepository sessionSeriesRepository;
    private final SessionSeriesMapper sessionSeriesMapper;
    private final GroupRepository groupRepository;

    // MappingContext pour SessionSeriesMapper
    private MappingContext mappingContext;

    @Autowired
    public SessionSeriesService(SessionSeriesRepository sessionSeriesRepository, SessionSeriesMapper sessionSeriesMapper,
                              GroupRepository groupRepository) {
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.sessionSeriesMapper = sessionSeriesMapper;
        this.groupRepository = groupRepository;
    }

    /**
     * PHASE 1 REFACTORING: Initialise le MappingContext après injection des dépendances
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.of(
                null, null, null, null, null, null, null,
                groupRepository,
                sessionSeriesRepository,
                null, null
        );
        LOGGER.debug("MappingContext initialized for SessionSeriesService");
    }

    /**
     * Retourne le MappingContext pour utilisation par les controllers
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    public List<SessionSeriesEntity> getAllSessionSeries() {
        return sessionSeriesRepository.findAll();
    }

    public SessionSeriesEntity getSessionSeriesById(Long id) {
        return sessionSeriesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session series not found"));
    }

    public SessionSeriesEntity createOrUpdateSessionSeries(SessionSeriesEntity sessionSeries) {
        return sessionSeriesRepository.save(sessionSeries);
    }

    public List<SessionSeriesDto> getSeriesByGroupId(Long groupId) {
        return sessionSeriesRepository.findByGroupId(groupId).stream()
                .map(sessionSeriesMapper::toDto)
                .toList();
    }

    public List<SessionSeriesDto> getSessionSeriesByGroupId(Long groupId) {
        return sessionSeriesRepository.findByGroupId(groupId).stream()
                .map(sessionSeriesMapper::toDto)
                .toList();
    }


    // Ajoutez d'autres méthodes personnalisées ici selon les besoins
}
