package com.school.management.service.payroll;

import com.school.management.repository.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Une série est-elle terminée ? Toutes ses séances actives validées, et au moins une (spec
 * teacher-payroll, Série_Terminée).
 *
 * <p>Lu sur les séances elles-mêmes : {@code session_series.sessions_completed} n'est tenu à jour
 * par aucun code, et {@code total_sessions} est le nombre prévu, pas celui des séances réellement
 * créées. Une séance désactivée n'est ni comptée, ni attendue.</p>
 */
@Service
public class SeriesCompletionService {

    private final SessionRepository sessionRepository;

    public SeriesCompletionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Avancement d'une série.
     *
     * @param activeSessions    séances actives
     * @param validatedSessions séances actives validées
     */
    public record SeriesCompletion(long activeSessions, long validatedSessions) {

        /** Sans séance, une série n'a rien produit : elle n'est pas terminée. */
        public boolean finished() {
            return activeSessions > 0 && validatedSessions == activeSessions;
        }

        /** Séances actives encore à valider. */
        public long remaining() {
            return activeSessions - validatedSessions;
        }

        static SeriesCompletion empty() {
            return new SeriesCompletion(0, 0);
        }
    }

    /** Avancement de plusieurs séries en une requête ; une série sans séance active vaut (0, 0). */
    @Transactional(readOnly = true)
    public Map<Long, SeriesCompletion> of(Collection<Long> seriesIds) {
        Map<Long, SeriesCompletion> result = new HashMap<>();
        if (seriesIds.isEmpty()) {
            return result;
        }
        seriesIds.forEach(seriesId -> result.put(seriesId, SeriesCompletion.empty()));
        for (Object[] row : sessionRepository.countCompletionBySeries(seriesIds)) {
            result.put((Long) row[0], new SeriesCompletion(count(row[1]), count(row[2])));
        }
        return result;
    }

    /** Avancement d'une série. */
    @Transactional(readOnly = true)
    public SeriesCompletion of(Long seriesId) {
        Objects.requireNonNull(seriesId, "seriesId");
        return of(List.of(seriesId)).get(seriesId);
    }

    private static long count(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
