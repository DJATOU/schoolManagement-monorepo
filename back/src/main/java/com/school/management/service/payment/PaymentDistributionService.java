package com.school.management.service.payment;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service responsable de la distribution des paiements sur les sessions.
 *
 * Gère la logique de répartition d'un montant payé sur les différentes
 * sessions d'une série, en ordre chronologique.
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Service
public class PaymentDistributionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentDistributionService.class);

    private final SessionRepository sessionRepository;
    private final PaymentDetailRepository paymentDetailRepository;

    public PaymentDistributionService(
            SessionRepository sessionRepository,
            PaymentDetailRepository paymentDetailRepository) {
        this.sessionRepository = sessionRepository;
        this.paymentDetailRepository = paymentDetailRepository;
    }

    /**
     * Distribue un montant sur toutes les sessions d'une série en ordre chronologique.
     *
     * La distribution se fait session par session, en commençant par la première.
     * Si une session a déjà un PaymentDetail, on complète le montant manquant.
     *
     * @param payment le paiement à distribuer
     * @param sessionSeriesId l'ID de la série de sessions
     * @param amountPaid le montant à distribuer
     * @throws CustomServiceException si le paiement dépasse le coût total
     */
    @Transactional
    public void distributePayment(PaymentEntity payment, Long sessionSeriesId, double amountPaid) {
        LOGGER.info("Distributing payment {} of amount {} for series {}",
            payment.getId(), amountPaid, sessionSeriesId);

        // Récupérer toutes les sessions de la série, triées chronologiquement
        List<SessionEntity> sessions = sessionRepository
            .findBySessionSeriesId(sessionSeriesId)
            .stream()
            .sorted(Comparator.comparing(SessionEntity::getSessionTimeStart))
            .toList();

        if (sessions.isEmpty()) {
            LOGGER.warn("No sessions found for series: {}", sessionSeriesId);
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

        // Vérifier si le paiement dépasse le coût total
        double totalCost = calculateTotalCost(payment.getGroup());
        if (payment.getAmountPaid() >= totalCost) {
            double surplus = payment.getAmountPaid() - totalCost;
            if (surplus > 0) {
                throw new CustomServiceException(
                    "Le paiement a été complété. Le montant excédentaire de " + surplus + " euros sera remboursé.",
                    HttpStatus.OK
                );
            }
        }

        LOGGER.info("Payment distribution completed. Remaining: {}", remaining);
    }

    /**
     * Distribue un montant sur une session spécifique.
     *
     * @param payment le paiement
     * @param session la session
     * @param pricePerSession le prix par session
     * @param remaining le montant restant à distribuer
     * @return le montant restant après distribution sur cette session
     */
    private double distributeToSession(
            PaymentEntity payment,
            SessionEntity session,
            double pricePerSession,
            double remaining) {

        LOGGER.debug("Distributing to session {} - remaining: {}", session.getId(), remaining);

        Optional<PaymentDetailEntity> existingDetailOpt = paymentDetailRepository
            .findByPaymentIdAndSessionId(payment.getId(), session.getId());

        if (existingDetailOpt.isPresent()) {
            // Session déjà partiellement payée - compléter le montant
            PaymentDetailEntity detail = existingDetailOpt.get();
            double needed = pricePerSession - detail.getAmountPaid();

            if (needed > 0) {
                double toAdd = Math.min(needed, remaining);
                detail.setAmountPaid(detail.getAmountPaid() + toAdd);
                paymentDetailRepository.save(detail);

                LOGGER.debug("Updated payment detail {} - added: {}, new total: {}",
                    detail.getId(), toAdd, detail.getAmountPaid());

                return remaining - toAdd;
            }
        } else {
            // Nouvelle session - créer un PaymentDetail
            double toPay = Math.min(pricePerSession, remaining);
            PaymentDetailEntity newDetail = new PaymentDetailEntity();
            newDetail.setPayment(payment);
            newDetail.setSession(session);
            newDetail.setAmountPaid(toPay);
            newDetail.setIsCatchUp(false); // Pas un rattrapage
            paymentDetailRepository.save(newDetail);

            LOGGER.debug("Created new payment detail for session {} - amount: {}",
                session.getId(), toPay);

            return remaining - toPay;
        }

        return remaining;
    }

    /**
     * Calcule le coût total d'un groupe (prix par session × nombre de sessions dans la série)
     *
     * @param group le groupe
     * @return le coût total
     */
    private double calculateTotalCost(GroupEntity group) {
        double pricePerSession = group.getPrice().getPrice();
        int sessionNumberPerSerie = group.getSessionNumberPerSerie();
        return pricePerSession * sessionNumberPerSerie;
    }

    /**
     * Calcule le coût des sessions créées pour une série
     *
     * @param seriesId l'ID de la série
     * @param group le groupe
     * @return le coût des sessions créées
     */
    public double calculateCreatedSessionsCost(Long seriesId, GroupEntity group) {
        int totalSessions = sessionRepository.countBySessionSeriesId(seriesId);
        double pricePerSession = group.getPrice().getPrice();
        return totalSessions * pricePerSession;
    }

    /**
     * Vérifie si un paiement peut être traité (ne dépasse pas le coût des sessions créées)
     *
     * @param seriesId l'ID de la série
     * @param newTotalAmount le nouveau montant total
     * @param group le groupe
     * @return true si le paiement peut être traité
     */
    public boolean canProcessPayment(Long seriesId, double newTotalAmount, GroupEntity group) {
        double totalCreatedCost = calculateCreatedSessionsCost(seriesId, group);
        boolean canProcess = newTotalAmount <= totalCreatedCost;

        LOGGER.debug("Can process payment: {} (total: {}, created cost: {})",
            canProcess, newTotalAmount, totalCreatedCost);

        return canProcess;
    }
}
