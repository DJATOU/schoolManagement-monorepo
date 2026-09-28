package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service de bascule automatique de série ({@link SessionSeriesEntity}) — « rollover ».
 *
 * <p>Implémente l'option A du requirement 3 : lorsqu'une séance est ajoutée à un groupe,
 * elle est rattachée à la série courante tant que celle-ci n'a pas atteint son nombre de
 * séances planifiées ({@code totalSessions}). Dès que la série courante est pleine, une
 * nouvelle série est créée automatiquement (nommée via {@link SeriesNamingService}) et la
 * séance y est rattachée. Si aucune série n'existe encore pour le groupe, la première série
 * (séquence 001) est créée.</p>
 *
 * <p>Invariant garanti : aucune série ne contient jamais plus de séances que son
 * {@code totalSessions}.</p>
 */
@Service
public class SeriesRolloverService {

    private final SessionSeriesRepository sessionSeriesRepository;
    private final SessionRepository sessionRepository;
    private final SeriesNamingService seriesNamingService;

    public SeriesRolloverService(SessionSeriesRepository sessionSeriesRepository,
                                 SessionRepository sessionRepository,
                                 SeriesNamingService seriesNamingService) {
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.sessionRepository = sessionRepository;
        this.seriesNamingService = seriesNamingService;
    }

    /**
     * Rattache une séance à la série appropriée du groupe, en créant une nouvelle série si
     * nécessaire (requirements 3.1, 3.2, 3.3).
     *
     * <p>Algorithme :</p>
     * <ol>
     *   <li>Recherche la série courante (la plus récente) du groupe.</li>
     *   <li>Si aucune série n'existe → crée la PREMIÈRE série (séquence 001) et y rattache
     *       la séance (requirement 3.1, cas première série).</li>
     *   <li>Si la série courante n'est pas pleine ({@code count < totalSessions}) → rattache
     *       la séance à la série COURANTE (requirement 3.3).</li>
     *   <li>Si la série courante est pleine ({@code count == totalSessions}) → crée la série
     *       SUIVANTE (nommée avec séquence auto-incrémentée) et y rattache la séance
     *       (requirements 3.1, 3.2).</li>
     * </ol>
     *
     * @param group   le groupe auquel la séance est ajoutée
     * @param session la séance à rattacher
     * @return la série (courante ou nouvellement créée) à laquelle la séance est rattachée
     */
    @Transactional
    public SessionSeriesEntity attachSessionToSeries(GroupEntity group, SessionEntity session) {
        Objects.requireNonNull(group, "Le groupe ne doit pas être nul.");
        Objects.requireNonNull(session, "La séance ne doit pas être nulle.");

        Optional<SessionSeriesEntity> currentOpt = findCurrentSeries(group.getId());

        // Cas 1 : aucune série n'existe encore pour le groupe → créer la première (001).
        if (currentOpt.isEmpty()) {
            return createSeriesAndAttach(group, session);
        }

        SessionSeriesEntity current = currentOpt.get();
        int count = sessionRepository.countBySessionSeriesId(current.getId());

        // Cas 2 : la série courante n'est pas pleine → rattacher à la série courante.
        if (count < current.getTotalSessions()) {
            session.setSessionSeries(current);
            sessionRepository.save(session);
            return current;
        }

        // Cas 3 : la série courante est pleine → créer la série suivante et y rattacher.
        return createSeriesAndAttach(group, session);
    }

    /**
     * Recherche la série courante (la plus récente) d'un groupe.
     *
     * <p>On sélectionne la série au {@code serieTimeStart} maximal ; en cas de date nulle,
     * on se rabat sur l'identifiant afin de conserver un ordre déterministe.</p>
     *
     * @param groupId identifiant du groupe
     * @return la série courante, ou {@link Optional#empty()} si le groupe n'en a aucune
     */
    private Optional<SessionSeriesEntity> findCurrentSeries(Long groupId) {
        List<SessionSeriesEntity> existing = sessionSeriesRepository.findByGroupId(groupId);
        if (existing == null || existing.isEmpty()) {
            return Optional.empty();
        }

        // Tri : d'abord par date de début (les nulles en premier), puis par id ; on prend
        // le dernier élément (le plus récent).
        Comparator<SessionSeriesEntity> byStartThenId = Comparator
                .comparing(SessionSeriesEntity::getSerieTimeStart,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(SessionSeriesEntity::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder()));

        return existing.stream().max(byStartThenId);
    }

    /**
     * Crée une nouvelle série pour le groupe (nommée via {@link SeriesNamingService}, qui
     * calcule automatiquement le numéro de séquence), la sauvegarde puis y rattache la
     * séance.
     *
     * @param group   le groupe associé à la nouvelle série
     * @param session la séance à rattacher à la nouvelle série
     * @return la nouvelle série créée
     */
    private SessionSeriesEntity createSeriesAndAttach(GroupEntity group, SessionEntity session) {
        SessionSeriesEntity newSeries = new SessionSeriesEntity();
        newSeries.setGroup(group);
        newSeries.setName(seriesNamingService.buildName(group, session.getSessionTimeStart()));
        newSeries.setTotalSessions(group.getSessionNumberPerSerie());
        newSeries.setSerieTimeStart(session.getSessionTimeStart());

        SessionSeriesEntity saved = sessionSeriesRepository.save(newSeries);

        session.setSessionSeries(saved);
        sessionRepository.save(session);

        return saved;
    }
}
