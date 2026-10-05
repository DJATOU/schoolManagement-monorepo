package com.school.management.service.payment;

import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.EncashmentKind;
import com.school.management.persistance.EncashmentStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentCarryOverEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.EncashmentAllocationRepository;
import com.school.management.repository.EncashmentRepository;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.service.correction.CorrectionReason;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cycle de vie d'un Encaissement : enregistrement, imputation sur les séries, neutralisation
 * (spec admin-corrections, exigences 1 et 2, design D2 et D3).
 *
 * <p><b>Le cumul de série est un dérivé.</b> Après toute écriture d'Imputation, le cumul
 * {@code payments.amount_paid} d'une série est recalculé comme la somme de ses Imputations
 * actives — jamais incrémenté, jamais réécrit depuis la ventilation. C'est l'invariant 1.4 :
 * l'argent compté sur une série est exactement l'argent des encaissements non annulés.</p>
 *
 * <p><b>Ce que ce service ne décide pas.</b> Où va chaque dinar d'un versement (plan de report)
 * appartient à {@code PaymentAllocationService} ; quelles séances le reçoivent, à
 * {@code PaymentDistributionService}. Les règles métier d'une annulation demandée par
 * l'administratrice — aperçu, plancher des remboursements, année close, trace — appartiennent à
 * la correction (lot B). Ici, seulement la mécanique, et les invariants qu'aucun appelant ne doit
 * pouvoir contourner.</p>
 */
@Service
public class EncashmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EncashmentService.class);

    private static final int MONEY_SCALE = 2;
    private static final int MAX_PAYMENT_METHOD_LENGTH = 50;
    private static final String SYSTEM = "system";

    private final EncashmentRepository encashmentRepository;
    private final EncashmentAllocationRepository allocationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentCarryOverRepository carryOverRepository;
    private final ReceiptNumberService receiptNumberService;
    private final PaymentCostResolver paymentCostResolver;
    private final AuditorAware<String> auditorAware;

    public EncashmentService(EncashmentRepository encashmentRepository,
                             EncashmentAllocationRepository allocationRepository,
                             PaymentRepository paymentRepository,
                             PaymentDetailRepository paymentDetailRepository,
                             PaymentCarryOverRepository carryOverRepository,
                             ReceiptNumberService receiptNumberService,
                             PaymentCostResolver paymentCostResolver,
                             AuditorAware<String> auditorAware) {
        this.encashmentRepository = encashmentRepository;
        this.allocationRepository = allocationRepository;
        this.paymentRepository = paymentRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.carryOverRepository = carryOverRepository;
        this.receiptNumberService = receiptNumberService;
        this.paymentCostResolver = paymentCostResolver;
        this.auditorAware = auditorAware;
    }

    /**
     * Ce qu'il faut pour enregistrer un encaissement. Date, heure, auteur et numéro de reçu n'en
     * font pas partie : ils sont fixés par le serveur.
     */
    public record NewEncashment(StudentEntity student,
                                GroupEntity group,
                                SessionSeriesEntity targetSeries,
                                BigDecimal amount,
                                EncashmentKind kind,
                                String paymentMethod,
                                String notes) {
    }

    // ------------------------------------------------------------------
    // Enregistrement
    // ------------------------------------------------------------------

    /**
     * Enregistre un versement reçu et lui attribue son numéro de reçu.
     *
     * @throws CustomServiceException 400 si une donnée manque, si le montant n'est pas strictement
     *                                positif, ou si la série visée n'appartient pas au groupe
     */
    @Transactional
    public EncashmentEntity open(NewEncashment request) {
        Objects.requireNonNull(request, "request");
        if (request.student() == null || request.group() == null || request.targetSeries() == null
                || request.kind() == null) {
            throw badRequest("Encaissement incomplet : étudiant, groupe, série et type sont obligatoires.");
        }
        if (!sameId(seriesGroupId(request.targetSeries()), request.group().getId())) {
            throw badRequest("La série « " + request.targetSeries().getName()
                    + " » n'appartient pas au groupe « " + request.group().getName() + " ».");
        }
        BigDecimal amount = positiveMoney(request.amount(), "Montant reçu");
        String paymentMethod = normalizedPaymentMethod(request.paymentMethod());

        Date receivedAt = new Date();
        EncashmentEntity encashment = EncashmentEntity.builder()
                .receiptNumber(receiptNumberService.next(receivedAt))
                .student(request.student())
                .group(request.group())
                .targetSeries(request.targetSeries())
                .amountReceived(amount)
                .kind(request.kind())
                .paymentMethod(paymentMethod)
                .notes(normalizedNotes(request.notes()))
                .receivedAt(receivedAt)
                .receivedBy(currentAuditor())
                .status(EncashmentStatus.ACTIVE)
                .build();
        EncashmentEntity saved = encashmentRepository.save(encashment);

        LOGGER.info("Encaissement {} enregistré : {} DA, étudiant {}, série visée {}",
                saved.getReceiptNumber(), amount.toPlainString(), request.student().getId(),
                request.targetSeries().getId());
        return saved;
    }

    // ------------------------------------------------------------------
    // Imputation
    // ------------------------------------------------------------------

    /**
     * Crédite une part de l'encaissement sur une série, puis recalcule le cumul de cette série.
     *
     * <p>La ligne de paiement est verrouillée avant le recalcul, comme le fait déjà le
     * remboursement : un encaissement et un remboursement simultanés sur la même série voient
     * chacun un cumul à jour.</p>
     *
     * @param encashment  encaissement actif
     * @param payment     ligne de paiement (cumul) de l'étudiant pour la série créditée
     * @param amount      part créditée, strictement positive
     * @param carriedOver vrai si la série créditée n'est pas la série visée
     * @throws CustomServiceException 409 si l'encaissement est annulé ; 400 si la ligne de paiement
     *                                est celle d'un autre étudiant, si l'indicateur de report
     *                                contredit la série, ou si la part dépasse le reste à imputer
     */
    @Transactional
    public EncashmentAllocationEntity allocate(EncashmentEntity encashment, PaymentEntity payment,
                                               BigDecimal amount, boolean carriedOver) {
        Objects.requireNonNull(encashment, "encashment");
        Objects.requireNonNull(payment, "payment");
        if (!encashment.isActive()) {
            throw new CustomServiceException("L'encaissement " + encashment.getReceiptNumber()
                    + " est annulé : il ne peut plus rien créditer.", HttpStatus.CONFLICT);
        }
        BigDecimal part = positiveMoney(amount, "Part imputée");

        PaymentEntity locked = paymentRepository.findByIdForUpdate(payment.getId())
                .orElseThrow(() -> new CustomServiceException(
                        "Ligne de paiement introuvable : " + payment.getId(), HttpStatus.NOT_FOUND));
        if (locked.getStudent() == null || !sameId(locked.getStudent().getId(), encashment.getStudent().getId())) {
            throw badRequest("La ligne de paiement " + locked.getId()
                    + " n'est pas celle de l'étudiant de l'encaissement " + encashment.getReceiptNumber() + ".");
        }
        SessionSeriesEntity series = locked.getSessionSeries();
        if (series == null) {
            throw badRequest("La ligne de paiement " + locked.getId() + " n'est rattachée à aucune série.");
        }
        boolean isTarget = sameId(series.getId(), encashment.getTargetSeries().getId());
        if (carriedOver == isTarget) {
            throw badRequest(carriedOver
                    ? "Un report ne peut pas créditer la série visée par l'encaissement."
                    : "Une imputation directe ne peut créditer que la série visée par l'encaissement.");
        }

        // Une part ne peut pas dépasser ce qui reste de l'argent reçu : aucune imputation ne
        // fabrique de l'argent qui n'a pas été versé.
        BigDecimal alreadyAllocated = money(allocationRepository.sumActiveAmountForEncashment(encashment.getId()));
        BigDecimal remaining = encashment.getAmountReceived().subtract(alreadyAllocated);
        if (part.compareTo(remaining) > 0) {
            throw badRequest("Imputation de " + part.toPlainString() + " DA refusée : il ne reste que "
                    + remaining.toPlainString() + " DA à imputer sur l'encaissement "
                    + encashment.getReceiptNumber() + ".");
        }

        EncashmentAllocationEntity allocation = allocationRepository.save(EncashmentAllocationEntity.builder()
                .encashment(encashment)
                .series(series)
                .payment(locked)
                .amount(part)
                .carriedOver(carriedOver)
                .active(true)
                .build());

        refreshSeriesCumul(locked);
        return allocation;
    }

    // ------------------------------------------------------------------
    // Neutralisation
    // ------------------------------------------------------------------

    /**
     * Neutralise un encaissement : il reste enregistré, mais ne compte plus dans aucun montant.
     *
     * <p>Ses Imputations, ses lignes de ventilation et ses reports sont désactivés, puis le cumul et
     * le statut de chaque série touchée sont recalculés. Les autres encaissements de ces séries ne
     * sont pas touchés.</p>
     *
     * @throws CustomServiceException 404 si l'encaissement est introuvable ; 409 s'il est déjà annulé
     */
    @Transactional
    public EncashmentEntity neutralize(Long encashmentId, CorrectionReason reason) {
        Objects.requireNonNull(reason, "reason");
        EncashmentEntity encashment = encashmentRepository.findByIdForUpdate(encashmentId)
                .orElseThrow(() -> new CustomServiceException(
                        "Encaissement introuvable : " + encashmentId, HttpStatus.NOT_FOUND));
        if (!encashment.isActive()) {
            throw new CustomServiceException("L'encaissement " + encashment.getReceiptNumber()
                    + " est déjà annulé.", HttpStatus.CONFLICT);
        }

        // Lignes de paiement touchées, dans l'ordre et sans doublon : chacune est recalculée une fois.
        Map<Long, PaymentEntity> touched = new LinkedHashMap<>();
        for (EncashmentAllocationEntity allocation
                : allocationRepository.findByEncashmentIdAndActiveTrueOrderByIdAsc(encashmentId)) {
            allocation.setActive(false);
            for (PaymentDetailEntity detail : paymentDetailRepository.findByEncashmentAllocationId(allocation.getId())) {
                detail.setActive(false);
            }
            for (PaymentCarryOverEntity carryOver : carryOverRepository.findByEncashmentAllocationId(allocation.getId())) {
                carryOver.setActive(false);
            }
            touched.putIfAbsent(allocation.getPayment().getId(), allocation.getPayment());
        }

        encashment.setStatus(EncashmentStatus.CANCELLED);
        encashment.setCancelledAt(new Date());
        encashment.setCancelledBy(currentAuditor());
        encashment.setCancelReasonType(reason.type());
        encashment.setCancelReasonText(reason.text());
        encashmentRepository.save(encashment);

        for (PaymentEntity payment : touched.values()) {
            PaymentEntity locked = paymentRepository.findByIdForUpdate(payment.getId()).orElseThrow();
            refreshSeriesCumul(locked);
        }

        LOGGER.info("Encaissement {} neutralisé ({}), {} série(s) recalculée(s)",
                encashment.getReceiptNumber(), reason.type(), touched.size());
        return encashment;
    }

    // ------------------------------------------------------------------
    // Mode de paiement et note
    // ------------------------------------------------------------------

    /**
     * Corrige le mode de paiement et la note d'un encaissement actif (spec admin-corrections,
     * exigence 3.6). Ni le montant, ni l'étudiant, ni les séries ne changent : aucun montant n'est
     * recalculé. Valeurs normalisées comme à l'encaissement.
     *
     * @throws CustomServiceException 409 si l'encaissement est annulé ; 400 si le mode est trop long
     */
    @Transactional
    public EncashmentEntity editDetails(EncashmentEntity encashment, String paymentMethod, String notes) {
        Objects.requireNonNull(encashment, "encashment");
        if (!encashment.isActive()) {
            throw new CustomServiceException("L'encaissement " + encashment.getReceiptNumber()
                    + " est annulé : il ne se corrige plus.", HttpStatus.CONFLICT);
        }
        encashment.setPaymentMethod(normalizedPaymentMethod(paymentMethod));
        encashment.setNotes(normalizedNotes(notes));
        return encashmentRepository.save(encashment);
    }

    /** Mode de paiement tel qu'un encaissement le conserve : espaces retirés, vide = absent. */
    public static String normalizedPaymentMethod(String paymentMethod) {
        String method = blankToNull(paymentMethod);
        if (method != null && method.length() > MAX_PAYMENT_METHOD_LENGTH) {
            throw badRequest("Mode de paiement trop long : " + MAX_PAYMENT_METHOD_LENGTH + " caractères au plus.");
        }
        return method;
    }

    /** Note telle qu'un encaissement la conserve : espaces retirés, vide = absente. */
    public static String normalizedNotes(String notes) {
        return blankToNull(notes);
    }

    // ------------------------------------------------------------------
    // Cumul de série
    // ------------------------------------------------------------------

    /**
     * Recalcule le cumul d'une série depuis ses Imputations actives, puis son statut stocké.
     *
     * @param payment ligne de paiement, verrouillée par l'appelant
     * @return le cumul recalculé, échelle 2
     */
    @Transactional
    public BigDecimal refreshSeriesCumul(PaymentEntity payment) {
        BigDecimal cumul = money(allocationRepository.sumActiveAmountForPayment(payment.getId()));
        payment.setAmountPaid(cumul.doubleValue());
        payment.setStatus(PaymentLineStatus.of(cumul, seriesCost(payment)));
        paymentRepository.save(payment);
        return cumul;
    }

    /** Coût au prorata de la série, vide s'il ne peut pas être résolu. */
    private Optional<BigDecimal> seriesCost(PaymentEntity payment) {
        if (payment.getStudent() == null || payment.getSessionSeries() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(paymentCostResolver
                    .resolve(payment.getStudent().getId(), payment.getSessionSeries().getId())
                    .monthTotalCost());
        } catch (RuntimeException e) {
            LOGGER.warn("Coût de la série non résolu pour la ligne de paiement {} : {}",
                    payment.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private String currentAuditor() {
        return auditorAware.getCurrentAuditor().filter(name -> !name.isBlank()).orElse(SYSTEM);
    }

    private static BigDecimal positiveMoney(BigDecimal value, String label) {
        if (value == null) {
            throw badRequest(label + " obligatoire.");
        }
        BigDecimal amount = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw badRequest(label + " strictement positif attendu, reçu " + value.toPlainString() + ".");
        }
        return amount;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static Long seriesGroupId(SessionSeriesEntity series) {
        return series.getGroup() == null ? null : series.getGroup().getId();
    }

    private static boolean sameId(Long a, Long b) {
        return a != null && a.equals(b);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static CustomServiceException badRequest(String message) {
        return new CustomServiceException(message, HttpStatus.BAD_REQUEST);
    }
}
