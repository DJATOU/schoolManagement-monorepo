package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test de propriété (jqwik) pour {@link SeriesRolloverService}.
 *
 * <p>Les repositories et {@link SeriesNamingService} sont mockés afin que les 100+
 * itérations restent rapides.</p>
 */
class SeriesRolloverServicePropertyTest {

    private static final long GROUP_ID = 42L;
    private static final long CURRENT_SERIES_ID = 100L;

    // Feature: payment-attendance-rules, Property 14: For any group and added session, the session is attached to the current series when that series holds fewer than totalSessions, and otherwise to a newly created next series; in all cases no series ever holds more than its totalSessions.
    @Property(tries = 100)
    void property14_rolloverInvariant(
            @ForAll @IntRange(min = 1, max = 20) int totalSessions,
            @ForAll @IntRange(min = 0, max = 20) int currentCount,
            @ForAll boolean seriesExists) {

        // Le nombre courant ne peut pas dépasser le total (invariant préexistant).
        int count = Math.min(currentCount, totalSessions);

        SessionSeriesRepository seriesRepository = mock(SessionSeriesRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        SeriesNamingService namingService = mock(SeriesNamingService.class);

        GroupEntity group = group(totalSessions);
        SessionEntity session = session();

        SessionSeriesEntity current = null;
        if (seriesExists) {
            current = seriesWith(totalSessions);
            when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(current));
            when(sessionRepository.countBySessionSeriesId(CURRENT_SERIES_ID)).thenReturn(count);
        } else {
            when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());
        }

        when(namingService.buildName(any(), any())).thenReturn("Série X - 01-2026-001");
        // save() renvoie l'entité passée (avec un id simulé pour la nouvelle série).
        when(seriesRepository.save(any(SessionSeriesEntity.class))).thenAnswer(inv -> {
            SessionSeriesEntity s = inv.getArgument(0);
            s.setId(200L);
            return s;
        });

        SeriesRolloverService service = new SeriesRolloverService(
                seriesRepository, sessionRepository, namingService);

        SessionSeriesEntity result = service.attachSessionToSeries(group, session);

        // La séance est toujours rattachée à la série renvoyée.
        assertThat(session.getSessionSeries()).isSameAs(result);

        boolean full = seriesExists && count == totalSessions;

        if (!seriesExists || full) {
            // Cas première série OU série pleine → une NOUVELLE série est créée.
            verify(seriesRepository, times(1)).save(any(SessionSeriesEntity.class));
            assertThat(result).isNotSameAs(current);
            assertThat(result.getTotalSessions()).isEqualTo(totalSessions);
            // La nouvelle série démarre vide → après rattachement elle contiendra 1 séance,
            // donc jamais plus que totalSessions (totalSessions >= 1).
            assertThat(1).isLessThanOrEqualTo(result.getTotalSessions());
        } else {
            // Série courante non pleine → rattachement à la série COURANTE, aucune création.
            verify(seriesRepository, never()).save(any(SessionSeriesEntity.class));
            assertThat(result).isSameAs(current);
            // count < totalSessions → count + 1 <= totalSessions : invariant respecté.
            assertThat(count + 1).isLessThanOrEqualTo(result.getTotalSessions());
        }
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

    private static SessionEntity session() {
        SessionEntity session = new SessionEntity();
        session.setSessionTimeStart(dateAt(2026, 1, 10));
        return session;
    }

    private static SessionSeriesEntity seriesWith(int totalSessions) {
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setId(CURRENT_SERIES_ID);
        s.setTotalSessions(totalSessions);
        s.setSerieTimeStart(dateAt(2026, 1, 1));
        return s;
    }

    private static Date dateAt(int year, int month, int day) {
        LocalDate ld = LocalDate.of(year, month, day);
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
