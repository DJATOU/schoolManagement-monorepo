package com.school.management.service;

import com.school.management.dto.DiscountRequestDTO;
import com.school.management.persistance.DiscountEntity;
import com.school.management.persistance.DiscountScope;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.DiscountRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service de gestion des réductions (discounts).
 *
 * <p>Deux responsabilités :</p>
 * <ul>
 *   <li>{@link #create(DiscountRequestDTO)} : valide et persiste une réduction. Une
 *       réduction porte sur exactement une portée ({@link DiscountScope}) et un taux
 *       compris dans l'intervalle {@code [0.00, 1.00]}.</li>
 *   <li>{@link #resolveRate(Long, Long)} : résout l'unique taux applicable pour un
 *       contexte de facturation (étudiant + série) en sélectionnant la portée la plus
 *       spécifique (Session &gt; Série &gt; Groupe). Les portées ne sont jamais cumulées.</li>
 * </ul>
 */
@Service
public class DiscountService {

    /** Échelle monétaire / de taux appliquée au taux résolu (cohérence avec le calculateur). */
    private static final int RATE_SCALE = 2;
    private static final RoundingMode RATE_ROUNDING = RoundingMode.HALF_UP;

    private final DiscountRepository discountRepository;
    private final SessionSeriesRepository sessionSeriesRepository;

    public DiscountService(DiscountRepository discountRepository,
                           SessionSeriesRepository sessionSeriesRepository) {
        this.discountRepository = discountRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
    }

    /**
     * Crée une réduction après validation.
     *
     * <p>Règles (requirements 12.1, 12.7, 12.8) :</p>
     * <ul>
     *   <li>exactement un identifiant de portée doit être renseigné et doit correspondre
     *       au scope déclaré ; zéro ou plusieurs → rejet ;</li>
     *   <li>le taux doit être non nul et compris dans {@code [0.00, 1.00]}.</li>
     * </ul>
     *
     * @param dto données de création
     * @return la réduction persistée
     * @throws CustomServiceException (HTTP 400) si la validation échoue
     */
    /**
     * Liste toutes les réductions enregistrées.
     *
     * @return la liste des réductions (vide si aucune)
     */
    @Transactional(readOnly = true)
    public List<DiscountEntity> findAll() {
        return discountRepository.findAll();
    }

    public DiscountEntity create(DiscountRequestDTO dto) {
        Objects.requireNonNull(dto, "La requête de réduction ne doit pas être nulle.");

        validateScope(dto);
        validateRate(dto.rate());

        StudentEntity student = StudentEntity.builder().id(dto.studentId()).build();

        DiscountEntity discount = DiscountEntity.builder()
                .student(student)
                .scope(dto.scope())
                .groupId(dto.groupId())
                .seriesId(dto.seriesId())
                .sessionId(dto.sessionId())
                .rate(dto.rate())
                .build();

        return discountRepository.save(discount);
    }

    /**
     * Met à jour le taux d'une réduction existante.
     *
     * <p>Seul le taux est modifiable : changer la portée ou la cible reviendrait à une autre
     * réduction (il faut alors la supprimer et en créer une nouvelle). Le nouveau taux est
     * soumis à la même validation que la création ({@code [0.00, 1.00]}).</p>
     *
     * @param id   identifiant de la réduction
     * @param rate nouveau taux
     * @return la réduction mise à jour
     * @throws CustomServiceException (404) si la réduction est introuvable,
     *                                (400) si le taux est hors bornes
     */
    @Transactional
    public DiscountEntity updateRate(Long id, BigDecimal rate) {
        validateRate(rate);
        DiscountEntity discount = loadDiscount(id);
        discount.setRate(rate);
        return discountRepository.save(discount);
    }

    /**
     * Supprime définitivement une réduction.
     *
     * <p>Suppression réelle (et non désactivation) : une réduction encore présente resterait
     * prise en compte par {@link #resolveRate(Long, Long)} et continuerait donc de minorer
     * les montants dus.</p>
     *
     * @param id identifiant de la réduction
     * @throws CustomServiceException (404) si la réduction est introuvable
     */
    @Transactional
    public void delete(Long id) {
        discountRepository.delete(loadDiscount(id));
    }

    /** Charge une réduction ou lève une 404. */
    private DiscountEntity loadDiscount(Long id) {
        return discountRepository.findById(id)
                .orElseThrow(() -> new CustomServiceException(
                        "Réduction introuvable pour l'identifiant : " + id,
                        HttpStatus.NOT_FOUND));
    }

    /**
     * Résout l'unique taux de réduction applicable pour un contexte de facturation.
     *
     * <p>Sélectionne la portée la plus spécifique parmi les réductions applicables
     * (Session &gt; Série &gt; Groupe) et n'additionne jamais les portées. Une exemption
     * (réduction de portée Groupe au taux 1.00) se résout naturellement à 1.00.</p>
     *
     * <p>Portées applicables au contexte (étudiant, série) :</p>
     * <ul>
     *   <li>SESSION : la séance visée appartient à la série ;</li>
     *   <li>SERIES : la série visée est la série courante ;</li>
     *   <li>GROUP : le groupe visé est celui de la série.</li>
     * </ul>
     *
     * <p>En cas d'égalité de spécificité (plusieurs réductions de même portée applicables),
     * la sélection est déterministe : la réduction d'identifiant le plus élevé est retenue.</p>
     *
     * @param studentId identifiant de l'étudiant
     * @param seriesId  identifiant de la série (contexte de facturation)
     * @return le taux applicable (échelle 2, HALF_UP) ; {@code 0.00} si aucune réduction
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveRate(Long studentId, Long seriesId) {
        Objects.requireNonNull(studentId, "studentId ne doit pas être nul.");
        Objects.requireNonNull(seriesId, "seriesId ne doit pas être nul.");

        SessionSeriesEntity series = sessionSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new CustomServiceException(
                        "Série introuvable pour l'identifiant : " + seriesId,
                        HttpStatus.NOT_FOUND));

        Long groupId = series.getGroup() != null ? series.getGroup().getId() : null;
        Set<Long> sessionIds = seriesSessionIds(series);

        List<DiscountEntity> discounts = discountRepository.findByStudentId(studentId);

        // Portée la plus spécifique d'abord : Session > Série > Groupe.
        Optional<BigDecimal> sessionRate = mostSpecific(discounts, DiscountScope.SESSION,
                d -> d.getSessionId() != null && sessionIds.contains(d.getSessionId()));
        if (sessionRate.isPresent()) {
            return normalize(sessionRate.get());
        }

        Optional<BigDecimal> seriesRate = mostSpecific(discounts, DiscountScope.SERIES,
                d -> seriesId.equals(d.getSeriesId()));
        if (seriesRate.isPresent()) {
            return normalize(seriesRate.get());
        }

        Optional<BigDecimal> groupRate = mostSpecific(discounts, DiscountScope.GROUP,
                d -> groupId != null && groupId.equals(d.getGroupId()));
        if (groupRate.isPresent()) {
            return normalize(groupRate.get());
        }

        return normalize(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // Helpers privés
    // ------------------------------------------------------------------

    private void validateScope(DiscountRequestDTO dto) {
        DiscountScope scope = dto.scope();
        boolean valid = scope != null && switch (scope) {
            case GROUP -> dto.groupId() != null && dto.seriesId() == null && dto.sessionId() == null;
            case SERIES -> dto.seriesId() != null && dto.groupId() == null && dto.sessionId() == null;
            case SESSION -> dto.sessionId() != null && dto.groupId() == null && dto.seriesId() == null;
        };

        if (!valid) {
            throw new CustomServiceException(
                    "Une réduction doit avoir exactement une portée correspondant à son scope.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateRate(BigDecimal rate) {
        if (rate == null
                || rate.compareTo(BigDecimal.ZERO) < 0
                || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new CustomServiceException(
                    "Le taux de réduction doit être compris entre 0.00 et 1.00.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Sélectionne, parmi les réductions de la portée donnée et applicables au contexte,
     * le taux de celle d'identifiant le plus élevé (choix déterministe).
     */
    private Optional<BigDecimal> mostSpecific(List<DiscountEntity> discounts,
                                              DiscountScope scope,
                                              java.util.function.Predicate<DiscountEntity> applicable) {
        return discounts.stream()
                .filter(d -> d.getScope() == scope)
                .filter(applicable)
                .max(Comparator.comparing(DiscountEntity::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(DiscountEntity::getRate);
    }

    private Set<Long> seriesSessionIds(SessionSeriesEntity series) {
        Set<SessionEntity> sessions = series.getSessions();
        if (sessions == null) {
            return Set.of();
        }
        return sessions.stream()
                .map(SessionEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private BigDecimal normalize(BigDecimal rate) {
        return rate.setScale(RATE_SCALE, RATE_ROUNDING);
    }
}
