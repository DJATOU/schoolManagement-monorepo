package com.school.management.service.payment;

import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.repository.PaymentDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * NOUVELLE CLASSE - Service pour gérer la désactivation des PaymentDetails.
 *
 * Cette classe est utilisée pour désactiver les PaymentDetails quand une session
 * est dévalidée, afin d'éviter que ces paiements soient comptés dans les calculs futurs.
 *
 * POURQUOI CETTE CLASSE ?
 * Quand vous dévalidez une session, les PaymentDetails restaient actifs, ce qui causait
 * l'erreur "Le montant dépasse le coût total". Cette classe résout ce problème.
 *
 * @author Claude Code
 * @since Décembre 2024
 */
@Service
public class PaymentDetailDeactivationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentDetailDeactivationService.class);

    private final PaymentDetailRepository paymentDetailRepository;

    public PaymentDetailDeactivationService(PaymentDetailRepository paymentDetailRepository) {
        this.paymentDetailRepository = paymentDetailRepository;
    }

    /**
     * Désactive tous les PaymentDetails associés à une session.
     *
     * QUAND L'UTILISER :
     * - Appelée automatiquement par SessionService.deactivateSession()
     * - Quand vous dévalidez une session
     *
     * @param sessionId l'ID de la session à désactiver
     * @return le nombre de PaymentDetails désactivés
     */
    @Transactional
    public int deactivatePaymentDetailsBySessionId(Long sessionId) {
        LOGGER.info("Deactivating all payment details for session: {}", sessionId);

        List<PaymentDetailEntity> paymentDetails = paymentDetailRepository.findBySessionId(sessionId);

        if (paymentDetails.isEmpty()) {
            LOGGER.debug("No payment details found for session: {}", sessionId);
            return 0;
        }

        int count = 0;
        for (PaymentDetailEntity detail : paymentDetails) {
            if (Boolean.TRUE.equals(detail.getActive())) {
                detail.setActive(false);
                paymentDetailRepository.save(detail);
                count++;
                LOGGER.debug("Deactivated payment detail {} for session {} (student: {}, amount: {})",
                        detail.getId(), sessionId,
                        detail.getPayment().getStudent().getId(),
                        detail.getAmountPaid());
            }
        }

        LOGGER.info("Successfully deactivated {} payment details for session {}", count, sessionId);
        return count;
    }

    /**
     * Réactive tous les PaymentDetails associés à une session.
     *
     * QUAND L'UTILISER :
     * - Pour annuler une dévalidation de session
     * - Quand vous réactivez une session
     *
     * @param sessionId l'ID de la session à réactiver
     * @return le nombre de PaymentDetails réactivés
     */
    @Transactional
    public int reactivatePaymentDetailsBySessionId(Long sessionId) {
        LOGGER.info("Reactivating all payment details for session: {}", sessionId);

        List<PaymentDetailEntity> paymentDetails = paymentDetailRepository.findBySessionId(sessionId);

        if (paymentDetails.isEmpty()) {
            LOGGER.debug("No payment details found for session: {}", sessionId);
            return 0;
        }

        int count = 0;
        for (PaymentDetailEntity detail : paymentDetails) {
            if (Boolean.FALSE.equals(detail.getActive())) {
                detail.setActive(true);
                paymentDetailRepository.save(detail);
                count++;
                LOGGER.debug("Reactivated payment detail {} for session {}", detail.getId(), sessionId);
            }
        }

        LOGGER.info("Successfully reactivated {} payment details for session {}", count, sessionId);
        return count;
    }

    /**
     * Compte le nombre de PaymentDetails actifs pour une session.
     *
     * QUAND L'UTILISER :
     * - Pour vérifier si une session a des paiements actifs avant de la supprimer
     * - Pour des statistiques
     *
     * @param sessionId l'ID de la session
     * @return le nombre de PaymentDetails actifs
     */
    public int countActivePaymentDetailsBySessionId(Long sessionId) {
        List<PaymentDetailEntity> activeDetails = paymentDetailRepository.findActiveBySessionId(sessionId);
        return activeDetails.size();
    }
}