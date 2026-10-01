package com.school.management.service.payment;

import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.EncashmentKind;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.AttendanceRepository;
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
import java.util.Optional;

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
 * <h2>Un versement est un Encaissement</h2>
 * Chaque versement accepté devient un Encaissement numéroté ({@link EncashmentService}), et
 * chaque part qu'il crédite une Imputation. Le cumul d'une série n'est plus incrémenté ici : il
 * est recalculé par {@link EncashmentService#allocate} comme la somme des Imputations actives,
 * avec le statut évalué contre le coût au prorata (spec admin-corrections, exigences 1.1 et 1.4).
 * Un Encaissement annulé cesse ainsi de compter sans qu'aucun montant ne soit réécrit à la main.
 *
 * <h2>Une seule transaction</h2>
 * Encaissement, Imputations, ventilations et traces de report vivent dans la même transaction
 * (exigence 5.6) : un échec à n'importe quelle étape annule l'ensemble, numéro de reçu compris
 * (exigences 4.9, 5.5, 5.7).
 *
 * <h2>Le rattrapage suit la même règle</h2>
 * Le chemin rattrapage passe par le même Encaissement, la même clé d'idempotence et le même
 * calcul de statut (spec admin-corrections, exigence 1.7). Il paie une séance : il est plafonné
 * au prix net de la séance et à ce qui reste dû sur sa série, et ne se reporte jamais.
 */
@Service
public class PaymentProcessingService {

        private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProcessingService.class);

        /**
         * Mode de paiement et note saisis avec le versement. Ils sont conservés sur l'Encaissement
         * et n'entrent dans aucun calcul.
         *
         * @param method mode de paiement (« cash », « cheque »…), facultatif
         * @param note   note libre, facultative
         */
        public record PaymentMeans(String method, String note) {
                /** Aucun mode ni note : appels internes et tests. */
                public static final PaymentMeans NONE = new PaymentMeans(null, null);
        }

        private final PaymentRepository paymentRepository;
        private final StudentRepository studentRepository;
        private final GroupRepository groupRepository;
        private final SessionRepository sessionRepository;
        private final SessionSeriesRepository sessionSeriesRepository;
        private final StudentGroupRepository studentGroupRepository;

        /**
         * Présences de l'étudiant sur la série : elles rattachent au groupe un étudiant venu en
         * rattrapage sans y être inscrit.
         */
        private final AttendanceRepository attendanceRepository;

        private final PaymentDistributionService distributionService;

        /** Source du prix net et du plafond encaissable, réduction comprise. */
        private final PaymentQuoteService paymentQuoteService;

        /** Décide seul où va chaque dinar du versement, sans rien écrire. */
        private final PaymentAllocationService allocationService;

        /** Trace des montants reçus par report (exigence 6.1). */
        private final PaymentCarryOverService carryOverService;

        /** Sépare le rejeu d'une soumission du second encaissement réel. */
        private final PaymentIdempotencyService idempotencyService;

        /** Enregistre l'Encaissement, ses Imputations, et recalcule le cumul de chaque série. */
        private final EncashmentService encashmentService;

        /**
         * Refuse l'encaissement sur un groupe d'une année scolaire close (school-year, exigence
         * 9.2). Les autres écritures liées au paiement (édition et suppression d'une ligne de
         * ventilation) le faisaient déjà ; l'encaissement était la seule porte restée ouverte.
         */
        private final com.school.management.service.ReadOnlyYearGuard readOnlyYearGuard;

        public PaymentProcessingService(
                        PaymentRepository paymentRepository,
                        StudentRepository studentRepository,
                        GroupRepository groupRepository,
                        SessionRepository sessionRepository,
                        SessionSeriesRepository sessionSeriesRepository,
                        StudentGroupRepository studentGroupRepository,
                        AttendanceRepository attendanceRepository,
                        PaymentDistributionService distributionService,
                        PaymentQuoteService paymentQuoteService,
                        PaymentAllocationService allocationService,
                        PaymentCarryOverService carryOverService,
                        PaymentIdempotencyService idempotencyService,
                        EncashmentService encashmentService,
                        com.school.management.service.ReadOnlyYearGuard readOnlyYearGuard) {
                this.idempotencyService = idempotencyService;
                this.encashmentService = encashmentService;
                this.readOnlyYearGuard = readOnlyYearGuard;
                this.paymentRepository = paymentRepository;
                this.studentRepository = studentRepository;
                this.groupRepository = groupRepository;
                this.sessionRepository = sessionRepository;
                this.sessionSeriesRepository = sessionSeriesRepository;
                this.studentGroupRepository = studentGroupRepository;
                this.attendanceRepository = attendanceRepository;
                this.distributionService = distributionService;
                this.paymentQuoteService = paymentQuoteService;
                this.allocationService = allocationService;
                this.carryOverService = carryOverService;
        }

        // ------------------------------------------------------------------
        // Versement de série
        // ------------------------------------------------------------------

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
                return processPayment(studentId, groupId, sessionSeriesId, amountPaid, null, PaymentMeans.NONE);
        }

        /** Comme {@link #processPayment(Long, Long, Long, double, String, PaymentMeans)}, sans mode ni note. */
        @Transactional
        public PaymentAllocationResult processPayment(Long studentId, Long groupId, Long sessionSeriesId,
                        double amountPaid, String idempotencyKey) {
                return processPayment(studentId, groupId, sessionSeriesId, amountPaid, idempotencyKey,
                                PaymentMeans.NONE);
        }

        /**
         * Encaisse un versement, en écartant le rejeu d'une soumission déjà traitée.
         *
         * <p>Deux situations produisent une requête rigoureusement identique : le double clic sur
         * « Encaisser », et le second versement réel du même montant le même jour, prévu par la
         * règle du paiement par facilité. Aucune donnée ne les sépare. La clé d'idempotence porte
         * l'intention : engendrée à l'ouverture du formulaire, elle est identique pour un rejeu et
         * neuve pour un nouvel encaissement.</p>
         *
         * <p>Sans clé, le comportement est celui d'avant : chaque appel encaisse.</p>
         *
         * @param idempotencyKey clé fournie par le client, ou {@code null}
         * @param means          mode de paiement et note, conservés sur l'Encaissement
         * @throws CustomServiceException 409 si la clé a déjà servi pour un encaissement différent
         */
        @Transactional
        public PaymentAllocationResult processPayment(Long studentId, Long groupId, Long sessionSeriesId,
                        double amountPaid, String idempotencyKey, PaymentMeans means) {
                LOGGER.info("Processing payment for student {} on series {} - amount: {}",
                                studentId, sessionSeriesId, amountPaid);

                String key = idempotencyService.normalizeKey(idempotencyKey);

                // Le rejeu est écarté AVANT toute validation et toute écriture : une soumission
                // déjà traitée ne doit pas pouvoir échouer sur un contrôle que l'original a passé,
                // ni produire un second versement.
                Optional<PaymentAllocationResult> replay = idempotencyService.findReplay(
                                key, studentId, groupId, sessionSeriesId, money(amountPaid));
                if (replay.isPresent()) {
                        return replay.get();
                }

                StudentEntity student = studentRepository.findById(Objects.requireNonNull(studentId))
                                .orElseThrow(() -> new CustomServiceException(
                                                "Student not found with ID: " + studentId, HttpStatus.NOT_FOUND));

                GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                                .orElseThrow(() -> new CustomServiceException("Group not found with ID: " + groupId,
                                                HttpStatus.NOT_FOUND));

                // Avant toute écriture, et avant le contrôle d'inscription : sur une année close,
                // c'est l'année qui bloque, et le message doit le dire plutôt que d'accuser une
                // inscription manquante.
                readOnlyYearGuard.assertGroupMutable(group);

                requireEnrolmentOrCatchUp(studentId, group, sessionSeriesId);

                SessionSeriesEntity targetSeries = sessionSeriesRepository.findById(Objects.requireNonNull(sessionSeriesId))
                                .orElseThrow(() -> new CustomServiceException(
                                                "Session series not found with ID: " + sessionSeriesId, HttpStatus.NOT_FOUND));

                // Refus du montant nul ou négatif (exigence 4.6). Le contrôle est délégué au
                // garde-fou du service de ventilation, qui nomme la cause réelle — série soldée,
                // étudiant exempté, reste à payer — au lieu du seul symptôme. Le plafond, lui,
                // n'est plus de son ressort : il appartient au plan.
                distributionService.canProcessPayment(studentId, sessionSeriesId, amountPaid);

                BigDecimal amount = money(amountPaid);

                // Plan calculé AVANT toute écriture : le refus total n'a ainsi rien à annuler, et
                // aucun numéro de reçu n'est attribué à un versement refusé.
                AllocationPlan plan = allocationService.plan(studentId, groupId, sessionSeriesId, amount);
                if (!plan.isComplete()) {
                        throw new CustomServiceException(unplaceableMessage(plan, amount),
                                        HttpStatus.BAD_REQUEST);
                }

                EncashmentEntity encashment = encashmentService.open(new EncashmentService.NewEncashment(
                                student, group, targetSeries, amount, EncashmentKind.REGULAR,
                                means.method(), means.note()));
                // Une seule date pour tout ce que produit ce versement : celle de l'Encaissement.
                Date paymentDate = encashment.getReceivedAt();

                BigDecimal directlyAllocated = zero();
                List<CarriedOverAmount> carryOvers = new ArrayList<>();
                PaymentEntity targetedPayment = null;
                PaymentEntity firstCreditedPayment = null;

                for (SeriesAllocation allocation : plan.allocations()) {
                        PaymentEntity payment = getOrCreateSeriesPayment(student, group, allocation.seriesId());
                        payment.setPaymentDate(paymentDate);

                        // Imputation, puis cumul et statut de la série recalculés depuis les
                        // Imputations actives : jamais un incrément fait ici.
                        EncashmentAllocationEntity imputation = encashmentService.allocate(
                                        encashment, payment, allocation.amount(), allocation.carriedOver());

                        // Un versement n'est traité qu'une fois sa ventilation achevée (exigence
                        // 4.8) : un échec ici remonte et annule la transaction entière.
                        distributionService.distributePayment(payment, allocation.seriesId(),
                                        allocation.amount().doubleValue());

                        if (allocation.carriedOver()) {
                                carryOverService.record(studentId, sessionSeriesId, allocation.seriesId(),
                                                payment, allocation.amount(), paymentDate, imputation);
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
                                sessionSeriesId, amount, directlyAllocated, carryOvers, primaryPayment, encashment);

                // Empreinte conservée dans la MÊME transaction : si la ventilation avait échoué,
                // elle disparaîtrait avec, et une nouvelle tentative resterait possible. Une
                // empreinte survivant à un échec bloquerait la reprise d'un versement jamais abouti.
                idempotencyService.remember(key, result, null);

                LOGGER.info("Versement {} de {} DA réparti : {} DA sur la série {}, {} DA reportés sur {} série(s)",
                                encashment.getReceiptNumber(), amount.toPlainString(),
                                directlyAllocated.toPlainString(), sessionSeriesId,
                                result.amountCarriedOver().toPlainString(), carryOvers.size());

                return result;
        }

        // ------------------------------------------------------------------
        // Rattrapage
        // ------------------------------------------------------------------

        /** Comme {@link #processCatchUpPayment(Long, Long, double, String, PaymentMeans)}, sans clé ni mode. */
        @Transactional
        public PaymentAllocationResult processCatchUpPayment(Long studentId, Long sessionId, double amountPaid) {
                return processCatchUpPayment(studentId, sessionId, amountPaid, null, PaymentMeans.NONE);
        }

        /**
         * Encaisse le paiement d'une séance de rattrapage.
         *
         * <p>Même règle que le versement de série (spec admin-corrections, exigence 1.7) : un
         * Encaissement numéroté, de type {@link EncashmentKind#CATCH_UP}, une Imputation sur la
         * série de la séance, le cumul et le statut recalculés depuis les Imputations, la même clé
         * d'idempotence. Le statut se compare donc au coût au prorata, et non plus au coût des
         * séances assistées au tarif catalogue.</p>
         *
         * <p><b>Plafond.</b> Un rattrapage paie une séance : le montant ne dépasse ni le prix net de
         * la séance, ni ce qui reste dû sur sa série. Il ne se reporte pas sur une autre série. Une
         * séance gratuite ici (rattrapage compensatoire, facturé dans le groupe d'origine), déjà
         * réglée ou encore « à préciser » ne laisse rien à encaisser : le versement est refusé, au
         * lieu de créer un crédit que rien n'explique.</p>
         *
         * @param idempotencyKey clé fournie par le client, ou {@code null}
         * @param means          mode de paiement et note, conservés sur l'Encaissement
         * @throws CustomServiceException 400 si le montant est nul, négatif ou au-delà du plafond,
         *                                si la séance n'a pas de série, ou si l'étudiant n'est ni
         *                                inscrit ni présent sur la série ; 404 si l'étudiant ou la
         *                                séance est introuvable ; 409 sur une année close ou une clé
         *                                réutilisée
         */
        @Transactional
        public PaymentAllocationResult processCatchUpPayment(Long studentId, Long sessionId, double amountPaid,
                        String idempotencyKey, PaymentMeans means) {
                LOGGER.info("Processing catch-up payment for student {} on session {} - amount: {}",
                                studentId, sessionId, amountPaid);

                String key = idempotencyService.normalizeKey(idempotencyKey);
                Optional<PaymentAllocationResult> replay = idempotencyService.findCatchUpReplay(
                                key, studentId, sessionId, money(amountPaid));
                if (replay.isPresent()) {
                        return replay.get();
                }

                requirePositiveAmount(amountPaid);

                StudentEntity student = studentRepository.findById(Objects.requireNonNull(studentId))
                                .orElseThrow(() -> new CustomServiceException(
                                                "Student not found with ID: " + studentId, HttpStatus.NOT_FOUND));

                SessionEntity session = sessionRepository.findById(Objects.requireNonNull(sessionId))
                                .orElseThrow(() -> new CustomServiceException(
                                                "Session not found with ID: " + sessionId, HttpStatus.NOT_FOUND));

                GroupEntity group = session.getGroup();
                SessionSeriesEntity series = session.getSessionSeries();

                // Même règle que l'encaissement de série : pas d'écriture sur une année close.
                readOnlyYearGuard.assertGroupMutable(group);

                if (series == null) {
                        throw new CustomServiceException(
                                        "La séance " + sessionId + " n'appartient à aucune série : aucun "
                                                        + "rattrapage ne peut y être encaissé.",
                                        HttpStatus.BAD_REQUEST);
                }

                requireEnrolmentOrCatchUp(studentId, group, series.getId());

                BigDecimal amount = money(amountPaid);
                requireWithinCatchUpCeiling(amount, paymentQuoteService.quote(studentId, series.getId()), series);

                EncashmentEntity encashment = encashmentService.open(new EncashmentService.NewEncashment(
                                student, group, series, amount, EncashmentKind.CATCH_UP,
                                means.method(), means.note()));

                PaymentEntity payment = getOrCreateSeriesPayment(student, group, series.getId());
                payment.setPaymentDate(encashment.getReceivedAt());
                encashmentService.allocate(encashment, payment, amount, false);

                distributionService.distributePayment(payment, series.getId(), amount.doubleValue());

                PaymentAllocationResult result = new PaymentAllocationResult(studentId, group.getId(),
                                series.getId(), amount, amount, List.of(), payment, encashment);
                idempotencyService.remember(key, result, sessionId);

                LOGGER.info("Rattrapage {} de {} DA encaissé sur la série {} - ligne {}, statut {}",
                                encashment.getReceiptNumber(), amount.toPlainString(), series.getId(),
                                payment.getId(), payment.getStatus());

                return result;
        }

        /**
         * Plafond d'un rattrapage : le prix net de la séance, et ce qui reste dû sur sa série.
         *
         * <p>Le second est le plafond encaissable du devis : pour un étudiant venu seulement en
         * rattrapage, le dû à ce jour, c'est-à-dire les séances suivies et facturables. Un
         * rattrapage compensatoire n'y entre pas (sa séance est facturée dans le groupe
         * d'origine), un rattrapage « à préciser » non plus (il ne facture rien tant qu'il n'est
         * pas tranché).</p>
         */
        private void requireWithinCatchUpCeiling(BigDecimal amount, PaymentQuoteDTO quote,
                        SessionSeriesEntity series) {
                BigDecimal maxPayable = quote.maxPayable();

                if (maxPayable.signum() <= 0) {
                        if (quote.exempted()) {
                                throw new CustomServiceException(
                                                "Cet étudiant est exempté : aucun montant n'est dû pour cette série.",
                                                HttpStatus.BAD_REQUEST);
                        }
                        throw new CustomServiceException(String.format(
                                        "Rien à encaisser pour ce rattrapage : la série « %s » ne doit plus rien "
                                                        + "à cet étudiant. La séance est déjà réglée, gratuite ici "
                                                        + "(rattrapage compensatoire, facturé dans le groupe d'origine), "
                                                        + "ou encore « à préciser » : renseignez d'abord la séance "
                                                        + "manquée et la décision « déjà payée ».",
                                        series.getName()),
                                        HttpStatus.BAD_REQUEST);
                }

                BigDecimal sessionPrice = quote.netPricePerSession();
                if (amount.compareTo(sessionPrice) > 0) {
                        throw new CustomServiceException(String.format(
                                        "Le montant payé (%s DA) dépasse le coût de la séance (%s DA)%s.",
                                        amount.toPlainString(), sessionPrice.toPlainString(), discountSuffix(quote)),
                                        HttpStatus.BAD_REQUEST);
                }

                if (amount.compareTo(maxPayable) > 0) {
                        throw new CustomServiceException(String.format(
                                        "Versement de %s DA refusé en totalité : au maximum %s DA restent dus sur "
                                                        + "la série « %s » pour ce rattrapage. Un rattrapage ne se "
                                                        + "reporte pas sur une autre série.",
                                        amount.toPlainString(), maxPayable.toPlainString(), series.getName()),
                                        HttpStatus.BAD_REQUEST);
                }
        }

        // ------------------------------------------------------------------
        // Messages de refus
        // ------------------------------------------------------------------

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

        // ------------------------------------------------------------------
        // Garde-fous et outils
        // ------------------------------------------------------------------

        /**
         * Exige un rattachement de l'étudiant à la série visée : une inscription au groupe, ou
         * une présence de rattrapage sur cette série.
         *
         * <p>Le contrôle protège d'un versement encaissé pour un étudiant étranger au groupe :
         * son montant entrerait dans l'encaissé du groupe sans entrer dans l'attendu, calculé
         * sur les seuls membres, ce qui gonflerait le taux de recouvrement.</p>
         *
         * <p>Une inscription <strong>inactive</strong> est acceptée : un étudiant ayant quitté le
         * groupe peut rester débiteur, et refuser son versement empêcherait de recouvrer sa
         * dette.</p>
         *
         * <p><strong>Le rattrapage est accepté sans inscription.</strong> Un étudiant venu
         * rattraper une séance dans ce groupe a consommé cette séance : business-rules.md la
         * déclare facturable, et {@code PaymentQuoteService} l'annonce comme telle
         * ({@code catchUpOnly}, plafond égal au dû à ce jour). Exiger une inscription refusait
         * l'encaissement d'un montant que l'application venait de présenter comme dû — le devis
         * affichait « 1 séance × 2 000 DA » et la confirmation échouait. La présence de
         * rattrapage vaut rattachement : elle est enregistrée sur la série, donc l'étudiant n'est
         * pas étranger au groupe, et son coût est résolu par le même résolveur que celui des
         * membres.</p>
         *
         * @throws CustomServiceException (HTTP 400) si l'étudiant n'a ni inscription ni présence
         *                                de rattrapage sur la série
         */
        private void requireEnrolmentOrCatchUp(Long studentId, GroupEntity group, Long sessionSeriesId) {
                boolean everEnrolled = studentGroupRepository.findByGroupId(group.getId()).stream()
                                .map(sg -> sg.getStudent())
                                .filter(Objects::nonNull)
                                .anyMatch(student -> studentId.equals(student.getId()));
                if (everEnrolled) {
                        return;
                }

                boolean attendedSeries = !attendanceRepository
                                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, sessionSeriesId)
                                .isEmpty();
                if (attendedSeries) {
                        return;
                }

                throw new CustomServiceException(String.format(
                                "L'étudiant %d n'est ni inscrit dans le groupe « %s » ni présent en "
                                                + "rattrapage sur cette série : aucun versement ne peut y "
                                                + "être encaissé. Inscrivez-le au groupe, ou enregistrez sa "
                                                + "présence sur la séance rattrapée.",
                                studentId, group.getName()),
                                HttpStatus.BAD_REQUEST);
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

        /**
         * Ligne de paiement (cumul) de l'étudiant pour la série, créée vide si elle n'existe pas.
         * Son montant et son statut sont ensuite fixés par {@link EncashmentService#allocate}.
         */
        private PaymentEntity getOrCreateSeriesPayment(StudentEntity student, GroupEntity group,
                        Long sessionSeriesId) {
                // findActive... ignore les lignes CANCELLED : une ligne annulée ne reçoit plus rien.
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
                                                                        "Session series not found", HttpStatus.NOT_FOUND));

                                        PaymentEntity newPayment = PaymentEntity.builder()
                                                        .student(student)
                                                        .group(group)
                                                        .sessionSeries(sessionSeries)
                                                        .amountPaid(0.0)
                                                        .paymentDate(new Date())
                                                        .status(PaymentLineStatus.PENDING)
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
