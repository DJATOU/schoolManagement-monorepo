package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailSearchDTO;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.RefundRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Déduit les remboursements des lignes de l'écran « Gestion des paiements » (décision du
 * propriétaire produit : après un remboursement, on déduit ce qui a été rendu).
 *
 * <h2>Pourquoi une imputation et non une colonne</h2>
 * Un remboursement porte sur le <strong>versement</strong> d'une série ({@code refund.payment}),
 * jamais sur une ligne : il n'existe aucun « montant remboursé de la ligne » stocké. Pour afficher
 * un montant net par ligne, le remboursement est imputé aux lignes du versement <strong>les plus
 * récentes d'abord</strong>. C'est la même règle que l'historique de l'étudiant, dont la couverture
 * est plafonnée au versé net : l'argent rendu y découvre les dernières séances. Les deux écrans
 * montrent donc la même séance découverte.
 *
 * <h2>Le statut affiché devient celui du net</h2>
 * Le statut stocké ({@code payments.status}) est calculé sur le cumul brut : un versement soldé puis
 * remboursé restait « Soldé ». Pour les seuls versements remboursés, le statut est recalculé sur le
 * net avec la règle du statut stocké ({@link PaymentLineStatus}) ; un versement entièrement rendu
 * s'annonce « Remboursé ». Un versement annulé garde son statut : c'est l'information qui prime.
 *
 * <p>Rien n'est écrit : c'est une lecture, et l'annotation n'est faite que pour l'affichage.</p>
 */
@Component
public class PaymentLineRefundAnnotator {

    /** Versement dont tout le versé a été rendu. Statut d'affichage, jamais stocké. */
    public static final String REFUNDED = "REFUNDED";

    private static final String CANCELLED = "CANCELLED";
    private static final int MONEY_SCALE = 2;

    /**
     * Ordre chronologique des lignes, identique à celui de l'historique de l'étudiant : date de
     * versement (les lignes sans date en dernier, donc tenues pour les plus récentes), puis
     * identifiant. Il est parcouru à l'envers pour imputer un remboursement.
     */
    private static final Comparator<Line> CHRONOLOGICAL =
            Comparator.comparing(Line::paymentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Line::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private final RefundRepository refundRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentCostResolver paymentCostResolver;

    public PaymentLineRefundAnnotator(RefundRepository refundRepository,
                                      PaymentDetailRepository paymentDetailRepository,
                                      PaymentCostResolver paymentCostResolver) {
        this.refundRepository = refundRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.paymentCostResolver = paymentCostResolver;
    }

    /** Une ligne active d'un versement, réduite à ce qu'il faut pour imputer un remboursement. */
    private record Line(Long id, BigDecimal amount, Date paymentDate) {
    }

    /**
     * Renseigne, sur chaque ligne de la page, sa part remboursée, son montant net, le total remboursé
     * de son versement et, si ce versement a été remboursé, son statut net.
     *
     * <p>L'imputation porte sur <strong>toutes</strong> les lignes actives du versement, pas sur les
     * seules lignes de la page : une page filtrée ou paginée ne doit pas déplacer la part remboursée
     * d'une ligne à l'autre.</p>
     */
    public void annotate(List<PaymentDetailSearchDTO> rows) {
        rows.forEach(row -> {
            row.setRefundedAmount(zero());
            row.setNetAmount(money(row.getAmountPaid()));
            row.setPaymentRefunded(zero());
        });

        Set<Long> paymentIds = rows.stream()
                .map(PaymentDetailSearchDTO::getPaymentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (paymentIds.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> refundedByPayment = refundedByPayment(paymentIds);
        if (refundedByPayment.isEmpty()) {
            return;
        }

        Map<Long, List<Line>> linesByPayment = linesByPayment(refundedByPayment.keySet());
        Map<Long, BigDecimal> shareByLine = new HashMap<>();
        refundedByPayment.forEach((paymentId, refunded) ->
                shareByLine.putAll(spread(refunded, linesByPayment.getOrDefault(paymentId, List.of()))));

        Map<Long, String> statusByPayment = new HashMap<>();
        for (PaymentDetailSearchDTO row : rows) {
            BigDecimal refunded = refundedByPayment.get(row.getPaymentId());
            if (refunded == null) {
                continue;
            }
            BigDecimal share = shareByLine.getOrDefault(row.getId(), zero());
            row.setPaymentRefunded(refunded);
            row.setRefundedAmount(share);
            // Jamais négatif : la part d'une ligne est bornée à son montant (voir spread).
            row.setNetAmount(money(row.getAmountPaid()).subtract(share));
            if (!CANCELLED.equals(row.getPaymentStatus())) {
                row.setPaymentStatus(statusByPayment.computeIfAbsent(row.getPaymentId(),
                        paymentId -> netStatus(row, linesByPayment.getOrDefault(paymentId, List.of()), refunded)));
            }
        }
    }

    /** Remboursements strictement positifs, par versement. */
    private Map<Long, BigDecimal> refundedByPayment(Collection<Long> paymentIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : refundRepository.sumActiveRefundsByPayment(paymentIds)) {
            BigDecimal refunded = money(toBigDecimal(row[1]));
            if (refunded.signum() > 0) {
                result.put((Long) row[0], refunded);
            }
        }
        return result;
    }

    private Map<Long, List<Line>> linesByPayment(Collection<Long> paymentIds) {
        Map<Long, List<Line>> result = new HashMap<>();
        for (Object[] row : paymentDetailRepository.findActiveLinesOfPayments(paymentIds)) {
            result.computeIfAbsent((Long) row[0], key -> new ArrayList<>())
                    .add(new Line((Long) row[1], money(toBigDecimal(row[2])), (Date) row[3]));
        }
        return result;
    }

    /**
     * Impute un remboursement aux lignes, de la plus récente à la plus ancienne, chacune à hauteur de
     * son montant. Le reliquat éventuel — remboursement supérieur aux lignes encore actives — n'est
     * imputé nulle part : aucune ligne ne passe sous zéro.
     */
    private Map<Long, BigDecimal> spread(BigDecimal refunded, List<Line> lines) {
        Map<Long, BigDecimal> shares = new HashMap<>();
        BigDecimal remaining = refunded;
        List<Line> latestFirst = lines.stream().sorted(CHRONOLOGICAL.reversed()).toList();
        for (Line line : latestFirst) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal share = remaining.min(line.amount());
            shares.put(line.id(), share);
            remaining = remaining.subtract(share);
        }
        return shares;
    }

    /**
     * Statut d'un versement remboursé, sur son net : versé de ses lignes actives moins le remboursé.
     * Rien ne reste : « Remboursé ». Sinon la règle du statut stocké, contre le coût au prorata de la
     * série ; un coût introuvable ne rend jamais « Soldé ».
     */
    private String netStatus(PaymentDetailSearchDTO row, List<Line> lines, BigDecimal refunded) {
        BigDecimal paid = lines.stream().map(Line::amount).reduce(zero(), BigDecimal::add);
        BigDecimal net = paid.subtract(refunded).max(zero());
        if (net.signum() == 0) {
            return REFUNDED;
        }
        return PaymentLineStatus.of(net, seriesCost(row));
    }

    private Optional<BigDecimal> seriesCost(PaymentDetailSearchDTO row) {
        if (row.getStudentId() == null || row.getSeriesId() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(paymentCostResolver
                    .calculatorFor(row.getStudentId(), row.getSeriesId())
                    .monthTotalCost());
        } catch (RuntimeException e) {
            // Série introuvable ou tarif invalide : le statut reste « en cours », jamais « soldé ».
            return Optional.empty();
        }
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

    private static BigDecimal money(Double value) {
        return money(value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }
}
