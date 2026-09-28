package com.school.management.service.payment;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.DiscountService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Couche de câblage entre les entités/repositories et le {@link PaymentCostCalculator}
 * (calculateur pur). Sa responsabilité est de transformer un couple
 * {@code (studentId, seriesId)} en un {@link PaymentCostCalculator} construit à partir
 * des quatre valeurs résolues (séances planifiées, séances présentes, prix par séance,
 * taux de réduction), puis de répondre aux questions de statut (requirement 4.2).
 *
 * <p>Le calculateur reste pur : toute résolution d'entités (lecture du prix via
 * {@code group.getPrice().getPrice()}, décompte des séances facturables et suivies, agrégation
 * des paiements et remboursements) est effectuée ici, sous transaction en lecture seule car des
 * relations paresseuses (lazy) sont traversées.</p>
 *
 * <h2>Prorata : les deux décomptes viennent du même ensemble de séances (exigences 2.1, 2.2)</h2>
 * Les séances planifiées passées au calculateur ne sont plus {@code series.getTotalSessions()}
 * mais le décompte des <b>séances facturables</b> fourni par le {@link BillableSessionsResolver} :
 * une séance tenue avant l'arrivée de l'étudiant dans le groupe et à laquelle il n'a pas assisté
 * ne lui est pas due. {@code monthTotalCost} devient ainsi le Coût_Série_Prorata sans qu'une
 * ligne du calculateur ne change.
 *
 * <p>Le décompte des séances suivies vient du <b>même</b> résolveur, et non plus de
 * {@code AttendanceRepository.countPresentForStudentAndSeries} : les deux décomptes doivent
 * porter sur le même ensemble de séances, sans quoi l'invariant
 * {@code amountDueSoFar ≤ monthTotalCost} (exigence 2.4) peut être violé sur des données
 * limites. Le décompte reste <b>borné à la série</b> — l'unité de facturation est la série,
 * jamais une plage de dates ni une agrégation entre groupes.</p>
 *
 * <h2>Montant versé effectif (requirement 5, 13.3)</h2>
 * Le montant versé effectif est {@code somme(paiements non annulés) − somme(remboursements)}.
 * Les deux agrégats proviennent de requêtes {@code COALESCE(..., 0)} ; on garde néanmoins un
 * garde-fou null → {@link BigDecimal#ZERO}. Un remboursement ne devrait jamais dépasser les
 * paiements ; par sécurité, si la soustraction est négative, on ramène à zéro pour rester
 * compatible avec le calculateur (qui rejette un montant versé négatif).
 */
@Service
public class PaymentCostResolver {

    private static final int MONEY_SCALE = PaymentCostCalculator.MONEY_SCALE;
    private static final RoundingMode MONEY_ROUNDING = PaymentCostCalculator.MONEY_ROUNDING;

    private final SessionSeriesRepository sessionSeriesRepository;
    private final BillableSessionsResolver billableSessionsResolver;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final DiscountService discountService;

    public PaymentCostResolver(SessionSeriesRepository sessionSeriesRepository,
                               BillableSessionsResolver billableSessionsResolver,
                               PaymentRepository paymentRepository,
                               RefundRepository refundRepository,
                               DiscountService discountService) {
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.billableSessionsResolver = billableSessionsResolver;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.discountService = discountService;
    }

    /**
     * Résultat de statut de paiement pour un étudiant et une série.
     *
     * @param monthTotalCost  coût total du mois (après réduction)
     * @param amountDueSoFar  montant dû à ce jour (après réduction), seuil de retard
     * @param amountPaid      montant versé effectif (paiements non annulés − remboursements)
     * @param late            l'étudiant est-il en retard ({@code amountPaid < amountDueSoFar})
     * @param monthFullyPaid  le mois est-il soldé ({@code amountPaid >= monthTotalCost})
     */
    public record PaymentStatusResult(
            BigDecimal monthTotalCost,
            BigDecimal amountDueSoFar,
            BigDecimal amountPaid,
            boolean late,
            boolean monthFullyPaid) {}

    /**
     * Construit le {@link PaymentCostCalculator} pour un étudiant et une série.
     *
     * <p>Étapes de résolution :</p>
     * <ol>
     *   <li>charge la série (404 si introuvable) ;</li>
     *   <li>{@code plannedSessions = billable.billableCount()} — le décompte au prorata, et non
     *       plus {@code series.getTotalSessions()} (exigence 2.1) ;</li>
     *   <li>{@code pricePerSession} = prix du groupe (Double) converti en {@link BigDecimal},
     *       null → 0, échelle 2 ;</li>
     *   <li>{@code attendedSessions = billable.attendedCount()} — les présences parmi les seules
     *       séances facturables, borné à la série (exigence 2.2) ;</li>
     *   <li>{@code discountRate} = taux résolu par {@link DiscountService} ;</li>
     *   <li>construit le calculateur, en traduisant toute {@link IllegalArgumentException} en
     *       erreur de validation domaine (HTTP 400).</li>
     * </ol>
     *
     * @param studentId identifiant de l'étudiant
     * @param seriesId  identifiant de la série
     * @return le calculateur construit
     * @throws CustomServiceException 404 si la série est introuvable, 400 si les entrées
     *                                sont invalides
     */
    @Transactional(readOnly = true)
    public PaymentCostCalculator calculatorFor(Long studentId, Long seriesId) {
        SessionSeriesEntity series = sessionSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new CustomServiceException(
                        "Série introuvable pour l'identifiant : " + seriesId,
                        HttpStatus.NOT_FOUND));

        // Les deux décomptes proviennent du même résolveur, donc du même ensemble de séances :
        // les séances suivies sont un sous-ensemble des facturables (exigences 2.1, 2.2, 2.4).
        BillableSessions billable = billableSessionsResolver.resolve(studentId, seriesId);
        int plannedSessions = billable.billableCount();
        int attendedSessions = billable.attendedCount();

        BigDecimal pricePerSession = resolvePricePerSession(series.getGroup());
        BigDecimal discountRate = discountService.resolveRate(studentId, seriesId);

        try {
            return new PaymentCostCalculator(
                    plannedSessions, attendedSessions, pricePerSession, discountRate);
        } catch (IllegalArgumentException e) {
            throw new CustomServiceException(
                    "Entrées de calcul de coût invalides : " + e.getMessage(),
                    e, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Résout le statut de paiement complet pour un étudiant et une série.
     *
     * @param studentId identifiant de l'étudiant
     * @param seriesId  identifiant de la série
     * @return le {@link PaymentStatusResult} (coûts, montant versé effectif, statuts)
     * @throws CustomServiceException 404 si la série est introuvable, 400 si les entrées
     *                                sont invalides
     */
    @Transactional(readOnly = true)
    public PaymentStatusResult resolve(Long studentId, Long seriesId) {
        PaymentCostCalculator calc = calculatorFor(studentId, seriesId);

        BigDecimal paid = nullToZero(
                paymentRepository.sumAmountPaidForStudentAndSeries(studentId, seriesId));
        BigDecimal refunds = nullToZero(
                refundRepository.sumRefundsForStudentAndSeries(studentId, seriesId));

        BigDecimal effectivePaid = paid.subtract(refunds).setScale(MONEY_SCALE, MONEY_ROUNDING);
        // Un remboursement ne devrait pas dépasser les paiements ; par sécurité, on ramène
        // à zéro pour rester compatible avec le calculateur (qui rejette un versement négatif).
        if (effectivePaid.signum() < 0) {
            effectivePaid = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        boolean late = calc.isLate(effectivePaid);
        boolean monthFullyPaid = calc.isMonthFullyPaid(effectivePaid);

        return new PaymentStatusResult(
                calc.monthTotalCost(),
                calc.amountDueSoFar(),
                effectivePaid,
                late,
                monthFullyPaid);
    }

    // ------------------------------------------------------------------
    // Helpers privés
    // ------------------------------------------------------------------

    /**
     * Résout le prix par séance depuis le groupe de la série. Tout maillon null
     * (groupe, prix, valeur du prix) est traité comme un prix de zéro. Le résultat
     * est normalisé à l'échelle 2, HALF_UP.
     */
    private BigDecimal resolvePricePerSession(GroupEntity group) {
        if (group == null) {
            return zero();
        }
        PricingEntity pricing = group.getPrice();
        if (pricing == null || pricing.getPrice() == null) {
            return zero();
        }
        return BigDecimal.valueOf(pricing.getPrice()).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? zero() : value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
