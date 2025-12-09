package com.school.management.service;

import com.school.management.dto.session.SessionDTO;
import com.school.management.dto.session.SessionSearchCriteriaDTO;
import com.school.management.mapper.SessionMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.RoomEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.*;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.util.CommonSpecifications;
import com.school.management.shared.mapper.MappingContext;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

@Service
public class SessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);
    private static final String SESSION_NOT_FOUND_MESSAGE = "Session not found with id: ";
    private static final String GROUPID = "groupId";
    private static final String ROOMID = "roomId";
    private static final String TEACHERID = "teacherId";

    private final SessionRepository sessionRepository;
    private final RoomRepository roomRepository;
    private final TeacherRepository teacherRepository;
    private final GroupRepository groupRepository;
    private final SessionSeriesRepository sessionSeriesRepository;
    private final PatchService patchService;
    private final SessionMapper sessionMapper;

    // MappingContext pour SessionMapper
    private MappingContext mappingContext;

    @Autowired
    public SessionService(SessionRepository sessionRepository, PatchService patchService, GroupRepository groupRepository,
                          SessionMapper sessionMapper, RoomRepository roomRepository, TeacherRepository teacherRepository,
                          SessionSeriesRepository sessionSeriesRepository) {
        this.sessionRepository = sessionRepository;
        this.patchService = patchService;
        this.groupRepository = groupRepository;
        this.sessionMapper = sessionMapper;
        this.roomRepository = roomRepository;
        this.teacherRepository = teacherRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
    }

    /**
     * PHASE 1 REFACTORING: Initialise le MappingContext après injection des dépendances
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.of(
                null, null, null, null, null,
                teacherRepository,
                roomRepository,
                groupRepository,
                sessionSeriesRepository,
                null,
                sessionRepository
        );
        LOGGER.debug("MappingContext initialized for SessionService");
    }

    /**
     * Retourne le MappingContext pour utilisation par les controllers
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    public List<SessionEntity> getAllSessions() {
        return sessionRepository.findAll();
    }

    public List<SessionEntity> getAllSessionsWithDetail() {
        return sessionRepository.findAllWithDetails();
    }

    public Optional<SessionEntity> getSessionById(Long id) {
        return sessionRepository.findById(id);
    }

    public SessionEntity createSession(SessionEntity session) {
        return sessionRepository.save(session);
    }

    public SessionEntity updateSession(Long sessionId, Map<String, Object> updates) {
        SessionEntity session = getSessionById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found with ID: " + sessionId));

        updateEntityRelations(session, updates);
        updateSessionTimes(session, updates);

        // Utilisation de ModelMapper pour les autres mises à jour simples
        patchService.applyPatch(session, updates);

        // Sauvegarder l'entité session mise à jour
        return sessionRepository.save(session);
    }

    private void updateEntityRelations(SessionEntity session, Map<String, Object> updates) {
        if (updates.containsKey(GROUPID)) {
            Long groupId = extractId(updates.get(GROUPID));
            GroupEntity group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new EntityNotFoundException("Group not found with ID: " + groupId));
            session.setGroup(group);
            updates.remove(GROUPID);
        }

        if (updates.containsKey(ROOMID)) {
            Long roomId = extractId(updates.get(ROOMID));
            RoomEntity room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new EntityNotFoundException("Room not found with ID: " + roomId));
            session.setRoom(room);
            updates.remove(ROOMID);
        }

        if (updates.containsKey(TEACHERID)) {
            Long teacherId = extractId(updates.get(TEACHERID));
            TeacherEntity teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> new EntityNotFoundException("Teacher not found with ID: " + teacherId));
            session.setTeacher(teacher);
            updates.remove(TEACHERID);
        }
    }

    private void updateSessionTimes(SessionEntity session, Map<String, Object> updates) {
        updateSessionTime("sessionTimeStart", updates, session::setSessionTimeStart);
        updateSessionTime("sessionTimeEnd", updates, session::setSessionTimeEnd);
    }

    private void updateSessionTime(String key, Map<String, Object> updates, Consumer<Date> setter) {
        if (updates.containsKey(key)) {
            Object timeObject = updates.get(key);
            if (timeObject instanceof Date date) {
                setter.accept(date);
            } else if (timeObject instanceof String dateString) {
                try {
                    Date parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX").parse(dateString);
                    setter.accept(parsedDate);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("Invalid date format for " + key, e);
                }
            }
            updates.remove(key);
        }
    }


    private Long extractId(Object idObj) {
        if (idObj == null) {
            return null;
        }
        try {
            return Long.valueOf(idObj.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ID value: " + idObj);
        }
    }

    public void deleteSession(Long id) {
        sessionRepository.deleteById(id);
    }

    public List<SessionEntity> findSessionsByCriteria(SessionSearchCriteriaDTO criteria) {
        Specification<SessionEntity> spec = Specification.where(null);

        spec = spec.and(CommonSpecifications.likeIfNotNull("title", criteria.getTitle()))
                .and(CommonSpecifications.equalsIfNotNull("sessionType", criteria.getSessionType()))
                .and(CommonSpecifications.greaterThanOrEqualToIfNotNull("sessionTimeStart", criteria.getStartDate()))
                .and(CommonSpecifications.lessThanOrEqualToIfNotNull("sessionTimeEnd", criteria.getEndDate()))
                .and(CommonSpecifications.equalsIfNotNull("teacher.id", criteria.getTeacherId()))
                .and(CommonSpecifications.equalsIfNotNull("group.id", criteria.getGroupId()))
                .and(CommonSpecifications.equalsIfNotNull("isFinished", criteria.getIsFinished()))
                .and(CommonSpecifications.equalsIfNotNull("room.id", criteria.getRoomId()));

        return sessionRepository.findAll(spec);
    }

    @Transactional
    public SessionEntity markSessionAsFinished(Long sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomServiceException(SESSION_NOT_FOUND_MESSAGE + sessionId));
        session.setIsFinished(true);
        return sessionRepository.save(session);
    }

    public SessionEntity markSessionAsUnfinished(Long sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomServiceException(SESSION_NOT_FOUND_MESSAGE + sessionId));
        session.setIsFinished(false);
        return sessionRepository.save(session);
    }

    public List<SessionEntity> getSessionsBySeriesId(Long sessionSeriesId) {
        return sessionRepository.findBySessionSeriesId(sessionSeriesId);
    }

    public List<SessionDTO> findSessionsInRange(LocalDateTime start, LocalDateTime end) {
        return sessionRepository.findBySessionTimeStartBetween(start, end).stream().map(sessionMapper::sessionEntityToSessionDto)
                .toList();
    }

    public List<SessionEntity> getSessionsByGroupIdAndDateRange(Long groupId, LocalDateTime start, LocalDateTime end) {
        return sessionRepository.findByGroupIdAndSessionTimeStartBetween(groupId, start, end);
    }

    public List<SessionDTO> findByGroupIdAndSessionTimeStartBetween(Long groupId, LocalDateTime start, LocalDateTime end) {
        return sessionRepository.findByGroupIdAndSessionTimeStartBetween(groupId, start, end)
                .stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();
    }


}