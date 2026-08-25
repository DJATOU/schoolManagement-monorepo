package com.school.management.service.payment;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.AllocationPlan.SeriesAllocation;
import com.school.management.service.payment.PaymentAllocationResult.CarriedOverAmount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Encaissement d'un versement : plafonnement sur la série visée puis report du surplus sur les
 * séries suivantes (exigences 4 et 5).
 *
 * <h2>Le plan avant l'écriture</h2>
 * La répartition est calculée <strong>entièrement en lecture</strong> par le
 * {@link PaymentAllocationService} avant qu'une seule ligne ne soit écrite. Ce découpage rend le
 * refus total de l'exigence 5.11 trivial : lorsque le plan ne couvre pas le versement, rien n'a
 * encore été écrit et il n'y a aucune annulation à orchestrer.
 *
 * <h2>Jamais d'encaissement partiel</h2>
 * Un versement dont une part ne peut être imputée nulle part est refusé <strong>en totalité</strong>,
 * y compris la part qui aurait été plaçable. La raison est comptable et non technique : en
 * acceptant partiellement, l'argent physiquement reçu diverge du montant enregistré, et
 * l'administrateur conserve la différence en main sans aucune trace. Le message de refus annonce
 * donc le maximum réellement encaissable <em>et</em> l'action corrective (exigence 5.12).
 *
 * <h2>Une seule transaction</h2>
 * Imputations, ventilations et traces de report vivent dans la même transaction (exigence 5.6) :
 * un échec à n'importe quelle étape annule l'ensemble, y compris la part déjà imputée sur la
 * série visée (exigences 4.9, 5.5, 5.7).
 *
 * <h2>Statut évalué contre le coût au prorata</h2>
 * Le statut de la ligne de paiement se compare au Coût_Série_Prorata de sa série, et non au coût
 * des séances assistées : un étudiant arrivé à la dernière séance d'une série et l'ayant réglée
 * est soldé (exigences 11.1, 11.2). Ce sont deux quantités que {@code business-rules.md} demande
 * explicitement de ne pas confondre.
 */
@Service
public class PaymentProcessingService {

        private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProcessingService.class);

        private static final String STATUS_COMPLETED = "COMPLETED";
        private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

        private final PaymentRepository paymentRepository;
        private final StudentRepository studentRepository;
        private final GroupRepository groupRepository;
        private final SessionRepository sessionRepository;
        private final SessionSeriesRepository sessionSeriesRepository;
        private final StudentGroupRepository studentGroupRepository;
        private final PaymentDistributionService distributionService;

        /** Source du prix net et du plafond encaissable, réduction comprise. */
        private final PaymentQuoteService paymentQuoteService;

        /** Décide seul où va chaque dinar du versement, sans rien écrire. */
        private final PaymentAllocationService allocationService;

        /** Trace des montants reçus par report (exigence 6.1). */
        private final PaymentCarryOverService carryOverService;

        public PaymentProcessingService(
                        PaymentRepository paymentRepository,
                        StudentRepository studentRepository,
                        GroupRepository groupRepository,
                        SessionRepository sessionRepository,
                        SessionSeriesRepository sessionSeriesRepository,
                        StudentGroupRepository studentGroupRepository,
                        PaymentDistributionService distributionService,
                        PaymentQuoteService paymentQuoteService,
                        PaymentAllocationService allocationService,
                        PaymentCarryOverService carryOverService) {
                this.paymentRepository = paymentRepository;
                this.studentRepository = studentRepository;
                this.groupRepository = groupRepository;
                this.sessionRepository = sessionRepository;
                this.sessionSeriesRepository = sessionSeriesRepository;
                this.studentGroupRepository = studentGroupRepository;
                this.distributionService = distributionService;
                this.paymentQuoteService = paymentQuoteService;
                this.allocationService = allocationService;
                this.carryOverService = carryOverService;
        }

        /**
         * Encaisse un versement sur une série, en reportant sur les séries suivantes la part qui
         * dépasse le plafond de la série visée.
         *
         * @param studentId       l'étudiant qui verse
         * @param groupId         le groupe concerné
         * @param sessionSeriesId la série visée à la saisie
         * @param amountPaid      le montant reçu, strictement positif
         * @return le détail de la répartition : part imputée et reports (exigence 6.3)
         * @throws CustomServiceException 400 si le montant est nul ou négatif, si l'étudiant n'a
         *                                jamais été inscrit au groupe, ou si une part du versement
         *                                ne peut être imputée sur aucune série ; 404 si l'étudiant,
         *                                le groupe ou la série est introuvable
         */
        @Transactional
        public PaymentAllocationResult processPayment(Long studentId, Long groupId, Long sessionSeriesId,
                        double amountPaid) {
                LOGGER.info("Processing payment for student {} on series {} - amount: {}",
                                studentId, sessionSeriesId, amountPaid);

                StudentEntity student = studentRepository.findById(Objects.requireNonNull(studentId))
                                .orElseThrow(() -> new CustomServiceException(
                                                "Student not found with ID: " + studentId));

                GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                                .orElseThrow(() -> new CustomServiceException("Group not found with ID: " + groupId));

                requireEnrolment(studentId, group);

                sessionSeriesRepository.findById(Objects.requireNonNull(sessionSeriesId))
                                .orElseThrow(() -> new CustomServiceException(
                                                "Session series not found with ID: " + sessionSeriesId));

                // Refus du montant nul ou négatif (exigence 4.6). Le contrôle est délégué au
                // garde-fou du service de ventilation, qui nomme la cause réelle — série soldée,
                // étudiant exempté, reste à payer — au lieu du seul symptôme. requirePositiveAmount
                // reste le garde-fou du chemin rattrapage, qui n'a pas de devis contextuel à sa
                // disposition. Le plafond, lui, n'est plus de son ressort : il appartient au plan.
                distributionService.canProcessPayment(studentId, sessionSeriesId, amountPaid);

                BigDecimal amount = money(amountPaid);

                // Plan calculé AVANT toute écriture : le refus total n'a ainsi rien à annuler.
                AllocationPlan plan = allocationService.plan(studentId, groupId, sessionSeriesId, amount);
                if (!plan.isComplete()) {
                        throw new CustomServiceException(unplaceableMessage(plan, amount),
                                        HttpStatus.BAD_REQUEST);
                }

                Date paymentDate = new Date();
                BigDecimal directlyAllocated = zero();
                List<CarriedOverAmount> carryOvers = new ArrayList<>();
                PaymentEntity targetedPayment = null;
                PaymentEntity firstCreditedPayment = null;

                for (SeriesAllocation allocation : plan.allocations()) {
                        PaymentEntity payment = getOrCreateSeriesPayment(student, group, allocation.seriesId());

                        BigDecimal newTotal = money(payment.getAmountPaid()).add(allocation.amount());
                        payment.setAmountPaid(newTotal.doubleValue());
                        payment.setPaymentDate(paymentDate);
                        payment.setStatus(resolveStatus(studentId, allocation.seriesId(), newTotal));
                        payment = paymentRepository.save(payment);

                        // Un versement n'est traité qu'une fois sa ventilation achevée (exigence
                        // 4.8) : un échec ici remonte et annule la transaction entière.
                        distributionService.distributePayment(payment, allocation.seriesId(),
                                        allocation.amount().doubleValue());

                        if (allocation.carriedOver()) {
                                carryOverService.record(studentId, sessionSeriesId, allocation.seriesId(),
                                                payment, allocation.amount(), paymentDate);
                                carryOvers.add(new CarriedOverAmount(allocation.seriesId(),
                                                allocation.seriesName(), allocation.amount()));
                        } else {
                                directlyAllocated = allocation.amount();
                                targetedPayment = payment;
                        }

                        if (firstCreditedPayment == null) {
                                firstCreditedPayment = payment;
                        }

                        LOGGER.info("Série {} créditée de {} DA (report : {}) - statut {}",
                                        allocation.seriesId(), allocation.amount().toPlainString(),
                                        allocation.carriedOver(), payment.getStatus());
                }

                // La série visée peut n'avoir rien reçu : soldée, elle est sautée et la totalité
                // part en report. La ligne principale du résultat est alors la première créditée.
                PaymentEntity primaryPayment = targetedPayment != null ? targetedPayment : firstCreditedPayment;

                PaymentAllocationResult result = new PaymentAllocationResult(studentId, groupId,
                                sessionSeriesId, amount, directlyAllocated, carryOvers, primaryPayment);

                LOGGER.info("Versement de {} DA réparti : {} DA sur la série {}, {} DA reportés sur {} série(s)",
                                amount.toPlainString(), directlyAllocated.toPlainString(), sessionSeriesId,
                                result.amountCarriedOver().toPlainString(), carryOvers.size());

                return result;
        }

        @Transactional
        public PaymentEntity processCatchUpPayment(Long studentId, Long sessionId, double amountPaid) {
                LOGGER.info("Processing catch-up payment for student {} on session {} - amount: {}",
                                studentId, sessionId, amountPaid);

                // Ce chemin ne passe pas par PaymentDistributionService.canProcessPayment : il
                // n'applique que son propre plafond. Le refus du versement nul doit donc être
                // répété ici, sans quoi un rattrapage à 0 resterait encaissable.
                requirePositiveAmount(amountPaid);

                StudentEntity student = studentRepository.findById(Objects.requireNonNull(studentId))
                                .orElseThrow(() -> new CustomServiceException(
                                                "Student not found with ID: " + studentId));

                SessionEntity session = sessionRepository.findById(Objects.requireNonNull(sessionId))
                                .orElseThrow(() -> new CustomServiceException(
                                                "Session not found with ID: " + sessionId));

                GroupEntity group = session.getGroup();
                SessionSeriesEntity sessionSeries = session.getSessionSeries();

                if (sessionSeries == null) {
                        throw new CustomServiceException("Session is not part of a series");
                }

                Long sessionSeriesId = sessionSeries.getId();

                // Prix net de la séance : le contrôle se faisait sur le tarif catalogue, donc un
                // étudiant réduit pouvait verser jusqu'au plein tarif pour un rattrapage.
                PaymentQuoteDTO quote = paymentQuoteService.quote(studentId, sessionSeriesId);
                double sessionCost = quote.netPricePerSession().doubleValue();

                if (amountPaid > sessionCost) {
                        throw new CustomServiceException(String.format(
                                        "Le montant payé (%.2f DA) dépasse le coût de la séance (%.2f DA)%s.",
                                        amountPaid, sessionCost, discountSuffix(quote)),
                                        HttpStatus.BAD_REQUEST);
                }

                PaymentEntity payment = getOrCreateSeriesPayment(student, group, sessionSeriesId);

                double previousTotal = payment.getAmountPaid();
                double newTotal = previousTotal + amountPaid;
                payment.setAmountPaid(newTotal);
                payment.setPaymentDate(new Date());

                double attendedSessionsCost = distributionService.calculateAttendedSessionsCost(
                                studentId, sessionSeriesId, group);

                if (newTotal >= attendedSessionsCost) {
                        payment.setStatus(STATUS_COMPLETED);
                        LOGGER.info("Payment COMPLETED for student {} - Total: {}/{}",
                                        studentId, newTotal, attendedSessionsCost);
                } else {
                        payment.setStatus(STATUS_IN_PROGRESS);
                        LOGGER.info("Payment IN_PROGRESS for student {} - Total: {}/{}",
                                        studentId, newTotal, attendedSessionsCost);
                }

                payment = paymentRepository.save(payment);

                distributionService.distributePayment(payment, sessionSeriesId, amountPaid);

                LOGGER.info("Catch-up payment processed successfully - Payment ID: {}, Status: {}",
                                payment.getId(), payment.getStatus());

                return payment;
        }

        /**
         * Statut de la ligne de paiement, évalué contre le <strong>coût au prorata</strong> de la
         * série et non contre le coût des séances assistées.
         *
         * <p>Comparer au coût des séances assistées faisait apparaître « soldé » un étudiant qui
         * n'avait réglé que les séances déjà suivies, et comparer au coût nominal
         * ({@code total_sessions × prix}) laisserait indéfiniment « en cours » un étudiant arrivé en
         * cours de série. Le Coût_Série_Prorata est la seule quantité qui répond à la question
         * « cette série est-elle soldée pour cet étudiant ? » (exigences 11.1, 11.2).</p>
         */
        private String resolveStatus(Long studentId, Long seriesId, BigDecimal newTotal) {
                BigDecimal prorataCost = paymentQuoteService.quote(studentId, seriesId).monthTotalCost();
                return newTotal.compareTo(prorataCost) >= 0 ? STATUS_COMPLETED : STATUS_IN_PROGRESS;
        }

        /**
         * Message de refus d'un versement dont une part n'est plaçable nulle part.
         *
         * <p><strong>Trois</strong> motifs très différents mènent ici, et les confondre
         * produirait un message trompeur — donc une action corrective qui ne corrige rien
         * (exigence 5.12) :</p>
         * <ul>
         *   <li>une série <strong>existe mais n'a aucune séance planifiée</strong> : elle n'est
         *       pas ouverte. L'action corrective est de créer ses séances, et le message la
         *       nomme ;</li>
         *   <li>une série <strong>a des séances, mais aucune facturable à cet étudiant</strong> :
         *       toutes sont antérieures à son inscription et non suivies. Lui conseiller de créer
         *       des séances serait faux : il en existe déjà, et une séance de plus dans le passé
         *       n'ouvrirait rien. Il faut une séance postérieure à l'inscription, ou constater
         *       que l'étudiant ne doit rien sur cette série ;</li>
         *   <li>toutes les séries de la chaîne sont <strong>soldées</strong>, ou le groupe n'en
         *       comporte aucune au-delà de celle visée. Parler de séances à créer serait faux ici
         *       aussi : il faut une nouvelle série, ou un montant plus petit.</li>
         * </ul>
         *
         * <p>Dans les trois cas le message annonce le <strong>maximum encaissable</strong> sur la
         * chaîne, afin que l'administrateur puisse reprendre sa saisie sans tâtonner.</p>
         */
        private String unplaceableMessage(AllocationPlan plan, BigDecimal amount) {
                String maximum = plan.totalAllocated().toPlainString();
                String header = String.format(
                                "Versement de %s DA refusé en totalité : au maximum %s DA peuvent être "
                                                + "encaissés sur cette chaîne de séries.",
                                amount.toPlainString(), maximum);

                return plan.firstBlockingSeries()
                                .map(series -> blockingSeriesMessage(header, series, maximum))
                                .orElseGet(() -> String.format(
                                                "%s Aucune série ne peut recevoir le reliquat de %s DA : les séries "
                                                                + "suivantes du groupe sont déjà soldées, ou le groupe n'en "
                                                                + "comporte aucune au-delà de celle visée. Créez une nouvelle "
                                                                + "série et ses séances, ou ramenez le montant à %s DA.",
                                                header, plan.unplaceable().toPlainString(), maximum));
        }

        /**
         * Formulation propre à la série bloquante : c'est ici que le motif d'écartement se
         * traduit en action corrective, et qu'une confusion entre les deux motifs bloquants se
         * paierait d'un conseil inexact.
         */
        private String blockingSeriesMessage(String header, AllocationPlan.SkippedSeries series,
                        String maximum) {
                if (series.reason() == AllocationPlan.SkipReason.NO_SESSIONS_PLANNED) {
                        return String.format(
                                        "%s La série « %s » ne comporte aucune séance : créez d'abord "
                                                        + "les séances de la série « %s » pour l'ouvrir, puis "
                                                        + "reprenez la saisie. Vous pouvez aussi ramener le "
                                                        + "montant à %s DA.",
                                        header, series.seriesName(), series.seriesName(), maximum);
                }
                return String.format(
                                "%s La série « %s » comporte des séances, mais aucune n'est facturable à "
                                                + "cet étudiant : elles sont toutes antérieures à son "
                                                + "inscription et il n'y a pas assisté. Créer des séances "
                                                + "supplémentaires n'y changerait rien : il faut une séance "
                                                + "postérieure à son inscription, ou constater qu'il ne doit "
                                                + "rien sur cette série. Vous pouvez aussi ramener le montant "
                                                + "à %s DA.",
                                header, series.seriesName(), maximum);
        }

        /**
         * Rappel de la réduction appliquée, à joindre aux messages de refus : sans cette
         * précision, un administrateur voit son montant refusé sans comprendre que le tarif
         * retenu est le tarif réduit.
         */
        private String discountSuffix(PaymentQuoteDTO quote) {
                if (quote.discountRate().signum() <= 0) {
                        return "";
                }
                return String.format(" — réduction de %s %% appliquée sur un tarif de %s DA",
                                quote.discountRate().multiply(BigDecimal.valueOf(100))
                                                .stripTrailingZeros().toPlainString(),
                                quote.grossPricePerSession().toPlainString());
        }

        /**
         * Exige que l'étudiant soit — ou ait été — inscrit dans le groupe.
         *
         * <p>Aucun contrôle n'existait : on pouvait encaisser un versement pour un étudiant
         * étranger au groupe. Son montant entrait alors dans l'encaissé du groupe sans entrer
         * dans l'attendu, calculé sur les seuls membres, ce qui gonflait artificiellement le
         * taux de recouvrement.</p>
         *
         * <p>On accepte volontairement une inscription <strong>inactive</strong> : un étudiant
         * ayant quitté le groupe peut rester débiteur, et refuser son versement empêcherait de
         * recouvrer sa dette. Seul l'étudiant n'ayant jamais été inscrit est rejeté.</p>
         *
         * @throws CustomServiceException (HTTP 400) si aucune inscription n'existe
         */
        private void requireEnrolment(Long studentId, GroupEntity group) {
                boolean everEnrolled = studentGroupRepository.findByGroupId(group.getId()).stream()
                                .map(sg -> sg.getStudent())
                                .filter(Objects::nonNull)
                                .anyMatch(student -> studentId.equals(student.getId()));
                if (!everEnrolled) {
                        throw new CustomServiceException(String.format(
                                        "L'étudiant %d n'est pas inscrit dans le groupe « %s » : "
                                                        + "aucun versement ne peut y être encaissé.",
                                        studentId, group.getName()),
                                        HttpStatus.BAD_REQUEST);
                }
        }

        /**
         * Refuse un versement nul ou négatif.
         *
         * <p>Encaisser 0 ne crée qu'une ligne de paiement vide, sans contrepartie à remettre à
         * l'étudiant. Le contrôle est explicite plutôt que porté par une annotation
         * {@code jakarta.validation} : ces annotations ne sont pas appliquées dans ce module,
         * aucun provider Jakarta n'étant présent sur le classpath.</p>
         *
         * @throws CustomServiceException (HTTP 400) si {@code amountPaid} n'est pas strictement positif
         */
        private void requirePositiveAmount(double amountPaid) {
                if (amountPaid <= 0) {
                        throw new CustomServiceException(
                                        "Le montant du versement doit être strictement positif.",
                                        HttpStatus.BAD_REQUEST);
                }
        }

        private PaymentEntity getOrCreateSeriesPayment(StudentEntity student, GroupEntity group,
                        Long sessionSeriesId) {
                // IMPORTANT: Utiliser findActive... pour ignorer les paiements CANCELLED
                // Cela permet de créer un nouveau paiement même si un paiement CANCELLED existe
                return paymentRepository.findActiveByStudentIdAndSessionSeriesId(student.getId(), sessionSeriesId)
                                .map(existingPayment -> {
                                        LOGGER.info("Using existing active payment {} for student {} and series {}",
                                                        existingPayment.getId(), student.getId(), sessionSeriesId);
                                        return existingPayment;
                                })
                                .orElseGet(() -> {
                                        LOGGER.info("Creating new payment for student {} and series {} (no active payment found, CANCELLED payments are ignored)",
                                                        student.getId(), sessionSeriesId);

                                        SessionSeriesEntity sessionSeries = sessionSeriesRepository
                                                        .findById(Objects.requireNonNull(sessionSeriesId))
                                                        .orElseThrow(() -> new CustomServiceException(
                                                                        "Session series not found"));

                                        PaymentEntity newPayment = PaymentEntity.builder()
                                                        .student(student)
                                                        .group(group)
                                                        .sessionSeries(sessionSeries)
                                                        .amountPaid(0.0)
                                                        .paymentDate(new Date())
                                                        .status(STATUS_IN_PROGRESS)
                                                        .build();

                                        return paymentRepository.save(Objects.requireNonNull(newPayment));
                                });
        }

        /**
         * Normalise un montant à l'échelle monétaire du projet (2 décimales, HALF_UP). Un
         * {@code null} en base est traité comme un cumul nul.
         */
        private BigDecimal money(Double amount) {
                if (amount == null) {
                        return zero();
                }
                return BigDecimal.valueOf(amount)
                                .setScale(PaymentCostCalculator.MONEY_SCALE, PaymentCostCalculator.MONEY_ROUNDING);
        }

        private BigDecimal zero() {
                return BigDecimal.ZERO.setScale(PaymentCostCalculator.MONEY_SCALE,
                                PaymentCostCalculator.MONEY_ROUNDING);
        }
}
