package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailSearchDTO;
import com.school.management.dto.PaymentDetailUpdateDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

@Service
public class PaymentDetailAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentDetailAdminService.class);

    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailAuditService paymentDetailAuditService;
    private final ReadOnlyYearGuard readOnlyYearGuard;

    /** Seule source du cumul et du statut d'une série : la somme des Imputations actives. */
    private final EncashmentService encashmentService;

    @Autowired
    public PaymentDetailAdminService(PaymentDetailRepository paymentDetailRepository,
            PaymentRepository paymentRepository,
            PaymentDetailAuditService paymentDetailAuditService,
            ReadOnlyYearGuard readOnlyYearGuard,
            EncashmentService encashmentService) {
        this.paymentDetailRepository = paymentDetailRepository;
        this.paymentRepository = paymentRepository;
        this.paymentDetailAuditService = paymentDetailAuditService;
        this.readOnlyYearGuard = readOnlyYearGuard;
        this.encashmentService = encashmentService;
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
            if (Boolean.TRUE.equals(updateDTO.getActive()) && !Boolean.TRUE.equals(detail.getActive())) {
                assertNotFromCancelledEncashment(detail);
            }
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
        assertNotFromCancelledEncashment(detail);

        String oldValue = buildValueString(detail);
        detail.setActive(true);
        paymentDetailRepository.save(detail);

        paymentDetailAuditService.logAction(id, "REACTIVATED", adminName, oldValue, buildValueString(detail), reason);
        recalculatePayment(detail.getPayment().getId());

        return detail;
    }

    /**
     * Recalcule le statut de la ligne de paiement après une écriture sur sa ventilation.
     *
     * <p><b>Le cumul ne vient plus de la ventilation</b> (spec admin-corrections, défaut 2). Il
     * remplaçait le montant versé par la somme des lignes actives : toute part non ventilée
     * disparaissait du registre à la première correction d'une ligne, et rien n'empêchait le
     * cumul de passer sous le total déjà remboursé. Le cumul est désormais la somme des
     * Imputations actives, que seul un Encaissement fait varier. Corriger une ligne de
     * ventilation ne crée ni ne détruit d'argent reçu.</p>
     *
     * <p>Le statut suit {@link PaymentLineStatus}, comme pour un encaissement ou son annulation.
     * La règle « toutes les lignes supprimées définitivement : CANCELLED » disparaît avec : une
     * ligne annulée sortait du statut et des devis tout en gardant son argent, et le versement
     * suivant ouvrait une seconde ligne pour la même série.</p>
     */
    @Transactional
    public void recalculatePayment(Long paymentId) {
        PaymentEntity payment = paymentRepository.findByIdForUpdate(Objects.requireNonNull(paymentId))
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
        encashmentService.refreshSeriesCumul(payment);
    }

    /**
     * Refuse de rendre active une ligne d'un Encaissement annulé : l'argent qu'elle ventile n'est
     * plus au registre, et la réactiver le ferait revenir dans les recettes (spec
     * admin-corrections, inventaire A.1).
     */
    private void assertNotFromCancelledEncashment(PaymentDetailEntity detail) {
        if (detail.getEncashmentAllocation() != null
                && !Boolean.TRUE.equals(detail.getEncashmentAllocation().getActive())) {
            throw new CustomServiceException("Cette ligne appartient à un encaissement annulé : elle ne peut "
                    + "pas être réactivée. L'argent qu'elle ventilait ne figure plus au registre.",
                    HttpStatus.CONFLICT);
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
