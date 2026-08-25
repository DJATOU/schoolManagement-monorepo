package com.school.management.service;

import com.school.management.mapper.SessionMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.RoomRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.payment.PaymentDetailDeactivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) du rattachement de série à la création d'une séance
 * ({@link SessionService#createSession}).
 *
 * <p>Ce rattachement n'était appliqué qu'aux séances récurrentes : la création à l'unité
 * acceptait la série envoyée par le client sans contrôle de capacité, ce qui laissait une
 * série de 2 séances en accueillir une 3ᵉ. Comme la facturation repose sur
 * {@code totalSessions}, la séance excédentaire n'était jamais facturée. Ces tests verrouillent
 * le fait que le serveur décide seul de la série.</p>
 */
class SessionCreationSeriesAttachmentTest {

    private static final long GROUP_ID = 5L;
    private static final long CURRENT_YEAR_ID = 1L;

    private SessionRepository sessionRepository;
    private SessionSeriesRepository seriesRepository;
    private SeriesNamingService namingService;
    private SessionService service;

    private GroupEntity group;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        seriesRepository = mock(SessionSeriesRepository.class);
        namingService = mock(SeriesNamingService.class);

        // Année courante : le garde lecture seule doit laisser passer la création.
        SchoolYearEntity currentYear = new SchoolYearEntity();
        currentYear.setId(CURRENT_YEAR_ID);
        CurrentSchoolYearService currentSchoolYearService = mock(CurrentSchoolYearService.class);
        when(currentSchoolYearService.requireCurrent()).thenReturn(currentYear);
        ReadOnlyYearGuard guard = new ReadOnlyYearGuard(currentSchoolYearService);

        group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("Groupe physique");
        group.setSessionNumberPerSerie(2);
        group.setSchoolYear(currentYear);

        SeriesRolloverService rollover =
                new SeriesRolloverService(seriesRepository, sessionRepository, namingService);

        service = new SessionService(
                sessionRepository,
                mock(GroupRepository.class),
                mock(SessionMapper.class),
                mock(RoomRepository.class),
                mock(TeacherRepository.class),
                seriesRepository,
                mock(PaymentDetailDeactivationService.class),
                mock(AttendanceService.class),
                guard,
                rollover);

        when(namingService.buildName(any(), any())).thenReturn("Groupe physique - 08-2026-002");
        when(sessionRepository.save(any(SessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(seriesRepository.save(any(SessionSeriesEntity.class))).thenAnswer(inv -> {
            SessionSeriesEntity s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(99L);
            }
            return s;
        });
    }

    private SessionSeriesEntity series(long id, int totalSessions) {
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setId(id);
        s.setGroup(group);
        s.setTotalSessions(totalSessions);
        s.setSerieTimeStart(new java.util.Date(0));
        return s;
    }

    private SessionEntity newSession() {
        SessionEntity session = new SessionEntity();
        session.setGroup(group);
        session.setSessionTimeStart(new java.util.Date());
        return session;
    }

    @Test
    void rattache_a_la_serie_courante_quand_elle_n_est_pas_pleine() {
        SessionSeriesEntity current = series(5L, 2);
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(java.util.List.of(current));
        when(sessionRepository.countBySessionSeriesId(5L)).thenReturn(1);

        SessionEntity created = service.createSession(newSession());

        assertThat(created.getSessionSeries()).isSameAs(current);
        verify(seriesRepository, org.mockito.Mockito.never()).save(any(SessionSeriesEntity.class));
    }

    @Test
    void cree_la_serie_suivante_quand_la_courante_est_pleine() {
        // Série de 2 séances déjà complète : la 3ᵉ séance ne doit pas y entrer.
        SessionSeriesEntity full = series(5L, 2);
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(java.util.List.of(full));
        when(sessionRepository.countBySessionSeriesId(5L)).thenReturn(2);

        SessionEntity created = service.createSession(newSession());

        assertThat(created.getSessionSeries()).isNotNull();
        assertThat(created.getSessionSeries().getId()).isNotEqualTo(5L);
        assertThat(created.getSessionSeries().getTotalSessions()).isEqualTo(2);
        verify(seriesRepository).save(any(SessionSeriesEntity.class));
    }

    @Test
    void ignore_la_serie_imposee_par_le_client_et_applique_la_capacite() {
        SessionSeriesEntity full = series(5L, 2);
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(java.util.List.of(full));
        when(sessionRepository.countBySessionSeriesId(5L)).thenReturn(2);

        // Le client désigne explicitement une série déjà pleine : la valeur est écartée.
        SessionEntity session = newSession();
        session.setSessionSeries(full);

        SessionEntity created = service.createSession(session);

        assertThat(created.getSessionSeries().getId()).isNotEqualTo(5L);
    }
}
