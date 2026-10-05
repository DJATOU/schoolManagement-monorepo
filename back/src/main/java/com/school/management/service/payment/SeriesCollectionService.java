package com.school.management.service.payment;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ce qu'une série a réellement encaissé : la source unique de l'Encaissé_Net.
 *
 * <h2>Pourquoi un service</h2>
 * Deux écrans annoncent ce montant : le relevé de recettes du groupe et la Paie des enseignants, dont
 * il est la base (spec teacher-payroll, exigence 3.3). Calculé deux fois, il finirait par différer
 * d'un centime ou d'une exclusion, et l'enseignant serait payé sur un chiffre que le relevé contredit.
 *
 * <h2>Ce que « encaissé » signifie</h2>
 * Le registre des paiements ({@code payments.amount_paid}) des paiements non annulés, tous élèves
 * confondus, rattrapages compris, moins les remboursements actifs de la série. Le registre et non la
 * ventilation par séance : une avance que la ventilation n'a pas pu affecter est bien encaissée.
 */
@Service
public class SeriesCollectionService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public SeriesCollectionService(PaymentRepository paymentRepository, RefundRepository refundRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    /**
     * Encaissé d'une série.
     *
     * @param gross    versé au registre, paiements annulés exclus
     * @param refunded remboursements actifs
     * @param net      {@code gross − refunded}
     */
    public record SeriesCollection(BigDecimal gross, BigDecimal refunded, BigDecimal net) {

        static SeriesCollection of(BigDecimal gross, BigDecimal refunded) {
            BigDecimal g = scale(gross);
            BigDecimal r = scale(refunded);
            return new SeriesCollection(g, r, g.subtract(r));
        }

        /** Rien d'encaissé ni de rendu. */
        public static SeriesCollection none() {
            return of(BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    /**
     * Encaissé de chaque série d'un groupe, en deux requêtes. Une série sans aucun versement ni
     * remboursement est absente de la table : c'est {@link SeriesCollection#none()}.
     */
    @Transactional(readOnly = true)
    public Map<Long, SeriesCollection> ofGroup(Long groupId) {
        Objects.requireNonNull(groupId, "groupId");
        Map<Long, BigDecimal> gross = sums(paymentRepository.sumPaidByGroupGroupedBySeries(groupId));
        Map<Long, BigDecimal> refunded = sums(refundRepository.sumRefundsByGroupGroupedBySeries(groupId));

        Map<Long, SeriesCollection> result = new HashMap<>();
        gross.keySet().forEach(seriesId -> result.put(seriesId, null));
        refunded.keySet().forEach(seriesId -> result.put(seriesId, null));
        result.replaceAll((seriesId, ignored) -> SeriesCollection.of(
                gross.getOrDefault(seriesId, BigDecimal.ZERO),
                refunded.getOrDefault(seriesId, BigDecimal.ZERO)));
        return result;
    }

    /**
     * Encaissé d'une série, lu par {@link #ofGroup} : la paie et le relevé partagent la requête, pas
     * seulement la règle.
     */
    @Transactional(readOnly = true)
    public SeriesCollection of(SessionSeriesEntity series) {
        Objects.requireNonNull(series, "series");
        GroupEntity group = series.getGroup();
        if (group == null || group.getId() == null) {
            return SeriesCollection.none();
        }
        return ofGroup(group.getId()).getOrDefault(series.getId(), SeriesCollection.none());
    }

    private static Map<Long, BigDecimal> sums(Iterable<Object[]> rows) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], toBigDecimal(row[1]));
        }
        return result;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
