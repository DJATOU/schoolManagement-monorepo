package com.school.management.service;

import com.school.management.mapper.SessionMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.RoomRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.payment.PaymentDetailDeactivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de la modification d'une séance ({@code PATCH /api/sessions/{id}}).
 *
 * <p>Contexte de régression : l'étape « champs simples » passait par ModelMapper. Le client
 * envoie l'objet séance complet, qui contient à la fois {@code groupName} et l'objet imbriqué
 * {@code group}. ModelMapper trouvait alors deux sources possibles pour {@code group.name},
 * levait une {@code ConfigurationException} et abandonnait <em>tout</em> le patch : le type de
 * séance modifié dans l'interface n'était jamais enregistré.</p>
 */
class SessionUpdateTest {

    private static final long SESSION_ID = 42L;

    private SessionRepository sessionRepository;
    private SessionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);

        service = new SessionService(
                sessionRepository,
                mock(GroupRepository.class),
                mock(SessionMapper.class),
                mock(RoomRepository.class),
                mock(TeacherRepository.class),
                mock(SessionSeriesRepository.class),
                mock(PaymentDetailDeactivationService.class),
                mock(AttendanceService.class),
                mock(ReadOnlyYearGuard.class),
                mock(SeriesRolloverService.class));

        when(sessionRepository.save(any(SessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SessionEntity existingSession() {
        GroupEntity group = new GroupEntity();
        group.setId(7L);
        group.setName("Groupe physique");

        SessionEntity session = new SessionEntity();
        session.setId(SESSION_ID);
        session.setTitle("Séance 1");
        session.setSessionType("EXERCICES");
        session.setGroup(group);

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        return session;
    }

    /**
     * Charge utile réaliste : l'interface diffuse l'objet séance complet, donc des champs
     * dérivés ({@code groupName}) et imbriqués ({@code group}, {@code students}) accompagnent
     * les champs réellement modifiables.
     */
    private Map<String, Object> fullClientPayload() {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", 7);
        group.put("name", "Groupe physique");

        Map<String, Object> updates = new HashMap<>();
        updates.put("id", 42);
        updates.put("sessionType", "COURS");
        updates.put("groupName", "Groupe physique");
        updates.put("roomName", "Salle 1");
        updates.put("teacherName", "Prof X");
        updates.put("group", group);
        updates.put("students", List.of(Map.of("id", 3, "isPresent", true)));
        return updates;
    }

    @Test
    void updateSession_persistsSessionType_evenWithFullClientPayload() {
        existingSession();

        SessionEntity updated = service.updateSession(SESSION_ID, fullClientPayload());

        assertThat(updated.getSessionType()).isEqualTo("COURS");
    }

    @Test
    void updateSession_keepsManagedGroup_whenPayloadCarriesNestedGroup() {
        existingSession();

        SessionEntity updated = service.updateSession(SESSION_ID, fullClientPayload());

        // Le groupe reste celui chargé en base : l'objet imbriqué du client est ignoré.
        assertThat(updated.getGroup().getId()).isEqualTo(7L);
        assertThat(updated.getGroup().getName()).isEqualTo("Groupe physique");
    }

    @Test
    void updateSession_appliesTitleDescriptionAndFinishedFlag() {
        existingSession();

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Séance révision");
        updates.put("description", "Chapitre 3");
        updates.put("isFinished", true);

        SessionEntity updated = service.updateSession(SESSION_ID, updates);

        assertThat(updated.getTitle()).isEqualTo("Séance révision");
        assertThat(updated.getDescription()).isEqualTo("Chapitre 3");
        assertThat(updated.getIsFinished()).isTrue();
    }

    @Test
    void updateSession_leavesUntouchedFields_whenKeyAbsent() {
        existingSession();

        SessionEntity updated = service.updateSession(SESSION_ID, new HashMap<>(Map.of("title", "Autre")));

        assertThat(updated.getSessionType()).isEqualTo("EXERCICES");
    }
}
