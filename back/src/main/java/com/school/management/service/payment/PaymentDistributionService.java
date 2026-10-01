package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ventilation d'une Imputation sur les séances de sa série : c'est ce service qui crée les
 * {@code payment_detail}.
 *
 * <h2>Les séances candidates viennent du résolveur partagé (exigences 4.5, 1.5)</h2>
 * Les candidates proviennent du {@link BillableSessionsResolver}, seule définition de la séance
 * facturable dans le projet. Une séance tenue avant l'arrivée de l'étudiant, et à laquelle il n'a
 * pas assisté, ne reçoit donc rien. Sa liste est déjà en ordre chronologique : la ventilation
 * s'appuie sur cet ordre sans le reconstituer.
 *
 * <h2>Une ligne par (Encaissement, séance) — spec admin-corrections, D3</h2>
 * Chaque ligne créée est une part d'une seule Imputation, donc d'un seul Encaissement
 * (exigence 1.3). Une ligne existante n'est jamais complétée : complétée, elle mêlait l'argent de
 * deux versements, et annuler l'un aurait retiré l'autre de la ventilation. Une séance peut ainsi
 * porter plusieurs lignes, une par Encaissement.
 *
 * <h2>Plafond au prix net (exigence 1.6)</h2>
 * Une séance ne reçoit que ce qui lui reste dû : son prix net, réduction comprise, moins ce que
 * ses lignes actives portent déjà, tous Encaissements confondus. Le tarif catalogue laissait un
 * étudiant réduit « payer » une séance au-delà de ce qu'il doit, et la séance suivante restait à
 * découvert dans la ventilation.
 *
 * <h2>Une ligne inactive ne bloque plus rien</h2>
 * Une ligne désactivée, ou supprimée définitivement par l'administrateur, ne compte pas dans la
 * part déjà ventilée et n'empêche aucun versement futur. La suppression définitive levait une
 * erreur 500 au versement suivant sur la séance, qui ne pouvait plus jamais être payée.
 */
@Service
public class PaymentDistributionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentDistributionService.class);

    private final SessionRepository sessionRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final AttendanceRepository attendanceRepository;

    /** Source du prix net et du plafond encaissable, réduction appliquée. */
    private final PaymentQuoteService paymentQuoteService;

    /** Source unique des séances facturables : la ventilation ne sort pas de cet ensemble. */
    private final BillableSessionsResolver billableSessionsResolver;

    public PaymentDistributionService(
            SessionRepository sessionRepository,
            PaymentDetailRepository paymentDetailRepository,
            AttendanceRepository attendanceRepository,
            PaymentQuoteService paymentQuoteService,
            BillableSessionsResolver billableSessionsResolver) {
        this.sessionRepository = sessionRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.attendanceRepository = attendanceRepository;
        this.paymentQuoteService = paymentQuoteService;
        this.billableSessionsResolver = billableSessionsResolver;
    }

    /**
     * Ventile une Imputation sur les séances facturables de sa série, dans l'ordre chronologique.
     *
     * <p>Chaque ligne créée porte l'Imputation, le montant de la part, et la date de
     * l'Encaissement. Un reliquat que plus aucune séance ne peut recevoir n'est pas perdu : il
     * reste compté dans le cumul de la série, qui est la somme des Imputations, et il est signalé
     * dans les journaux. Le plan d'imputation le rend exceptionnel : il plafonne déjà chaque série
     * au coût au prorata, c'est-à-dire au prix net de ses séances facturables.</p>
     *
     * @param allocation Imputation enregistrée, rattachée à sa ligne de paiement, sa série et son
     *                   Encaissement
     * @return les lignes créées, dans l'ordre des séances
     */
    @Transactional
    public List<PaymentDetailEntity> distribute(EncashmentAllocationEntity allocation) {
        Objects.requireNonNull(allocation, "allocation");
        PaymentEntity payment = Objects.requireNonNull(allocation.getPayment(), "allocation.payment");
        Long studentId = payment.getStudent().getId();
        Long seriesId = allocation.getSeries().getId();
        Date receivedAt = allocation.getEncashment().getReceivedAt();
        BigDecimal remaining = money(allocation.getAmount());

        LOGGER.info("Ventilation de l'imputation {} : {} DA sur la série {}", allocation.getId(),
                remaining.toPlainString(), seriesId);

        List<AttendanceEntity> attendances = attendanceRepository
                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, seriesId);
        Set<Long> catchUpSessionIds = catchUpSessionIdsOf(attendances);

        // Le mode « rattrapage » ne s'applique qu'à l'étudiant dont TOUTES les présences sur la
        // série sont des rattrapages — même critère que PaymentQuoteService.isCatchUpOnly, qui
        // fixe le plafond encaissable.
        //
        // Le test précédent était « l'étudiant a au moins une présence », ce qui basculait un
        // inscrit régulier en mode rattrapage dès sa première séance suivie. Sa ventilation se
        // limitait alors aux séances déjà suivies, alors que son plafond, lui, couvre la série
        // entière : régler son mois d'avance laissait la différence non ventilée.
        boolean catchUpOnly = !attendances.isEmpty()
                && attendances.stream().allMatch(a -> Boolean.TRUE.equals(a.getIsCatchUp()));

        List<SessionEntity> billable = billableSessionsResolver.resolve(studentId, seriesId).billable();
        // Le rattrapage pur ne doit que les séances qu'il est venu rattraper.
        List<SessionEntity> sessions = catchUpOnly
                ? billable.stream().filter(session -> catchUpSessionIds.contains(session.getId())).toList()
                : billable;

        List<PaymentDetailEntity> created = new ArrayList<>();
        if (sessions.isEmpty()) {
            LOGGER.warn("Aucune séance facturable pour l'étudiant {} sur la série {} : imputation {} non ventilée",
                    studentId, seriesId, allocation.getId());
            return created;
        }

        BigDecimal netPrice = money(paymentQuoteService.netPricePerSession(studentId, seriesId));
        if (netPrice.signum() <= 0) {
            // Étudiant exempté : aucune séance ne lui doit rien, il n'y a rien à ventiler.
            LOGGER.warn("Prix net nul pour l'étudiant {} sur la série {} : imputation {} non ventilée",
                    studentId, seriesId, allocation.getId());
            return created;
        }

        for (SessionEntity session : sessions) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal alreadyVentilated = money(BigDecimal.valueOf(nullToZero(
                    paymentDetailRepository.sumActiveAmountForPaymentAndSession(payment.getId(), session.getId()))));
            BigDecimal owed = netPrice.subtract(alreadyVentilated);
            if (owed.signum() <= 0) {
                continue;
            }
            BigDecimal part = remaining.min(owed);

            created.add(paymentDetailRepository.save(PaymentDetailEntity.builder()
                    .payment(payment)
                    .session(session)
                    .amountPaid(part.doubleValue())
                    .paymentDate(receivedAt)
                    .isCatchUp(catchUpSessionIds.contains(session.getId()))
                    .encashmentAllocation(allocation)
                    .build()));
            remaining = remaining.subtract(part);
        }

        if (remaining.signum() > 0) {
            // Le plan plafonne chaque série à son coût au prorata ; un reliquat signale donc une
            // ventilation en écart avec le cumul (ligne modifiée ou séance dévalidée) : à vérifier.
            LOGGER.warn("Imputation {} : {} DA non ventilés sur la série {}, toutes les séances facturables "
                    + "étant déjà couvertes au prix net de {} DA. Le montant reste compté dans le cumul.",
                    allocation.getId(), remaining.toPlainString(), seriesId, netPrice.toPlainString());
        }

        return created;
    }

    /** Séances couvertes par une présence de rattrapage. */
    private static Set<Long> catchUpSessionIdsOf(List<AttendanceEntity> attendances) {
        Set<Long> ids = new HashSet<>();
        for (AttendanceEntity attendance : attendances) {
            SessionEntity session = attendance.getSession();
            if (Boolean.TRUE.equals(attendance.getIsCatchUp()) && session != null && session.getId() != null) {
                ids.add(session.getId());
            }
        }
        return ids;
    }

    // calculateAttendedSessionsCost (statut du rattrapage : présences × tarif catalogue) a été
    // retiré avec A.4 (spec admin-corrections) : le rattrapage passe par EncashmentService, dont le
    // statut se compare au coût au prorata, réduction comprise, comme pour tout versement.

    /**
     * Refuse un versement nul ou négatif, avec un message nommant la cause réelle.
     *
     * <h2>Le plafond n'est plus de son ressort (exigence 4.6)</h2>
     * Cette méthode refusait tout montant supérieur au plafond de la série. Ce refus a disparu :
     * dépasser le montant dû d'une série n'est plus une erreur mais un <strong>report</strong> sur
     * les séries suivantes, et l'autorité du plafond appartient désormais au
     * {@link PaymentAllocationService}, seul à connaître la chaîne complète des séries et donc le
     * maximum réellement encaissable. Le conserver ici aurait refusé le versement avant même que
     * le report ne soit envisagé.
     *
     * <p>Le refus du montant nul ou négatif, lui, <strong>reste</strong>, ainsi que ses messages
     * contextuels : c'est le seul contrôle que le devis d'une série suffit à porter.</p>
     *
     * @param studentId       identifiant de l'étudiant
     * @param sessionSeriesId identifiant de la série
     * @param newAmount       montant du versement à enregistrer
     * @return {@code true} si le versement est acceptable
     * @throws CustomServiceException 400 si le montant est nul ou négatif
     */
    public boolean canProcessPayment(Long studentId, Long sessionSeriesId, double newAmount) {
        PaymentQuoteDTO quote = paymentQuoteService.quote(studentId, sessionSeriesId);
        BigDecimal amount = BigDecimal.valueOf(newAmount)
                .setScale(PaymentCostCalculator.MONEY_SCALE, PaymentCostCalculator.MONEY_ROUNDING);

        LOGGER.debug("Payment validation - already paid: {}, new amount: {}, max payable: {}",
                quote.amountPaid(), amount, quote.maxPayable());

        // Un versement nul ou négatif n'encaisse rien : il ne crée qu'une ligne de paiement
        // vide et un reçu qui n'atteste d'aucune somme. Le cas se présente dès que le plafond
        // encaissable vaut 0 (série soldée, étudiant exempté, ou série sans tarif).
        //
        // Ce contrôle est explicite et non déclaratif à dessein : les annotations
        // jakarta.validation du projet ne sont pas appliquées, faute de provider Jakarta sur
        // le classpath (hibernate-validator 6.2 est épinglé, or il implémente javax.validation).
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomServiceException(nonPositiveAmountMessage(quote), HttpStatus.BAD_REQUEST);
        }

        return true;
    }

    /**
     * Explique le refus d'un versement nul ou négatif en fonction de la situation réelle.
     *
     * <p>« Le montant doit être positif » est exact mais peu utile : dans la plupart des cas
     * l'administrateur a saisi 0 parce qu'il n'y avait de toute façon rien à encaisser. Le
     * message nomme donc la cause — exemption, série soldée — plutôt que le symptôme.</p>
     */
    private String nonPositiveAmountMessage(PaymentQuoteDTO quote) {
        if (quote.exempted()) {
            return "Cet étudiant est exempté : aucun montant n'est dû pour cette série.";
        }
        if (quote.maxPayable().compareTo(BigDecimal.ZERO) <= 0) {
            return "Cette série est déjà soldée : il n'y a plus rien à encaisser.";
        }
        return String.format(
                "Le montant à encaisser doit être supérieur à 0 DA (reste à payer : %s DA).",
                quote.maxPayable().stripTrailingZeros().toPlainString());
    }

    private static BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount)
                .setScale(PaymentCostCalculator.MONEY_SCALE, PaymentCostCalculator.MONEY_ROUNDING);
    }

    private static double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
