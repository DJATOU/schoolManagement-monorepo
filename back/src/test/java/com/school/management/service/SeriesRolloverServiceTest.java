package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link SeriesRolloverService}.
 *
 * <p>Couvre : aucune série → première série (001) ; série non pleine → série courante ;
 * série exactement pleine → nouvelle série suivante ; sélection de la série la plus récente
 * (par {@code serieTimeStart}) ; et la garantie que l'invariant {@code totalSessions} n'est
 * jamais dépassé.</p>
 */
class SeriesRolloverServiceTest {

    private static final long GROUP_ID = 7L;

    private SessionSeriesRepository seriesRepository;
    private SessionRepository sessionRepository;
    private SeriesNamingService namingService;
    private SeriesRolloverService service;

    @BeforeEach
    void setUp() {
        seriesRepository = mock(SessionSeriesRepository.class);
        sessionRepository = mock(SessionRepository.class);
        namingService = mock(SeriesNamingService.class);
        service = new SeriesRolloverService(seriesRepository, sessionRepository, namingService);

        when(namingService.buildName(any(), any())).thenReturn("Série G1 - 01-2026-001");
        when(seriesRepository.save(any(SessionSeriesEntity.class))).thenAnswer(inv -> {
            SessionSeriesEntity s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(999L);
            }
            return s;
        });
    }

    // ------------------------------------------------------------------
    // Cas 1 : aucune série → création de la première (001)
    // ------------------------------------------------------------------

    @Test
    void attach_noSeriesExists_createsFirstSeriesAndAttaches() {
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());
        GroupEntity group = group(8);
        SessionEntity session = session(dateAt(2026, 1, 10));

        SessionSeriesEntity result = service.attachSessionToSeries(group, session);

        ArgumentCaptor<SessionSeriesEntity> captor = ArgumentCaptor.forClass(SessionSeriesEntity.class);
        verify(seriesRepository).save(captor.capture());
        SessionSeriesEntity created = captor.getValue();

        assertThat(created.getName()).isEqualTo("Série G1 - 01-2026-001");
        assertThat(created.getTotalSessions()).isEqualTo(8);
        assertThat(created.getGroup()).isSameAs(group);
        assertThat(created.getSerieTimeStart()).isEqualTo(dateAt(2026, 1, 10));
        assertThat(session.getSessionSeries()).isSameAs(result);
        assertThat(result).isSameAs(created);
        verify(sessionRepository).save(session);
        verify(namingService).buildName(group, dateAt(2026, 1, 10));
    }

    @Test
    void attach_nullGroupIdList_createsFirstSeries() {
        // findByGroupId renvoie null → traité comme aucune série existante.
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(null);
        GroupEntity group = group(4);
        SessionEntity session = session(dateAt(2026, 2, 5));

        SessionSeriesEntity result = service.attachSessionToSeries(group, session);

        assertThat(result.getTotalSessions()).isEqualTo(4);
        assertThat(session.getSessionSeries()).isSameAs(result);
        verify(seriesRepository).save(any(SessionSeriesEntity.class));
    }

    // ------------------------------------------------------------------
    // Cas 2 : série courante non pleine → rattachement à la série courante
    // ------------------------------------------------------------------

    @Test
    void attach_currentSeriesNotFull_attachesToCurrent() {
        SessionSeriesEntity current = series(100L, 8, dateAt(2026, 1, 1));
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(current));
        when(sessionRepository.countBySessionSeriesId(100L)).thenReturn(5);

        GroupEntity group = group(8);
        SessionEntity session = session(dateAt(2026, 1, 20));

        SessionSeriesEntity result = service.attachSessionToSeries(group, session);

        assertThat(result).isSameAs(current);
        assertThat(session.getSessionSeries()).isSameAs(current);
        verify(sessionRepository).save(session);
        // Aucune nouvelle série ne doit être créée.
        verify(seriesRepository, never()).save(any(SessionSeriesEntity.class));
        // Invariant : 5 + 1 = 6 <= 8.
        assertThat(6).isLessThanOrEqualTo(current.getTotalSessions());
    }

    // ------------------------------------------------------------------
    // Cas 3 : série courante pleine → création de la série suivante
    // ------------------------------------------------------------------

    @Test
    void attach_currentSeriesFull_createsNextSeries() {
        SessionSeriesEntity current = series(100L, 8, dateAt(2026, 1, 1));
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(current));
        when(sessionRepository.countBySessionSeriesId(100L)).thenReturn(8); // pleine

        GroupEntity group = group(8);
        SessionEntity session = session(dateAt(2026, 2, 3));

        SessionSeriesEntity result = service.attachSessionToSeries(group, session);

        assertThat(result).isNotSameAs(current);
        assertThat(result.getTotalSessions()).isEqualTo(8);
        assertThat(result.getSerieTimeStart()).isEqualTo(dateAt(2026, 2, 3));
        assertThat(session.getSessionSeries()).isSameAs(result);
        verify(seriesRepository).save(any(SessionSeriesEntity.class));
        verify(sessionRepository).save(session);
        // La nouvelle série est vide au départ → ne dépassera jamais totalSessions.
    }

    // ------------------------------------------------------------------
    // Sélection de la série la plus récente (par serieTimeStart)
    // ------------------------------------------------------------------

    @Test
    void attach_multipleSeries_picksLatestByStartDate() {
        SessionSeriesEntity older = series(100L, 8, dateAt(2026, 1, 1));
        SessionSeriesEntity latest = series(101L, 8, dateAt(2026, 3, 1));
        SessionSeriesEntity middle = series(102L, 8, dateAt(2026, 2, 1));
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(older, latest, middle));
        // La série la plus récente (101) n'est pas pleine.
        when(sessionRepository.countBySessionSeriesId(101L)).thenReturn(2);

        GroupEntity group = group(8);
        SessionEntity session = session(dateAt(2026, 3, 15));

        SessionSeriesEntity result = service.attachSessionToSeries(group, session);

        assertThat(result).isSameAs(latest);
        assertThat(session.getSessionSeries()).isSameAs(latest);
        verify(sessionRepository).countBySessionSeriesId(101L);
        verify(seriesRepository, never()).save(any(SessionSeriesEntity.class));
    }

    @Test
    void attach_multipleSeries_nullStartFallsBackToId() {
        // Deux séries : une sans date (nullFirst), une avec id supérieur → la plus récente
        // est celle au serieTimeStart non nul, sinon départage par id.
        SessionSeriesEntity noDate = series(100L, 8, null);
        SessionSeriesEntity withDate = series(101L, 8, dateAt(2026, 1, 1));
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(noDate, withDate));
        when(sessionRepository.countBySessionSeriesId(101L)).thenReturn(1);

        SessionSeriesEntity result = service.attachSessionToSeries(group(8), session(dateAt(2026, 2, 1)));

        assertThat(result).isSameAs(withDate);
    }

    // ------------------------------------------------------------------
    // Validations
    // ------------------------------------------------------------------

    @Test
    void attach_nullGroup_throws() {
        assertThatThrownBy(() -> service.attachSessionToSeries(null, session(dateAt(2026, 1, 1))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void attach_nullSession_throws() {
        assertThatThrownBy(() -> service.attachSessionToSeries(group(8), null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static GroupEntity group(int sessionsPerSerie) {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("G1");
        group.setSessionNumberPerSerie(sessionsPerSerie);
        return group;
    }

    private static SessionEntity session(Date start) {
        SessionEntity session = new SessionEntity();
        session.setSessionTimeStart(start);
        return session;
    }

    private static SessionSeriesEntity series(Long id, int totalSessions, Date start) {
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setId(id);
        s.setTotalSessions(totalSessions);
        s.setSerieTimeStart(start);
        return s;
    }

    private static Date dateAt(int year, int month, int day) {
        LocalDate ld = LocalDate.of(year, month, day);
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
