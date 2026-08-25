package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailSearchDTO;
import com.school.management.dto.PaymentDetailUpdateDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.service.ReadOnlyYearGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PaymentDetailAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentDetailAdminService.class);

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailAuditService paymentDetailAuditService;
    private final ReadOnlyYearGuard readOnlyYearGuard;
    private final PaymentCostResolver paymentCostResolver;

    @Autowired
    public PaymentDetailAdminService(PaymentDetailRepository paymentDetailRepository,
            PaymentRepository paymentRepository,
            PaymentDetailAuditService paymentDetailAuditService,
            ReadOnlyYearGuard readOnlyYearGuard,
            PaymentCostResolver paymentCostResolver) {
        this.paymentDetailRepository = paymentDetailRepository;
        this.paymentRepository = paymentRepository;
        this.paymentDetailAuditService = paymentDetailAuditService;
        this.readOnlyYearGuard = readOnlyYearGuard;
        this.paymentCostResolver = paymentCostResolver;
    }

    @Transactional(readOnly = true)
    public Page<PaymentDetailEntity> getAllPaymentDetailsWithFilters(Long studentId,
            Long groupId,
            Long sessionSeriesId,
            Boolean active,
            Date dateFrom,
            Date dateTo,
            Pageable pageable) {
        return paymentDetailRepository.findAllWithFilters(studentId, groupId, sessionSeriesId, active, dateFrom, dateTo,
                pageable);
    }

    /**
     * Search payment details with complete data for Payment Management UI
     * Uses DTO projection to include student, group, series, and session
     * information
     * Filters by dateCreation (createdAt) instead of paymentDate
     */
    @Transactional(readOnly = true)
    public Page<PaymentDetailSearchDTO> searchPaymentDetailsWithCompleteData(Long studentId,
            Long groupId,
            Long sessionSeriesId,
            Long sessionId,
            Boolean active,
            Date dateFrom,
            Date dateTo,
            Long levelId,
            Pageable pageable) {
        return paymentDetailRepository.searchPaymentDetailsWithCompleteData(
                studentId, groupId, sessionSeriesId, sessionId, active, dateFrom, endOfDay(dateTo),
                levelId, pageable);
    }

    /**
     * Ramène une date de fin à 23:59:59.999 afin que la borne haute soit inclusive.
     *
     * <p>Sans cet ajustement, filtrer « jusqu'au 20/08 » exclurait tous les versements
     * saisis ce jour-là, la date étant comparée à minuit.</p>
     */
    private Date endOfDay(Date dateTo) {
        if (dateTo == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateTo);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    @Transactional(readOnly = true)
    public PaymentDetailEntity getPaymentDetail(Long id) {
        return paymentDetailRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new RuntimeException("Payment detail not found with id: " + id));
    }

    @Transactional
    public PaymentDetailEntity updatePaymentDetail(Long id, PaymentDetailUpdateDTO updateDTO, String adminName) {
        validateReason(updateDTO.getReason());

        PaymentDetailEntity detail = paymentDetailRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new RuntimeException("Payment detail not found with id: " + id));
        assertYearMutable(detail);

        String oldValue = buildValueString(detail);

        if (updateDTO.getAmount() != null) {
            detail.setAmountPaid(updateDTO.getAmount());
        }
        if (updateDTO.getActive() != null) {
            detail.setActive(updateDTO.getActive());
        }

        String newValue = buildValueString(detail);
        paymentDetailRepository.save(Objects.requireNonNull(detail));

        paymentDetailAuditService.logAction(id, "MODIFIED", adminName, oldValue, newValue, updateDTO.getReason());
        recalculatePayment(detail.getPayment().getId());

        return detail;
    }

    @Transactional
    public void deletePaymentDetail(Long id, String reason, String adminName) {
        validateReason(reason);

        PaymentDetailEntity detail = paymentDetailRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new RuntimeException("Payment detail not found with id: " + id));
        assertYearMutable(detail);

        String oldValue = buildValueString(detail);
        detail.setActive(false);
        detail.setPermanentlyDeleted(true); // SUPPRESSION DÉFINITIVE - irréversible
        paymentDetailRepository.save(detail);

        paymentDetailAuditService.logAction(id, "DELETED", adminName, oldValue, buildValueString(detail), reason);
        recalculatePayment(detail.getPayment().getId());
    }

    @Transactional
    public PaymentDetailEntity reactivatePaymentDetail(Long id, String reason, String adminName) {
        validateReason(reason);

        PaymentDetailEntity detail = paymentDetailRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new RuntimeException("Payment detail not found with id: " + id));
        assertYearMutable(detail);

        if (detail.getActive() != null && detail.getActive()) {
            throw new IllegalStateException("Payment detail is already active");
        }

        // IMPORTANT: Empêcher la réactivation des suppressions définitives
        if (detail.getPermanentlyDeleted() != null && detail.getPermanentlyDeleted()) {
            throw new IllegalStateException(
                    "Cannot reactivate a permanently deleted payment detail. This deletion is irreversible.");
        }

        String oldValue = buildValueString(detail);
        detail.setActive(true);
        paymentDetailRepository.save(detail);

        paymentDetailAuditService.logAction(id, "REACTIVATED", adminName, oldValue, buildValueString(detail), reason);
        recalculatePayment(detail.getPayment().getId());

        return detail;
    }

    @Transactional
    public void recalculatePayment(Long paymentId) {
        PaymentEntity payment = paymentRepository.findById(Objects.requireNonNull(paymentId))
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));

        // Récupérer TOUS les PaymentDetails (actifs et inactifs)
        List<PaymentDetailEntity> allDetails = paymentDetailRepository.findByPaymentId(paymentId);

        // Vérifier si tous les PaymentDetails ont été définitivement supprimés
        boolean allPermanentlyDeleted = !allDetails.isEmpty() &&
                allDetails.stream()
                        .allMatch(detail -> detail.getPermanentlyDeleted() != null && detail.getPermanentlyDeleted());

        // Calculer le total payé (uniquement les actifs) en BigDecimal (audit H4).
        BigDecimal totalPaid = allDetails.stream()
                .filter(detail -> detail.getActive() != null && detail.getActive())
                .map(PaymentDetailEntity::getAmountPaid)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        payment.setAmountPaid(totalPaid.doubleValue());

        Optional<BigDecimal> monthTotalCost = resolveMonthTotalCost(payment);

        // LOGIQUE DE STATUT (cf. business-rules.md) :
        // 1. Tous les détails définitivement supprimés → CANCELLED ;
        // 2. coût du mois connu : versé >= coût → COMPLETED (couvre le coût nul d'un
        //    étudiant exempté), versé nul → PENDING, sinon IN_PROGRESS ;
        // 3. coût inconnu (série absente, résolution impossible) : on ne prétend jamais
        //    COMPLETED.
        if (allPermanentlyDeleted) {
            payment.setStatus("CANCELLED");
        } else if (monthTotalCost.isPresent() && totalPaid.compareTo(monthTotalCost.get()) >= 0) {
            payment.setStatus("COMPLETED");
        } else if (totalPaid.signum() <= 0) {
            payment.setStatus("PENDING");
        } else {
            payment.setStatus("IN_PROGRESS");
        }

        paymentRepository.save(payment);
    }

    /**
     * Coût total du mois pour le paiement, délégué au {@link PaymentCostResolver}.
     *
     * <p>L'ancien calcul local ({@code prix × totalSessions}, avec repli silencieux sur
     * {@code sessions = 1}) ignorait les réductions et divergeait de la source de vérité
     * monétaire : un étudiant exempté restait éternellement « en cours », et un paiement
     * mal rattaché à une série passait « soldé » dès le premier versement.</p>
     *
     * @return le coût du mois, ou {@link Optional#empty()} si l'information est
     *         indisponible (paiement sans étudiant ou sans série, série introuvable)
     */
    private Optional<BigDecimal> resolveMonthTotalCost(PaymentEntity payment) {
        if (payment.getStudent() == null || payment.getSessionSeries() == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(paymentCostResolver
                    .resolve(payment.getStudent().getId(), payment.getSessionSeries().getId())
                    .monthTotalCost());
        } catch (RuntimeException e) {
            LOGGER.warn("Coût du mois non résolu pour le paiement {} : {}", payment.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Refuse toute écriture sur un détail de paiement rattaché à une année scolaire
     * close (exigence 9.2). L'année est résolue via la séance, avec repli sur le groupe
     * du paiement.
     */
    private void assertYearMutable(PaymentDetailEntity detail) {
        if (detail.getSession() != null) {
            readOnlyYearGuard.assertSessionMutable(detail.getSession());
            return;
        }
        readOnlyYearGuard.assertGroupMutable(detail.getPayment() == null ? null : detail.getPayment().getGroup());
    }

    private void validateReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("Reason is required for audit logging.");
        }
    }

    private String buildValueString(PaymentDetailEntity detail) {
        return "PaymentDetail{" +
                "id=" + detail.getId() +
                ", amountPaid=" + detail.getAmountPaid() +
                ", active=" + detail.getActive() +
                ", sessionId=" + (detail.getSession() != null ? detail.getSession().getId() : null) +
                ", paymentId=" + (detail.getPayment() != null ? detail.getPayment().getId() : null) +
                '}';
    }
}
