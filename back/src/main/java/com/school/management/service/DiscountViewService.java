package com.school.management.service;

import com.school.management.dto.DiscountResponseDTO;
import com.school.management.mapper.DiscountMapper;
import com.school.management.persistance.DiscountEntity;
import com.school.management.persistance.DiscountScope;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service de présentation des réductions : enrichit les réductions des libellés lisibles
 * nécessaires à l'affichage (nom de l'étudiant, libellé de la cible).
 *
 * <p>Séparé de {@link DiscountService}, qui reste concentré sur le métier critique
 * (validation à la création et résolution du taux applicable). Les libellés sont résolus
 * <strong>dans la transaction</strong> afin d'éviter toute {@code LazyInitializationException}
 * sur la relation étudiant.</p>
 */
@Service
public class DiscountViewService {

    private final DiscountService discountService;
    private final DiscountMapper discountMapper;
    private final GroupRepository groupRepository;
    private final SessionSeriesRepository sessionSeriesRepository;
    private final SessionRepository sessionRepository;

    public DiscountViewService(DiscountService discountService,
                               DiscountMapper discountMapper,
                               GroupRepository groupRepository,
                               SessionSeriesRepository sessionSeriesRepository,
                               SessionRepository sessionRepository) {
        this.discountService = discountService;
        this.discountMapper = discountMapper;
        this.groupRepository = groupRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Liste toutes les réductions, enrichies pour l'affichage.
     *
     * @return les réductions avec nom d'étudiant et libellé de cible
     */
    @Transactional(readOnly = true)
    public List<DiscountResponseDTO> findAllForDisplay() {
        return findForDisplay(null);
    }

    /**
     * Liste les réductions, éventuellement restreintes à un étudiant, enrichies pour
     * l'affichage.
     *
     * @param studentId identifiant de l'étudiant à filtrer, ou {@code null} pour tous
     * @return les réductions avec nom d'étudiant et libellé de cible
     */
    @Transactional(readOnly = true)
    public List<DiscountResponseDTO> findForDisplay(Long studentId) {
        return discountService.findAll().stream()
                .filter(discount -> matchesStudent(discount, studentId))
                .map(this::toDisplayDto)
                .toList();
    }

    /** Vrai si aucun filtre n'est demandé, ou si la réduction vise l'étudiant demandé. */
    private boolean matchesStudent(DiscountEntity discount, Long studentId) {
        if (studentId == null) {
            return true;
        }
        return discount.getStudent() != null
                && studentId.equals(discount.getStudent().getId());
    }

    /**
     * Construit le DTO d'affichage d'une réduction (libellés résolus).
     *
     * @param discount réduction à convertir
     * @return le DTO enrichi
     */
    public DiscountResponseDTO toDisplayDto(DiscountEntity discount) {
        // Mapping de base délégué à MapStruct ; seuls les libellés sont résolus ici.
        DiscountResponseDTO base = discountMapper.toDto(discount);
        return new DiscountResponseDTO(
                base.id(),
                base.studentId(),
                base.scope(),
                base.groupId(),
                base.seriesId(),
                base.sessionId(),
                base.rate(),
                studentName(discount),
                targetName(discount));
    }

    // ------------------------------------------------------------------
    // Résolution des libellés
    // ------------------------------------------------------------------

    /** Nom complet de l'étudiant, ou {@code null} si indisponible. */
    private String studentName(DiscountEntity discount) {
        StudentEntity student = discount.getStudent();
        if (student == null) {
            return null;
        }
        String first = student.getFirstName() != null ? student.getFirstName() : "";
        String last = student.getLastName() != null ? student.getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    /**
     * Libellé de la cible visée selon la portée : nom du groupe, nom de la série ou titre
     * de la séance. Renvoie {@code null} si la cible est introuvable ou sans libellé.
     */
    private String targetName(DiscountEntity discount) {
        DiscountScope scope = discount.getScope();
        if (scope == null) {
            return null;
        }
        return switch (scope) {
            case GROUP -> discount.getGroupId() == null ? null
                    : groupRepository.findById(discount.getGroupId())
                    .map(g -> g.getName())
                    .orElse(null);
            case SERIES -> discount.getSeriesId() == null ? null
                    : sessionSeriesRepository.findById(discount.getSeriesId())
                    .map(s -> s.getName())
                    .orElse(null);
            case SESSION -> discount.getSessionId() == null ? null
                    : sessionRepository.findById(discount.getSessionId())
                    .map(s -> s.getTitle())
                    .orElse(null);
        };
    }
}
