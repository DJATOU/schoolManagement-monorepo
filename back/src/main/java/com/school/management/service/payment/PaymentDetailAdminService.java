package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailSearchDTO;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/**
 * Consultation des lignes de ventilation pour l'écran « Gestion des paiements ».
 *
 * <h2>Une ligne ne se corrige plus à l'unité (spec admin-corrections, A.6)</h2>
 * Cet écran permettait de modifier le montant d'une ligne, de la désactiver, de la supprimer
 * définitivement ou de la réactiver. Depuis que l'argent reçu est porté par l'Encaissement, une
 * ligne n'est plus que la part, sur une séance, d'un versement enregistré tel qu'il a eu lieu :
 * <ul>
 *   <li>la modifier ne corrige pas le versement — le reçu, le cumul et le dû restent ceux de
 *       l'Encaissement ;</li>
 *   <li>mais elle fait diverger les recettes, qui somment la ventilation, de l'argent au
 *       registre : deux écrans affichent deux montants pour le même versement.</li>
 * </ul>
 * Ces actions sont donc refusées, avec le reçu à corriger. Un versement se corrige par
 * l'Annulation ou le Remplacement de son Encaissement (lot B), qui réécrit sa ventilation avec
 * lui. L'historique d'audit des lignes reste consultable.
 */
@Service
public class PaymentDetailAdminService {

    private final PaymentDetailRepository paymentDetailRepository;

    @Autowired
    public PaymentDetailAdminService(PaymentDetailRepository paymentDetailRepository) {
        this.paymentDetailRepository = paymentDetailRepository;
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
        return findDetail(id);
    }

    /**
     * Refuse toute correction d'une ligne de ventilation — montant, désactivation, suppression,
     * réactivation — en nommant le reçu à corriger à la place.
     *
     * @throws CustomServiceException 404 si la ligne est introuvable, 409 sinon
     */
    @Transactional(readOnly = true)
    public void refuseLineCorrection(Long id) {
        PaymentDetailEntity detail = findDetail(id);
        String receipt = receiptOf(detail);
        throw new CustomServiceException("Une ligne par séance ne se corrige pas à l'unité : elle est la part du "
                + "reçu " + receipt + ", enregistré tel qu'il a été encaissé. Pour corriger le montant ou "
                + "retirer ce versement, annulez ou corrigez le reçu " + receipt + " depuis la fiche de l'élève : "
                + "sa répartition par séance suivra.",
                HttpStatus.CONFLICT);
    }

    private PaymentDetailEntity findDetail(Long id) {
        return paymentDetailRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new CustomServiceException(
                        "Ligne de paiement introuvable : " + id, HttpStatus.NOT_FOUND));
    }

    private static String receiptOf(PaymentDetailEntity detail) {
        EncashmentAllocationEntity allocation = detail.getEncashmentAllocation();
        if (allocation == null || allocation.getEncashment() == null) {
            return "d'origine";
        }
        return allocation.getEncashment().getReceiptNumber();
    }
}
