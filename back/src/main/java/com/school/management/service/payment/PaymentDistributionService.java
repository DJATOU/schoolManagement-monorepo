package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Ventilation d'un montant imputé sur les séances d'une série : c'est ce service qui crée les
 * {@code payment_detail}.
 *
 * <h2>Les séances candidates viennent du résolveur partagé (exigences 4.5, 1.5)</h2>
 * La liste des séances était lue directement sur la série entière
 * ({@code sessionRepository.findBySessionSeriesId}). Une séance tenue <strong>avant</strong>
 * l'arrivée de l'étudiant dans le groupe et à laquelle il n'a pas assisté recevait donc une
 * affectation, alors que la facturation ne la reconnaît pas : le versement paraissait couvrir
 * des séances non dues, et le reliquat réel restait invisible.
 *
 * <p>Les candidates proviennent désormais du {@link BillableSessionsResolver}, seule définition
 * de la séance facturable dans le projet. Sa liste est déjà en ordre chronologique : la
 * ventilation s'appuie sur cet ordre sans le reconstituer.</p>
 */
@Service
public class PaymentDistributionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentDistributionService.class);

    private final SessionRepository sessionRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final AttendanceRepository attendanceRepository;

    /** Source du plafond encaissable, réduction appliquée. */
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

    @Transactional
    public void distributePayment(PaymentEntity payment, Long sessionSeriesId, double amountPaid) {
        LOGGER.info("Distributing payment {} of amount {} for series {}",
                payment.getId(), amountPaid, sessionSeriesId);

        Long studentId = payment.getStudent().getId();

        List<AttendanceEntity> existingAttendances = attendanceRepository
                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, sessionSeriesId);

        // Le mode « rattrapage » ne s'applique qu'à l'étudiant dont TOUTES les présences sur la
        // série sont des rattrapages — même critère que PaymentQuoteService.isCatchUpOnly, qui
        // fixe le plafond encaissable.
        //
        // Le test précédent était « l'étudiant a au moins une présence », ce qui basculait un
        // inscrit régulier en mode rattrapage dès sa première séance suivie. Sa ventilation se
        // limitait alors aux séances déjà suivies, alors que son plafond, lui, couvre la série
        // entière : régler son mois d'avance laissait la différence non ventilée, invisible dans
        // le relevé du groupe et dans l'historique de la série.
        boolean catchUpOnly = !existingAttendances.isEmpty()
                && existingAttendances.stream().allMatch(a -> Boolean.TRUE.equals(a.getIsCatchUp()));

        // Séances facturables de l'étudiant pour cette série : les séances antérieures à son
        // inscription et non assistées sont écartées ici, et ne peuvent donc plus recevoir
        // d'affectation (exigences 4.5, 1.5). L'ordre chronologique est celui du résolveur.
        List<SessionEntity> billable = billableSessionsResolver
                .resolve(studentId, sessionSeriesId)
                .billable();

        List<SessionEntity> sessions;
        if (catchUpOnly) {
            LOGGER.info("Student {} only has catch-up attendances ({}) - using CATCH-UP distribution mode",
                    studentId, existingAttendances.size());
            // Le rattrapage pur ne doit que les séances qu'il est venu rattraper : on restreint
            // les facturables aux séances couvertes par une présence de rattrapage.
            sessions = filterOnCatchUpAttendance(billable, existingAttendances);
        } else {
            LOGGER.info("Student {} is a regular member - using NORMAL distribution mode over its billable sessions",
                    studentId);
            sessions = billable;
        }

        if (sessions.isEmpty()) {
            LOGGER.warn("No billable session found for student {} on series {}", studentId, sessionSeriesId);
            return;
        }

        double remaining = amountPaid;
        double pricePerSession = payment.getGroup().getPrice().getPrice();

        LOGGER.debug("Price per session: {}, Total sessions: {}", pricePerSession, sessions.size());

        for (SessionEntity session : sessions) {
            if (remaining <= 0) {
                LOGGER.debug("No remaining amount, stopping distribution");
                break;
            }

            remaining = distributeToSession(payment, session, pricePerSession, remaining);
        }

        // Un excédent est signalé dans les journaux, plus levé en exception. Le code précédent
        // levait une CustomServiceException porteuse d'un HttpStatus.OK pour transmettre un
        // message de succès : étant une RuntimeException dans une méthode @Transactional, elle
        // annulait en réalité tout le paiement tout en annonçant qu'il avait été « complété ».
        //
        // Le montant ventilé est de toute façon plafonné en amont par le plan d'allocation,
        // série par série ; ce cas ne devrait donc pas se produire. S'il survient, il signale
        // une incohérence à investiguer
        // et non une opération à faire échouer. À noter que ce coût ignore la réduction de
        // l'étudiant, contrairement à PaymentCostCalculator : il ne sert qu'à cette alerte.
        double planCost = calculateTotalCost(payment.getGroup());
        double surplus = payment.getAmountPaid() - planCost;
        if (surplus > 0) {
            LOGGER.warn("Paiement {} : cumul {} supérieur au coût planifié du groupe {} (excédent {}). "
                    + "À vérifier : réduction, séances supplémentaires ou trop-perçu.",
                    payment.getId(), payment.getAmountPaid(), planCost, surplus);
        }

        LOGGER.info("Payment distribution completed. Remaining: {}", remaining);
    }

    private double distributeToSession(PaymentEntity payment, SessionEntity session,
            double pricePerSession, double remaining) {
        Optional<PaymentDetailEntity> existingDetail = paymentDetailRepository
                .findByPaymentIdAndSessionId(payment.getId(), session.getId());

        if (existingDetail.isPresent()) {
            PaymentDetailEntity detail = existingDetail.get();

            // CRITICAL: Check if payment was permanently deleted (irreversible)
            if (detail.getPermanentlyDeleted() != null && detail.getPermanentlyDeleted()) {
                throw new IllegalStateException(
                        "Cannot create a new payment for session " + session.getId() +
                                ". This session had a payment that was permanently deleted (irreversible). " +
                                "Payment detail ID: " + detail.getId());
            }

            // CRITICAL: Ignore PaymentDetails from CANCELLED payments
            if ("CANCELLED".equals(detail.getPayment().getStatus())) {
                LOGGER.debug("Ignoring PaymentDetail from CANCELLED payment for session {}", session.getId());
                // Treat as if no detail exists - will create new one below
                double amountForThisSession = Math.min(pricePerSession, remaining);
                PaymentDetailEntity newDetail = PaymentDetailEntity.builder()
                        .payment(payment)
                        .session(session)
                        .amountPaid(amountForThisSession)
                        .build();
                paymentDetailRepository.save(Objects.requireNonNull(newDetail));
                remaining -= amountForThisSession;
                LOGGER.debug("Created new PaymentDetail for session {} (ignored CANCELLED) - amount: {}",
                        session.getId(), amountForThisSession);
                return remaining;
            }

            // Only consider the payment if it's ACTIVE
            // If inactive (but not permanently deleted), treat it as if it doesn't exist
            // (create new one)
            if (detail.getActive() != null && detail.getActive()) {
                double alreadyPaid = detail.getAmountPaid();
                double stillOwed = pricePerSession - alreadyPaid;

                if (stillOwed > 0) {
                    double amountToAdd = Math.min(stillOwed, remaining);
                    detail.setAmountPaid(alreadyPaid + amountToAdd);
                    paymentDetailRepository.save(detail);
                    remaining -= amountToAdd;

                    LOGGER.debug("Updated existing ACTIVE PaymentDetail for session {} - added: {}, new total: {}",
                            session.getId(), amountToAdd, detail.getAmountPaid());
                }
            } else {
                // Inactive detail exists (but not permanently deleted), create a new active one
                LOGGER.info("Found INACTIVE PaymentDetail for session {}, creating new active one", session.getId());
                double amountForThisSession = Math.min(pricePerSession, remaining);
                PaymentDetailEntity newDetail = PaymentDetailEntity.builder()
                        .payment(payment)
                        .session(session)
                        .amountPaid(amountForThisSession)
                        .build();
                paymentDetailRepository.save(Objects.requireNonNull(newDetail));
                remaining -= amountForThisSession;

                LOGGER.debug("Created new ACTIVE PaymentDetail for session {} - amount: {}",
                        session.getId(), amountForThisSession);
            }
        } else {
            double amountForThisSession = Math.min(pricePerSession, remaining);
            PaymentDetailEntity newDetail = PaymentDetailEntity.builder()
                    .payment(payment)
                    .session(session)
                    .amountPaid(amountForThisSession)
                    .build();
            paymentDetailRepository.save(Objects.requireNonNull(newDetail));
            remaining -= amountForThisSession;

            LOGGER.debug("Created new PaymentDetail for session {} - amount: {}",
                    session.getId(), amountForThisSession);
        }

        return remaining;
    }

    /**
     * Restreint des séances facturables à celles couvertes par une présence de rattrapage.
     *
     * <p>L'ordre reçu est conservé : il vient du résolveur, déjà chronologique. Reconstituer un
     * tri ici sur {@code getSessionTimeStart} — ce que faisait l'ancienne lecture des présences —
     * était à la fois inutile et fragile, une séance sans date levant alors un
     * {@code NullPointerException} au comparateur.</p>
     */
    private List<SessionEntity> filterOnCatchUpAttendance(List<SessionEntity> billable,
            List<AttendanceEntity> attendances) {
        Set<Long> catchUpSessionIds = new HashSet<>();
        for (AttendanceEntity attendance : attendances) {
            if (!Boolean.TRUE.equals(attendance.getIsCatchUp())) {
                continue;
            }
            SessionEntity session = attendance.getSession();
            if (session != null && session.getId() != null) {
                catchUpSessionIds.add(session.getId());
            }
        }

        return billable.stream()
                .filter(session -> catchUpSessionIds.contains(session.getId()))
                .toList();
    }

    public List<SessionEntity> getAllSessionsForSeries(Long sessionSeriesId) {
        return sessionRepository
                .findBySessionSeriesId(sessionSeriesId)
                .stream()
                .sorted(Comparator.comparing(SessionEntity::getSessionTimeStart))
                .toList();
    }

    public double calculateAttendedSessionsCost(Long studentId, Long sessionSeriesId, GroupEntity group) {
        List<AttendanceEntity> existingAttendances = attendanceRepository
                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, sessionSeriesId);

        double pricePerSession = group.getPrice().getPrice();

        if (!existingAttendances.isEmpty()) {
            int attendedSessionsCount = existingAttendances.size();
            double cost = attendedSessionsCount * pricePerSession;
            LOGGER.debug("Student {} (CATCH-UP): {} sessions attended, cost: {}",
                    studentId, attendedSessionsCount, cost);
            return cost;
        } else {
            List<SessionEntity> allSessions = getAllSessionsForSeries(sessionSeriesId);
            double cost = allSessions.size() * pricePerSession;
            LOGGER.debug("Student {} (NORMAL): {} sessions in series, cost: {}",
                    studentId, allSessions.size(), cost);
            return cost;
        }
    }

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

    private double getTotalPaidForSeries(Long studentId, Long sessionSeriesId) {
        List<PaymentDetailEntity> details = paymentDetailRepository
                .findByPayment_StudentIdAndSession_SessionSeriesId(studentId, sessionSeriesId);

        // IMPORTANT: Only count ACTIVE PaymentDetails AND ignore CANCELLED payments
        // This allows re-payment after cancellation/deactivation
        return details.stream()
                .filter(detail -> detail.getActive() != null && detail.getActive())
                .filter(detail -> !"CANCELLED".equals(detail.getPayment().getStatus()))
                .mapToDouble(PaymentDetailEntity::getAmountPaid)
                .sum();
    }

    private double calculateTotalCost(GroupEntity group) {
        double pricePerSession = group.getPrice().getPrice();
        int sessionNumberPerSerie = group.getSessionNumberPerSerie();
        return pricePerSession * sessionNumberPerSerie;
    }
}