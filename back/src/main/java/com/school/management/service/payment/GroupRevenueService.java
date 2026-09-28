package com.school.management.service.payment;

import com.school.management.dto.revenue.GroupRevenueDTO;
import com.school.management.dto.revenue.MonthRevenueDTO;
import com.school.management.dto.revenue.SeriesRevenueDTO;
import com.school.management.dto.revenue.SessionRevenueDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calcul des encaissements d'un groupe : ce qui est entré en caisse, ce qui était attendu,
 * ce qu'il reste à recouvrer, ventilé par série, par séance et par mois d'encaissement.
 *
 * <h2>Ce que « encaissé » signifie ici</h2>
 * Somme des versements <strong>actifs</strong>, non définitivement supprimés, dont le
 * paiement parent n'est pas annulé, <strong>moins les remboursements</strong>. Les trois
 * exclusions sont appliquées en SQL ; les remboursements sont retirés ensuite car ils
 * vivent dans une autre table.
 *
 * <h2>Ce qui n'est pas confondu</h2>
 * L'attendu vient du {@link PaymentCostResolver} (donc {@code monthTotalCost}, réductions
 * comprises) et jamais d'un calcul local. Encaissé et attendu répondent à deux questions
 * différentes et sont calculés par deux chemins séparés (business-rules.md, audit H5).
 */
@Service
public class GroupRevenueService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupRevenueService.class);

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final GroupRepository groupRepository;
    private final SessionSeriesRepository sessionSeriesRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final RefundRepository refundRepository;
    private final PaymentCostResolver paymentCostResolver;

    public GroupRevenueService(GroupRepository groupRepository,
            SessionSeriesRepository sessionSeriesRepository,
            StudentGroupRepository studentGroupRepository,
            PaymentRepository paymentRepository,
            PaymentDetailRepository paymentDetailRepository,
            RefundRepository refundRepository,
            PaymentCostResolver paymentCostResolver) {
        this.groupRepository = groupRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.paymentRepository = paymentRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.refundRepository = refundRepository;
        this.paymentCostResolver = paymentCostResolver;
    }

    /**
     * Construit le relevé d'encaissements d'un groupe.
     *
     * @param groupId identifiant du groupe
     * @return le relevé complet (total, par série, par séance, par mois)
     * @throws CustomServiceException 404 si le groupe est introuvable
     */
    @Transactional(readOnly = true)
    public GroupRevenueDTO getGroupRevenue(Long groupId) {
        GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                .orElseThrow(() -> new CustomServiceException(
                        "Groupe introuvable pour l'identifiant : " + groupId, HttpStatus.NOT_FOUND));

        Map<Long, BigDecimal> collectedBySeries = readCollectedBySeries(groupId);
        Map<Long, BigDecimal> refundedBySeries = readRefundsBySeries(groupId);
        Map<Long, List<SessionRevenueDTO>> sessionsBySeries = readSessionsBySeries(groupId);

        List<Long> memberIds = studentGroupRepository.findByGroupIdAndActiveTrue(groupId).stream()
                .map(StudentGroupEntity::getStudent)
                .filter(Objects::nonNull)
                .map(student -> student.getId())
                .toList();

        List<SeriesRevenueDTO> seriesRevenues = new ArrayList<>();
        BigDecimal totalExpected = zero();

        BigDecimal totalRemaining = zero();
        BigDecimal totalOverpaid = zero();

        for (SessionSeriesEntity series : sessionSeriesRepository.findByGroupId(groupId)) {
            Long seriesId = series.getId();
            BigDecimal collected = collectedBySeries.getOrDefault(seriesId, zero());
            BigDecimal refunded = refundedBySeries.getOrDefault(seriesId, zero());
            BigDecimal netCollected = scale(collected.subtract(refunded));
            SeriesBalance balance = balanceForSeries(memberIds, seriesId);

            totalExpected = totalExpected.add(balance.expected());
            totalRemaining = totalRemaining.add(balance.remaining());
            totalOverpaid = totalOverpaid.add(balance.overpaid());

            seriesRevenues.add(new SeriesRevenueDTO(
                    seriesId,
                    series.getName(),
                    netCollected,
                    refunded,
                    balance.expected(),
                    balance.remaining(),
                    balance.overpaid(),
                    sessionsBySeries.getOrDefault(seriesId, List.of())));
        }

        // Total encaissé lu sur le registre des paiements, comme la ventilation par série.
        BigDecimal grossCollected = scale(nullToZero(paymentRepository.sumPaidForGroup(groupId)));
        BigDecimal totalRefunded = scale(nullToZero(refundRepository.sumRefundsForGroup(groupId)));
        BigDecimal netCollected = scale(grossCollected.subtract(totalRefunded));

        // Part encaissée qu'aucune séance ne porte encore : versement sans série rattachée, ou
        // avance sur des séances non encore tenues, que la ventilation n'a pas pu affecter.
        // Exposée plutôt que masquée — c'est précisément l'écart qui rendait le relevé
        // incohérent avec la situation individuelle des étudiants.
        BigDecimal allocatedToSessions = scale(
                toBigDecimal(paymentDetailRepository.sumCollectedForGroup(groupId)));
        BigDecimal unassigned = scale(grossCollected.subtract(allocatedToSessions));

        totalExpected = scale(totalExpected);

        return new GroupRevenueDTO(
                groupId,
                group.getName(),
                netCollected,
                totalRefunded,
                totalExpected,
                scale(totalRemaining),
                scale(totalOverpaid),
                seriesRevenues,
                readMonths(groupId),
                unassigned.signum() > 0 ? unassigned : zero());
    }

    // ------------------------------------------------------------------
    // Lectures agrégées
    // ------------------------------------------------------------------

    /**
     * Encaissé par série, lu sur le registre des paiements.
     *
     * <p>Source volontairement différente de la ventilation par séance : un versement que la
     * ventilation n'a pas pu affecter (avance sur des séances non encore tenues) reste invisible
     * dans {@code PaymentDetailEntity} alors qu'il est bien encaissé. Le lire ici garantit que
     * le relevé du groupe et la situation individuelle de l'étudiant — qui s'appuie déjà sur le
     * registre — annoncent le même montant.</p>
     */
    private Map<Long, BigDecimal> readCollectedBySeries(Long groupId) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : paymentRepository.sumPaidByGroupGroupedBySeries(groupId)) {
            result.put((Long) row[0], scale(toBigDecimal(row[1])));
        }
        return result;
    }

    private Map<Long, BigDecimal> readRefundsBySeries(Long groupId) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : refundRepository.sumRefundsByGroupGroupedBySeries(groupId)) {
            result.put((Long) row[0], scale(toBigDecimal(row[1])));
        }
        return result;
    }

    private Map<Long, List<SessionRevenueDTO>> readSessionsBySeries(Long groupId) {
        Map<Long, List<SessionRevenueDTO>> result = new HashMap<>();
        for (Object[] row : paymentDetailRepository.sumCollectedByGroupGroupedBySession(groupId)) {
            Long seriesId = (Long) row[0];
            result.computeIfAbsent(seriesId, key -> new ArrayList<>())
                    .add(new SessionRevenueDTO(
                            (Long) row[1],
                            (String) row[2],
                            (Date) row[3],
                            scale(toBigDecimal(row[4]))));
        }
        return result;
    }

    private List<MonthRevenueDTO> readMonths(Long groupId) {
        List<MonthRevenueDTO> months = new ArrayList<>();
        for (Object[] row : paymentDetailRepository.sumCollectedByGroupGroupedByMonth(groupId)) {
            months.add(new MonthRevenueDTO(
                    ((Number) row[0]).intValue(),
                    ((Number) row[1]).intValue(),
                    scale(toBigDecimal(row[2]))));
        }
        return months;
    }

    /**
     * Balance d'une série, agrégée étudiant par étudiant.
     *
     * @param expected  coût attendu total
     * @param remaining reste à recouvrer (somme des manques individuels)
     * @param overpaid  trop-perçu (somme des excédents individuels)
     */
    private record SeriesBalance(BigDecimal expected, BigDecimal remaining, BigDecimal overpaid) {
    }

    /**
     * Calcule la balance d'une série en sommant les situations individuelles.
     *
     * <p>Le reste à recouvrer était auparavant déduit du solde global de la série
     * ({@code attendu − encaissé}, borné à zéro). Cette compensation masquait la réalité :
     * un étudiant qui verse trop annulait le retard d'un autre, et la série paraissait
     * soldée. On somme donc séparément les manques et les excédents de chaque étudiant.</p>
     *
     * <p>Un étudiant dont le coût n'est pas résoluble est ignoré plutôt que de faire échouer
     * tout le relevé.</p>
     */
    private SeriesBalance balanceForSeries(List<Long> memberIds, Long seriesId) {
        BigDecimal expected = zero();
        BigDecimal remaining = zero();
        BigDecimal overpaid = zero();

        for (Long studentId : memberIds) {
            try {
                PaymentCostResolver.PaymentStatusResult status =
                        paymentCostResolver.resolve(studentId, seriesId);
                BigDecimal cost = status.monthTotalCost();
                BigDecimal paid = status.amountPaid();
                expected = expected.add(cost);

                BigDecimal balance = cost.subtract(paid);
                if (balance.signum() > 0) {
                    remaining = remaining.add(balance);
                } else if (balance.signum() < 0) {
                    overpaid = overpaid.add(balance.negate());
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Situation de paiement non résolue pour l'étudiant {} et la série {} : {}",
                        studentId, seriesId, e.getMessage());
            }
        }
        return new SeriesBalance(scale(expected), scale(remaining), scale(overpaid));
    }

    // ------------------------------------------------------------------
    // Helpers monétaires
    // ------------------------------------------------------------------

    /** Reste à encaisser, borné à zéro : un trop-perçu n'est pas une dette négative. */
    private BigDecimal remaining(BigDecimal expected, BigDecimal collected) {
        BigDecimal remaining = expected.subtract(collected);
        return remaining.signum() > 0 ? scale(remaining) : zero();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return zero();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? zero() : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
