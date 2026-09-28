package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionSeriesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Service de nommage des séries ({@link SessionSeriesEntity}).
 *
 * <p>Deux responsabilités (requirements 2.1 à 2.5) :</p>
 * <ul>
 *   <li>{@link #buildName(GroupEntity, Date)} : construit le nom d'une série au format
 *       exact {@code "Série {group_name} - {MM}-{yyyy}-{NNN}"} où {@code MM} est le mois
 *       sur deux chiffres (01-12), {@code yyyy} l'année sur quatre chiffres et
 *       {@code NNN} le numéro de séquence sur trois chiffres (avec zéros de tête) ;</li>
 *   <li>{@link #nextSequenceNumber(Long, Date)} : calcule le prochain numéro de séquence
 *       pour un groupe dans le mois calendaire de la date de début fournie. Le compteur
 *       redémarre à 1 (001) à chaque nouveau mois calendaire.</li>
 * </ul>
 */
@Service
public class SeriesNamingService {

    private final SessionSeriesRepository sessionSeriesRepository;

    public SeriesNamingService(SessionSeriesRepository sessionSeriesRepository) {
        this.sessionSeriesRepository = sessionSeriesRepository;
    }

    /**
     * Construit le nom d'une série au format
     * {@code "Série {group_name} - {MM}-{yyyy}-{NNN}"}.
     *
     * <p>Exemple : {@code "Série Math-A - 03-2026-001"}.</p>
     *
     * @param group       le groupe associé à la série (fournit le nom et l'identifiant)
     * @param seriesStart la date de début de la série (fournit mois et année)
     * @return le nom formaté de la série
     */
    public String buildName(GroupEntity group, Date seriesStart) {
        Objects.requireNonNull(group, "Le groupe ne doit pas être nul.");
        Objects.requireNonNull(seriesStart, "La date de début de série ne doit pas être nulle.");

        LocalDate start = toLocalDate(seriesStart);
        int sequence = nextSequenceNumber(group.getId(), seriesStart);

        // Format : "{group} - {MM}-{yyyy}-{NNN}" (mois 1-based sur 2 chiffres, année sur
        // 4 chiffres, séquence sur 3 chiffres avec zéros de tête).
        //
        // Le nom est volontairement NEUTRE EN LANGUE : ni le mot « Série », ni un nom de
        // mois. C'est une donnée stockée, elle ne peut pas être retraduite après coup ;
        // l'interface préfixe elle-même « Série : » / « Series: » via ngx-translate, et le
        // mois numérique se lit dans les deux langues.
        return String.format("%s - %02d-%04d-%03d",
                group.getName(), start.getMonthValue(), start.getYear(), sequence);
    }

    /**
     * Calcule le prochain numéro de séquence pour un groupe dans le mois calendaire de
     * {@code seriesStart}.
     *
     * <p>Compte les séries existantes du groupe dont la date {@code serieTimeStart} tombe
     * dans le même mois ET la même année que {@code seriesStart}, puis renvoie ce compte
     * incrémenté de un. La première série d'un mois donne donc 1 (001). Un nouveau mois
     * calendaire redémarre à 1, indépendamment des mois précédents (requirements 2.3, 2.4,
     * 2.5).</p>
     *
     * @param groupId     identifiant du groupe
     * @param seriesStart date de début de la nouvelle série
     * @return le numéro de séquence à attribuer (≥ 1)
     */
    @Transactional(readOnly = true)
    public int nextSequenceNumber(Long groupId, Date seriesStart) {
        Objects.requireNonNull(groupId, "groupId ne doit pas être nul.");
        Objects.requireNonNull(seriesStart, "La date de début de série ne doit pas être nulle.");

        LocalDate target = toLocalDate(seriesStart);

        List<SessionSeriesEntity> existing = sessionSeriesRepository.findByGroupId(groupId);

        long countInMonth = existing.stream()
                .map(SessionSeriesEntity::getSerieTimeStart)
                .filter(Objects::nonNull)
                .map(this::toLocalDate)
                .filter(d -> d.getYear() == target.getYear()
                        && d.getMonthValue() == target.getMonthValue())
                .count();

        return (int) countInMonth + 1;
    }

    /**
     * Convertit une {@link Date} héritée en {@link LocalDate} dans le fuseau système,
     * pour extraire mois et année de façon cohérente.
     */
    private LocalDate toLocalDate(Date date) {
        return Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }
}
