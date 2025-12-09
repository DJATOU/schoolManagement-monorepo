package com.school.management.service.payment;

import com.school.management.dto.PaymentDTO;
import com.school.management.dto.PaymentDetailDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service CRUD de base pour les paiements.
 *
 * Responsabilités:
 * - Créer, lire, mettre à jour, supprimer des paiements
 * - Récupérer les paiements d'un étudiant
 * - Convertir les entités en DTOs
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Service
public class PaymentCrudService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCrudService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;

    public PaymentCrudService(
            PaymentRepository paymentRepository,
            PaymentDetailRepository paymentDetailRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentDetailRepository = paymentDetailRepository;
    }

    /**
     * Récupère tous les paiements.
     *
     * @return la liste de tous les paiements
     */
    @Transactional(readOnly = true)
    public List<PaymentEntity> getAllPayments() {
        LOGGER.debug("Fetching all payments");
        return paymentRepository.findAll();
    }

    /**
     * Récupère tous les paiements avec pagination.
     *
     * @param pageable les paramètres de pagination
     * @return une page de paiements
     */
    @Transactional(readOnly = true)
    public Page<PaymentEntity> getAllPaymentsPaginated(Pageable pageable) {
        LOGGER.debug("Fetching all payments - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return paymentRepository.findAll(pageable);
    }

    /**
     * Récupère un paiement par son ID.
     *
     * @param id l'ID du paiement
     * @return le paiement
     * @throws ResourceNotFoundException si le paiement n'existe pas
     */
    @Transactional(readOnly = true)
    public PaymentEntity getPaymentById(Long id) {
        LOGGER.debug("Fetching payment by ID: {}", id);
        return paymentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    /**
     * Crée un nouveau paiement.
     *
     * @param payment le paiement à créer
     * @return le paiement créé
     */
    @Transactional
    public PaymentEntity createPayment(PaymentEntity payment) {
        LOGGER.info("Creating new payment for student: {}", payment.getStudent().getId());
        PaymentEntity saved = paymentRepository.save(payment);
        LOGGER.debug("Payment created with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Met à jour un paiement existant.
     *
     * @param id l'ID du paiement à mettre à jour
     * @return le paiement mis à jour
     * @throws ResourceNotFoundException si le paiement n'existe pas
     */
    @Transactional
    public PaymentEntity updatePayment(Long id) {
        LOGGER.info("Updating payment with ID: {}", id);
        PaymentEntity existingPayment = getPaymentById(id);
        // Les modifications spécifiques doivent être faites avant d'appeler cette méthode
        return paymentRepository.save(existingPayment);
    }

    /**
     * Sauvegarde ou met à jour un paiement.
     *
     * @param payment le paiement à sauvegarder
     * @return le paiement sauvegardé
     */
    @Transactional
    public PaymentEntity save(PaymentEntity payment) {
        LOGGER.debug("Saving payment: {}", payment.getId());
        return paymentRepository.save(payment);
    }

    /**
     * Récupère tous les paiements d'un étudiant, triés par date décroissante.
     *
     * @param studentId l'ID de l'étudiant
     * @return la liste des paiements de l'étudiant
     */
    @Transactional(readOnly = true)
    public List<PaymentEntity> getAllPaymentsForStudent(Long studentId) {
        LOGGER.debug("Fetching all payments for student: {}", studentId);
        return paymentRepository.findAllByStudentIdOrderByPaymentDateDesc(studentId);
    }

    /**
     * Récupère tous les paiements d'un étudiant avec pagination.
     *
     * @param studentId l'ID de l'étudiant
     * @param pageable les paramètres de pagination
     * @return une page de paiements
     */
    @Transactional(readOnly = true)
    public Page<PaymentEntity> getAllPaymentsForStudentPaginated(Long studentId, Pageable pageable) {
        LOGGER.debug("Fetching payments for student: {} - page: {}, size: {}",
            studentId, pageable.getPageNumber(), pageable.getPageSize());
        return paymentRepository.findAllByStudentId(studentId, pageable);
    }

    /**
     * Récupère l'historique des paiements pour une série.
     *
     * @param studentId l'ID de l'étudiant
     * @param sessionSeriesId l'ID de la série de sessions
     * @return la liste des paiements sous forme de DTOs
     */
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentHistoryForSeries(Long studentId, Long sessionSeriesId) {
        LOGGER.info("Fetching payment history for student: {} and series: {}", studentId, sessionSeriesId);
        List<PaymentEntity> payments = paymentRepository
            .findAllByStudentIdAndSessionSeriesId(studentId, sessionSeriesId);
        LOGGER.debug("Found {} payments", payments.size());

        return payments.stream()
            .map(this::convertToDto)
            .toList();
    }

    /**
     * Récupère les détails de paiement pour une série.
     *
     * @param studentId l'ID de l'étudiant
     * @param sessionSeriesId l'ID de la série de sessions
     * @return la liste des détails de paiement
     */
    @Transactional(readOnly = true)
    public List<PaymentDetailDTO> getPaymentDetailsForSeries(Long studentId, Long sessionSeriesId) {
        LOGGER.info("Fetching payment details for student: {} and series: {}", studentId, sessionSeriesId);
        List<PaymentDetailEntity> details = paymentDetailRepository
            .findByPayment_StudentIdAndSession_SessionSeriesId(studentId, sessionSeriesId);
        LOGGER.debug("Found {} payment details", details.size());

        return details.stream()
            .map(this::convertToPaymentDetailDto)
            .toList();
    }

    /**
     * Convertit une entité PaymentEntity en PaymentDTO.
     *
     * @param payment l'entité à convertir
     * @return le DTO
     */
    public PaymentDTO convertToDto(PaymentEntity payment) {
        return PaymentDTO.builder()
            .studentId(payment.getStudent().getId())
            .groupId(payment.getGroup() != null ? payment.getGroup().getId() : null)
            .sessionSeriesId(payment.getSessionSeries() != null ? payment.getSessionSeries().getId() : null)
            .sessionId(payment.getSession() != null ? payment.getSession().getId() : null)
            .amountPaid(payment.getAmountPaid())
            .status(payment.getStatus())
            .paymentMethod(payment.getPaymentMethod())
            .paymentDescription(payment.getDescription())
            .totalSeriesCost(calculateTotalSeriesCost(payment))
            .totalPaidForSeries(calculateTotalPaidForSeries(payment))
            .amountOwed(calculateAmountOwed(payment))
            .build();
    }

    /**
     * Convertit une entité PaymentDetailEntity en PaymentDetailDTO.
     *
     * @param detail l'entité à convertir
     * @return le DTO
     */
    private PaymentDetailDTO convertToPaymentDetailDto(PaymentDetailEntity detail) {
        return PaymentDetailDTO.builder()
            .paymentDetailId(detail.getId())
            .sessionId(detail.getSession().getId())
            .sessionName(detail.getSession().getTitle())
            .amountPaid(detail.getAmountPaid())
            .remainingBalance(detail.getSession().getGroup().getPrice().getPrice() - detail.getAmountPaid())
            .build();
    }

    /**
     * Calcule le coût total d'une série.
     *
     * @param payment le paiement
     * @return le coût total
     */
    private Double calculateTotalSeriesCost(PaymentEntity payment) {
        if (payment.getGroup() == null || payment.getSessionSeries() == null) {
            return 0.0;
        }
        double pricePerSession = payment.getGroup().getPrice().getPrice();
        int sessionCount = payment.getSessionSeries().getSessions().size();
        return pricePerSession * sessionCount;
    }

    /**
     * Calcule le montant total payé pour une série.
     *
     * @param payment le paiement
     * @return le montant total payé
     */
    private Double calculateTotalPaidForSeries(PaymentEntity payment) {
        return payment.getAmountPaid();
    }

    /**
     * Calcule le montant restant dû.
     *
     * @param payment le paiement
     * @return le montant dû
     */
    private Double calculateAmountOwed(PaymentEntity payment) {
        return calculateTotalSeriesCost(payment) - calculateTotalPaidForSeries(payment);
    }
}
