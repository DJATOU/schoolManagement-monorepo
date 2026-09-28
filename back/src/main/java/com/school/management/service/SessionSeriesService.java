package com.school.management.service;

import com.school.management.dto.SessionSeriesDto;
import com.school.management.mapper.SessionSeriesMapper;
import com.school.management.repository.GroupRepository;
import com.school.management.shared.mapper.MappingContext;
import com.school.management.service.exception.CustomServiceException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionSeriesRepository;

import java.util.List;
import java.util.Objects;

@Service
public class SessionSeriesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionSeriesService.class);

    private final SessionSeriesRepository sessionSeriesRepository;
    private final SessionSeriesMapper sessionSeriesMapper;
    private final GroupRepository groupRepository;
    private final ReadOnlyYearGuard readOnlyYearGuard;

    // MappingContext pour SessionSeriesMapper
    private MappingContext mappingContext;

    @Autowired
    public SessionSeriesService(SessionSeriesRepository sessionSeriesRepository,
            SessionSeriesMapper sessionSeriesMapper,
            GroupRepository groupRepository,
            ReadOnlyYearGuard readOnlyYearGuard) {
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.sessionSeriesMapper = sessionSeriesMapper;
        this.groupRepository = groupRepository;
        this.readOnlyYearGuard = readOnlyYearGuard;
    }

    /**
     * PHASE 1 REFACTORING: Initialise le MappingContext après injection des
     * dépendances
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.of(
                null, null, null, null, null, null, null, null,
                groupRepository,
                sessionSeriesRepository,
                null, null);
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
        return sessionSeriesRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new RuntimeException("Session series not found"));
    }

    public SessionSeriesEntity createOrUpdateSessionSeries(SessionSeriesEntity sessionSeries) {
        Objects.requireNonNull(sessionSeries);
        // Refuse la création/modification d'une série rattachée à une année passée (Exigence 9.2).
        readOnlyYearGuard.assertSeriesMutable(sessionSeries);
        return sessionSeriesRepository.save(sessionSeries);
    }

    /** Longueur maximale d'un nom de série, alignée sur la colonne « name » en base. */
    private static final int NAME_MAX_LENGTH = 255;

    /**
     * Renomme une série.
     *
     * <p>Le nom généré à la création suit le format {@code "{groupe} - {MM}-{yyyy}-{NNN}"}
     * produit par {@link SeriesNamingService}. Un renommage manuel le remplace définitivement :
     * le nom n'est jamais recalculé ensuite, la génération n'intervenant qu'à la création et
     * seulement si aucun nom n'est fourni. Un nom personnalisé survit donc aux modifications
     * ultérieures de la série.</p>
     *
     * <p>La modification d'une série d'une année passée reste refusée, l'historique étant en
     * lecture seule (Exigence 9.2) : le contrôle est appliqué par
     * {@link #createOrUpdateSessionSeries(SessionSeriesEntity)}.</p>
     *
     * <p>La validation est explicite et non déclarative : les annotations
     * {@code jakarta.validation} ne sont pas appliquées dans ce module, faute de provider
     * Jakarta sur le classpath.</p>
     *
     * @param id      identifiant de la série à renommer
     * @param newName le nouveau nom ; les espaces de bord sont retirés
     * @return la série renommée
     * @throws CustomServiceException (HTTP 400) si le nom est vide ou trop long
     */
    @Transactional
    public SessionSeriesEntity renameSeries(Long id, String newName) {
        SessionSeriesEntity series = getSessionSeriesById(Objects.requireNonNull(id));

        if (newName == null || newName.isBlank()) {
            throw new CustomServiceException("Le nom de la série ne peut pas être vide.",
                    HttpStatus.BAD_REQUEST);
        }
        String trimmed = newName.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new CustomServiceException(
                    "Le nom de la série ne peut pas dépasser " + NAME_MAX_LENGTH + " caractères.",
                    HttpStatus.BAD_REQUEST);
        }

        LOGGER.info("Renaming series {} from '{}' to '{}'", id, series.getName(), trimmed);
        series.setName(trimmed);
        return createOrUpdateSessionSeries(series);
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
