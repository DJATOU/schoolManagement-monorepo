package com.school.management.service.payment;

import com.school.management.persistance.*;
import com.school.management.repository.*;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsable du traitement des paiements.
 *
 * Gère la logique métier complexe de traitement des paiements:
 * - Paiements pour une série complète
 * - Paiements de rattrapage pour une session unique
 * - Validation des montants et des limites
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Service
public class PaymentProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProcessingService.class);
    private static final String COMPLETED = "completed";
    private static final String IN_PROGRESS = "In Progress";

    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final SessionRepository sessionRepository;
    private final SessionSeriesRepository sessionSeriesRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final AttendanceRepository attendanceRepository;

    private final PaymentDistributionService distributionService;

    public PaymentProcessingService(
            PaymentRepository paymentRepository,
            StudentRepository studentRepository,
            GroupRepository groupRepository,
            SessionRepository sessionRepository,
            SessionSeriesRepository sessionSeriesRepository,
            PaymentDetailRepository paymentDetailRepository,
            AttendanceRepository attendanceRepository,
            PaymentDistributionService distributionService) {
        this.paymentRepository = paymentRepository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.attendanceRepository = attendanceRepository;
        this.distributionService = distributionService;
    }

    /**
     * Traite un paiement pour une série complète de sessions.
     *
     * Le montant est distribué sur toutes les sessions de la série en ordre chronologique.
     *
     * @param studentId l'ID de l'étudiant
     * @param groupId l'ID du groupe
     * @param sessionSeriesId l'ID de la série de sessions
     * @param amountPaid le montant payé
     * @return le paiement créé ou mis à jour
     * @throws CustomServiceException si le paiement dépasse les limites
     */
    @Transactional
    public PaymentEntity processPayment(Long studentId, Long groupId, Long sessionSeriesId, double amountPaid) {
        LOGGER.info("Processing series payment: student={}, group={}, series={}, amount={}",
            studentId, groupId, sessionSeriesId, amountPaid);

        // 1. Valider les entités
        StudentEntity student = getStudent(studentId);
        GroupEntity group = getGroup(groupId);
        SessionSeriesEntity series = getSessionSeries(sessionSeriesId);

        // 2. Calculer les coûts
        double totalSeriesCost = calculateTotalCost(group);
        Optional<PaymentEntity> existingPaymentOpt = paymentRepository
            .findByStudentIdAndGroupIdAndSessionSeriesId(studentId, groupId, sessionSeriesId);

        double currentTotalPaid = existingPaymentOpt.map(PaymentEntity::getAmountPaid).orElse(0.0);
        double newTotalAmount = currentTotalPaid + amountPaid;

        // 3. Vérifier que le nouveau total ne dépasse pas le coût total
        if (newTotalAmount > totalSeriesCost) {
            double surplus = newTotalAmount - totalSeriesCost;
            throw new CustomServiceException(
                "Le montant payé dépasse le coût total de la série de " + surplus + " euros.",
                HttpStatus.BAD_REQUEST
            );
        }

        // 4. Vérifier que le paiement ne dépasse pas le coût des sessions créées
        if (!distributionService.canProcessPayment(sessionSeriesId, newTotalAmount, group)) {
            throw new CustomServiceException(
                "Le paiement ne peut pas être effectué car il dépasse le coût des sessions créées.",
                HttpStatus.BAD_REQUEST
            );
        }

        // 5. Créer ou mettre à jour le paiement
        PaymentEntity payment = getOrCreateSeriesPayment(student, group, series, amountPaid);

        // 6. Distribuer le paiement sur les sessions
        distributionService.distributePayment(payment, sessionSeriesId, amountPaid);

        // 7. Sauvegarder et retourner
        PaymentEntity saved = paymentRepository.save(payment);
        LOGGER.info("Series payment processed successfully: paymentId={}", saved.getId());

        return saved;
    }

    /**
     * Traite un paiement de rattrapage pour une session unique.
     *
     * @param studentId l'ID de l'étudiant
     * @param sessionId l'ID de la session
     * @param amountPaid le montant payé
     * @return le paiement créé
     * @throws CustomServiceException si le montant dépasse le coût de la session
     */
    @Transactional
    public PaymentEntity processCatchUpPayment(Long studentId, Long sessionId, double amountPaid) {
        LOGGER.info("Processing catch-up payment: student={}, session={}, amount={}",
            studentId, sessionId, amountPaid);

        // 1. Valider les entités
        StudentEntity student = getStudent(studentId);
        SessionEntity session = resolveCatchUpSession(studentId, sessionId);

        // 2. Vérifier le montant
        double sessionCost = session.getGroup().getPrice().getPrice();
        if (amountPaid > sessionCost) {
            double surplus = amountPaid - sessionCost;
            throw new CustomServiceException(
                "Le montant payé dépasse le coût de la session de " + surplus + " euros.",
                HttpStatus.BAD_REQUEST
            );
        }

        // 3. Créer le paiement pour la session unique
        PaymentEntity payment = new PaymentEntity();
        payment.setStudent(student);
        payment.setGroup(session.getGroup());
        payment.setSession(session);  // Lien vers la session unique
        payment.setAmountPaid(amountPaid);
        payment.setStatus(amountPaid >= sessionCost ? COMPLETED : "in progress");
        PaymentEntity savedPayment = paymentRepository.save(payment);

        // 4. Créer le détail de paiement
        PaymentDetailEntity detail = new PaymentDetailEntity();
        detail.setPayment(savedPayment);
        detail.setSession(session);
        detail.setAmountPaid(amountPaid);
        detail.setIsCatchUp(true); // Marquer comme rattrapage
        paymentDetailRepository.save(detail);

        LOGGER.info("Catch-up payment processed successfully: paymentId={}", savedPayment.getId());

        return savedPayment;
    }

    private SessionEntity resolveCatchUpSession(Long studentId, Long requestedSessionId) {
        List<AttendanceEntity> catchUpAttendances = attendanceRepository
            .findByStudentIdAndIsCatchUp(studentId, true)
            .stream()
            .filter(att -> Boolean.TRUE.equals(att.getIsPresent()))
            .filter(att -> att.getSession() != null)
            .toList();

        if (catchUpAttendances.isEmpty()) {
            throw new CustomServiceException(
                "Aucune session de rattrapage assistée n'a été trouvée pour cet étudiant.",
                HttpStatus.BAD_REQUEST
            );
        }

        Set<Long> paidCatchUpSessionIds = paymentDetailRepository.findByPayment_StudentId(studentId)
            .stream()
            .filter(detail -> Boolean.TRUE.equals(detail.getIsCatchUp()))
            .map(detail -> detail.getSession().getId())
            .collect(Collectors.toSet());

        Optional<AttendanceEntity> requestedAttendance = catchUpAttendances.stream()
            .filter(att -> att.getSession().getId().equals(requestedSessionId))
            .findFirst();

        if (requestedAttendance.isPresent() && paidCatchUpSessionIds.contains(requestedSessionId)) {
            throw new CustomServiceException(
                "Cette session de rattrapage est déjà payée.",
                HttpStatus.BAD_REQUEST
            );
        }

        Optional<AttendanceEntity> targetAttendance = requestedAttendance;
        if (targetAttendance.isEmpty()) {
            targetAttendance = catchUpAttendances.stream()
                .filter(att -> !paidCatchUpSessionIds.contains(att.getSession().getId()))
                .min(Comparator.comparing(
                    att -> Optional.ofNullable(att.getSession().getSessionTimeStart()).orElse(new Date(0))
                ));
        }

        return targetAttendance
            .map(AttendanceEntity::getSession)
            .orElseThrow(() -> new CustomServiceException(
                "Aucune session de rattrapage impayée n'a été trouvée pour cet étudiant.",
                HttpStatus.BAD_REQUEST
            ));
    }

    /**
     * Crée un nouveau paiement pour une série ou met à jour un paiement existant.
     *
     * @param student l'étudiant
     * @param group le groupe
     * @param series la série de sessions
     * @param amountPaid le montant payé
     * @return le paiement créé ou mis à jour
     */
    private PaymentEntity getOrCreateSeriesPayment(
            StudentEntity student,
            GroupEntity group,
            SessionSeriesEntity series,
            double amountPaid) {

        LOGGER.debug("Getting or creating payment for student: {}, series: {}", student.getId(), series.getId());

        double totalCost = calculateTotalCost(group);
        Optional<PaymentEntity> existingOpt = paymentRepository
            .findByStudentIdAndGroupIdAndSessionSeriesId(
                student.getId(), group.getId(), series.getId()
            );

        PaymentEntity payment;
        if (existingOpt.isPresent()) {
            // Paiement existant - mettre à jour
            payment = existingOpt.get();
            double newTotal = payment.getAmountPaid() + amountPaid;

            // Vérifier le coût des sessions créées
            double createdSessionsCost = distributionService.calculateCreatedSessionsCost(series.getId(), group);
            if (newTotal > createdSessionsCost) {
                throw new CustomServiceException(
                    "Le paiement total dépasse le coût des sessions créées.",
                    HttpStatus.BAD_REQUEST
                );
            }

            payment.setAmountPaid(newTotal);
            payment.setStatus(newTotal >= totalCost ? COMPLETED : IN_PROGRESS);

            LOGGER.debug("Updated existing payment: {} - new total: {}", payment.getId(), newTotal);
        } else {
            // Nouveau paiement - créer
            payment = new PaymentEntity();
            payment.setStudent(student);
            payment.setGroup(group);
            payment.setSessionSeries(series);
            payment.setAmountPaid(amountPaid);
            payment.setStatus(amountPaid >= totalCost ? COMPLETED : IN_PROGRESS);

            LOGGER.debug("Created new payment for amount: {}", amountPaid);
        }

        return paymentRepository.save(payment);
    }

    /**
     * Calcule le coût total d'un groupe (prix par session × nombre de sessions dans la série).
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
     * Récupère un étudiant par son ID.
     *
     * @param studentId l'ID de l'étudiant
     * @return l'étudiant
     * @throws RuntimeException si l'étudiant n'existe pas
     */
    private StudentEntity getStudent(Long studentId) {
        return studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));
    }

    /**
     * Récupère un groupe par son ID.
     *
     * @param groupId l'ID du groupe
     * @return le groupe
     * @throws RuntimeException si le groupe n'existe pas
     */
    private GroupEntity getGroup(Long groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("Group not found with ID: " + groupId));
    }

    /**
     * Récupère une série de sessions par son ID.
     *
     * @param seriesId l'ID de la série
     * @return la série
     * @throws RuntimeException si la série n'existe pas
     */
    private SessionSeriesEntity getSessionSeries(Long seriesId) {
        return sessionSeriesRepository.findById(seriesId)
            .orElseThrow(() -> new RuntimeException("Series not found with ID: " + seriesId));
    }
}
