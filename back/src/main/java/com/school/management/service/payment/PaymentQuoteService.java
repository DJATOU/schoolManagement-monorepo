package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.DiscountService;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Devis de paiement d'un étudiant pour une série : source unique du montant proposé à la
 * saisie et du plafond encaissable.
 *
 * <p>Le formulaire de saisie et le garde-fou anti-trop-perçu calculaient chacun leur coût à
 * partir du tarif catalogue, sans appliquer la réduction. Un étudiant à 65 % de réduction se
 * voyait donc proposer le plein tarif, et le contrôle serveur acceptait des versements
 * supérieurs à son dû réel. Les deux passent désormais par ce service, qui délègue le calcul
 * au {@link PaymentCostResolver} (donc au {@link PaymentCostCalculator}).</p>
 *
 * <h2>Prorata : le plafond ne dépasse plus ce que la facturation reconnaît (exigence 3)</h2>
 * Le plafond s'appuie sur {@code monthTotalCost}, qui est désormais le Coût_Série_Prorata :
 * seules les séances postérieures ou égales à l'inscription, ou effectivement suivies, sont
 * facturées. Le devis expose en plus le décompte facturable, le décompte écarté et l'excédent
 * déjà encaissé, pour que l'écran puisse justifier un montant inférieur au coût nominal.
 *
 * <p>Le décompte des séances suivies vient du {@link BillableSessionsResolver}, et non plus de
 * {@code AttendanceRepository.countPresentForStudentAndSeries} : le devis et le
 * {@link PaymentCostResolver} doivent retenir la <b>même</b> définition de séance facturable
 * (exigence 1.5), sinon le devis annonce un nombre de séances suivies incohérent avec le
 * montant dû qu'il affiche juste à côté.</p>
 */
@Service
public class PaymentQuoteService {

    private static final int MONEY_SCALE = PaymentCostCalculator.MONEY_SCALE;
    private static final RoundingMode MONEY_ROUNDING = PaymentCostCalculator.MONEY_ROUNDING;

    private final SessionSeriesRepository sessionSeriesRepository;
    private final AttendanceRepository attendanceRepository;
    private final DiscountService discountService;
    private final PaymentCostResolver paymentCostResolver;
    private final BillableSessionsResolver billableSessionsResolver;

    public PaymentQuoteService(SessionSeriesRepository sessionSeriesRepository,
            AttendanceRepository attendanceRepository,
            DiscountService discountService,
            PaymentCostResolver paymentCostResolver,
            BillableSessionsResolver billableSessionsResolver) {
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.attendanceRepository = attendanceRepository;
        this.discountService = discountService;
        this.paymentCostResolver = paymentCostResolver;
        this.billableSessionsResolver = billableSessionsResolver;
    }

    /**
     * Construit le devis d'un étudiant pour une série.
     *
     * @param studentId identifiant de l'étudiant
     * @param seriesId  identifiant de la série
     * @return le devis complet (tarifs, réduction, coûts, versements, plafond)
     * @throws CustomServiceException 404 si la série est introuvable
     */
    @Transactional(readOnly = true)
    public PaymentQuoteDTO quote(Long studentId, Long seriesId) {
        Objects.requireNonNull(studentId, "studentId ne doit pas être nul.");
        Objects.requireNonNull(seriesId, "seriesId ne doit pas être nul.");

        SessionSeriesEntity series = sessionSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new CustomServiceException(
                        "Série introuvable pour l'identifiant : " + seriesId, HttpStatus.NOT_FOUND));

        PaymentCostResolver.PaymentStatusResult status = paymentCostResolver.resolve(studentId, seriesId);

        BigDecimal grossPrice = resolveGrossPrice(series.getGroup());
        BigDecimal rate = normalizeRate(discountService.resolveRate(studentId, seriesId));
        BigDecimal netPrice = grossPrice.multiply(BigDecimal.ONE.subtract(rate))
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        // Même source de vérité que le PaymentCostResolver : le décompte des séances suivies
        // porte sur les seules séances facturables, borné à la série (exigence 1.5).
        BillableSessionsResolver.BillableSessions billable =
                billableSessionsResolver.resolve(studentId, seriesId);
        int attendedSessions = billable.attendedCount();
        boolean catchUpOnly = isCatchUpOnly(studentId, seriesId);

        // Excédent déjà encaissé au-delà du coût au prorata. Aucune reprise de données n'est
        // prévue : les séries historiquement sur-encaissées l'exposent ici, et leur plafond
        // tombe à zéro (exigence 3.5).
        BigDecimal existingExcess = notBelowZero(
                status.amountPaid().subtract(status.monthTotalCost()));

        // Plafond encaissable : le coût au prorata de la série pour un inscrit régulier
        // (il peut régler son mois d'avance), le dû à ce jour pour un rattrapage seul (il ne
        // doit que les séances effectivement suivies). Jamais négatif (exigence 3.3).
        BigDecimal ceiling = catchUpOnly ? status.amountDueSoFar() : status.monthTotalCost();
        BigDecimal maxPayable = existingExcess.signum() > 0
                ? zero()
                : notBelowZero(ceiling.subtract(status.amountPaid()));

        return new PaymentQuoteDTO(
                studentId,
                seriesId,
                // plannedSessions est déprécié et porte le décompte facturable : le coût annoncé
                // doit correspondre au nombre de séances affiché à côté de lui.
                billable.billableCount(),
                billable.billableCount(),
                billable.excludedCount(),
                attendedSessions,
                grossPrice,
                rate,
                netPrice,
                status.monthTotalCost(),
                status.amountDueSoFar(),
                status.amountPaid(),
                notBelowZero(status.monthTotalCost().subtract(status.amountPaid())),
                maxPayable,
                existingExcess,
                rate.compareTo(BigDecimal.ONE) == 0,
                catchUpOnly);
    }

    /**
     * Devis de chaque série d'un groupe pour un étudiant, dans l'ordre d'ajout des séries.
     *
     * <p>L'ordre est celui du dépôt ({@code findByGroupId} trie par identifiant croissant) : le
     * client peut donc l'utiliser tel quel sans retrier.</p>
     *
     * @param studentId identifiant de l'étudiant
     * @param groupId   identifiant du groupe
     * @return un devis par série, éventuellement vide si le groupe n'a aucune série
     */
    @Transactional(readOnly = true)
    public List<PaymentQuoteDTO> quotesForGroup(Long studentId, Long groupId) {
        Objects.requireNonNull(studentId, "studentId ne doit pas être nul.");
        Objects.requireNonNull(groupId, "groupId ne doit pas être nul.");

        return sessionSeriesRepository.findByGroupId(groupId).stream()
                .map(series -> quote(studentId, series.getId()))
                .toList();
    }

    /**
     * Montant maximal encaissable maintenant pour cet étudiant et cette série.
     *
     * <p>Utilisé par le garde-fou de saisie : au-delà, le versement crée un trop-perçu.</p>
     *
     * @param studentId identifiant de l'étudiant
     * @param seriesId  identifiant de la série
     * @return le plafond, jamais négatif
     */
    @Transactional(readOnly = true)
    public BigDecimal maxPayable(Long studentId, Long seriesId) {
        return quote(studentId, seriesId).maxPayable();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Vrai lorsque toutes les présences actives de l'étudiant sur la série sont des
     * rattrapages. Un étudiant sans aucune présence n'est pas considéré comme rattrapage :
     * il est traité comme un inscrit régulier, qui peut régler sa série d'avance.
     */
    private boolean isCatchUpOnly(Long studentId, Long seriesId) {
        List<AttendanceEntity> attendances = attendanceRepository
                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, seriesId);
        return !attendances.isEmpty()
                && attendances.stream().allMatch(a -> Boolean.TRUE.equals(a.getIsCatchUp()));
    }

    /** Tarif catalogue de la séance, 0 si le groupe ou son tarif est absent. */
    private BigDecimal resolveGrossPrice(GroupEntity group) {
        if (group == null) {
            return zero();
        }
        PricingEntity pricing = group.getPrice();
        if (pricing == null || pricing.getPrice() == null) {
            return zero();
        }
        return BigDecimal.valueOf(pricing.getPrice()).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal normalizeRate(BigDecimal rate) {
        return rate == null ? BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING) : rate;
    }

    private BigDecimal notBelowZero(BigDecimal value) {
        return value.signum() > 0 ? value.setScale(MONEY_SCALE, MONEY_ROUNDING) : zero();
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
