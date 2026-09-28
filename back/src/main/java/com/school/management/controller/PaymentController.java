package com.school.management.controller;

import com.school.management.api.response.common.PageResponse;
import com.school.management.dto.PaymentDTO;
import com.school.management.dto.PaymentDetailDTO;
import com.school.management.dto.payment.PaymentAllocationResultDTO;
import com.school.management.dto.payment.PaymentCarryOverDTO;
import com.school.management.dto.payment.StudentPaymentHistoryDTO;
import com.school.management.dto.session.SessionDTO;
import com.school.management.mapper.PaymentMapper;
import com.school.management.mapper.SessionMapper;
import com.school.management.persistance.*;
import com.school.management.service.GroupPaymentStatus;
import com.school.management.service.StudentPaymentStatus;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.PaymentAllocationResult;
import com.school.management.service.payment.PaymentCrudService;
import com.school.management.service.payment.PaymentHistoryService;
import com.school.management.service.payment.PaymentProcessingService;
import com.school.management.service.payment.PaymentStatusService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller REST pour la gestion des paiements.
 *
 * PHASE 2 REFACTORING:
 * - Utilise les 4 services spécialisés au lieu du PaymentService monolithique
 * - Ajoute la pagination sur les endpoints de liste
 * - Utilise PageResponse pour un format de réponse cohérent
 *
 * Services utilisés:
 * - PaymentCrudService: opérations CRUD de base
 * - PaymentProcessingService: traitement des paiements
 * - PaymentStatusService: calculs de statuts et statistiques
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentController.class);

    // PHASE 2: Services spécialisés au lieu du service monolithique
    private final PaymentCrudService paymentCrudService;
    private final PaymentProcessingService paymentProcessingService;
    private final PaymentStatusService paymentStatusService;

    /** Restitution des reports dans l'historique de l'étudiant (exigences 6.2, 6.4). */
    private final PaymentHistoryService paymentHistoryService;

    private final SessionMapper sessionMapper;
    private final PaymentMapper paymentMapper;

    @Autowired
    public PaymentController(
            PaymentCrudService paymentCrudService,
            PaymentProcessingService paymentProcessingService,
            PaymentStatusService paymentStatusService,
            PaymentHistoryService paymentHistoryService,
            SessionMapper sessionMapper,
            PaymentMapper paymentMapper) {
        this.paymentCrudService = paymentCrudService;
        this.paymentProcessingService = paymentProcessingService;
        this.paymentStatusService = paymentStatusService;
        this.paymentHistoryService = paymentHistoryService;
        this.sessionMapper = sessionMapper;
        this.paymentMapper = paymentMapper;
    }

    /**
     * Crée un nouveau paiement de base.
     *
     * <p>Note libre facultative (requirement 11) : l'endpoint d'enregistrement de paiement
     * {@code POST /api/payments} porte le champ optionnel {@code notes}. Ce champ transite
     * par {@link PaymentMapper} qui le mappe dans les deux sens
     * ({@code PaymentDTO.notes ↔ PaymentEntity.notes}). Une note fournie est persistée avec
     * le paiement ; en son absence, {@code null} est persisté (requirement 11.1, 11.3), et
     * la note est renvoyée dans la réponse (requirement 11.2). Le chemin {@code /process}
     * (via {@code PaymentProcessingService} avec des arguments primitifs) ne porte pas de
     * note et reste inchangé, tout comme les endpoints multipart d'upload.</p>
     *
     * @param paymentDto les données du paiement
     * @return le paiement créé
     */
    @PostMapping
    public ResponseEntity<PaymentDTO> createPayment(@Valid @RequestBody PaymentDTO paymentDto) {
        LOGGER.info("Creating payment for student: {}", paymentDto.getStudentId());

        // Note: Pour utiliser PaymentMapper.toEntity, il faut un MappingContext
        // Pour l'instant, on utilise directement le service qui ne nécessite pas de
        // mapper
        // TODO: Créer un MappingContext dans PaymentCrudService si nécessaire

        PaymentEntity savedPayment = paymentCrudService.createPayment(
                paymentMapper.toEntity(paymentDto, null) // TODO: passer le mapping context
        );

        return new ResponseEntity<>(paymentMapper.toDto(savedPayment), HttpStatus.CREATED);
    }

    // PATCH /{id} générique retiré : il projetait une Map arbitraire du client sur l'entité
    // (ModelMapper), permettant d'écraser n'importe quel champ d'un paiement — dont le montant,
    // l'indicateur actif et les champs d'audit. Aucun écran ne l'utilisait. Les modifications
    // légitimes passent par les points d'entrée dédiés de l'administration des paiements, qui
    // tracent l'opération.

    /**
     * Récupère tous les paiements avec pagination.
     *
     * PHASE 2: Endpoint paginé pour éviter de charger tous les paiements.
     *
     * @param pageable paramètres de pagination (page, size, sort)
     * @return une page de paiements
     */
    @GetMapping
    public ResponseEntity<PageResponse<PaymentDTO>> getAllPayments(
            @PageableDefault(size = 20, sort = "paymentDate") Pageable pageable) {

        LOGGER.info("Fetching all payments - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<PaymentEntity> payments = paymentCrudService.getAllPaymentsPaginated(pageable);
        Page<PaymentDTO> paymentDTOs = payments.map(paymentMapper::toDto);

        return ResponseEntity.ok(PageResponse.of(paymentDTOs));
    }

    /**
     * Récupère tous les paiements d'un étudiant avec pagination.
     *
     * PHASE 2: Endpoint paginé.
     *
     * @param studentId l'ID de l'étudiant
     * @param pageable  paramètres de pagination
     * @return une page de paiements de l'étudiant
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<PageResponse<PaymentDTO>> getAllPaymentsForStudent(
            @PathVariable Long studentId,
            @PageableDefault(size = 20, sort = "paymentDate") Pageable pageable) {

        LOGGER.info("Fetching payments for student: {} - page: {}, size: {}",
                studentId, pageable.getPageNumber(), pageable.getPageSize());

        Page<PaymentEntity> payments = paymentCrudService.getAllPaymentsForStudentPaginated(studentId, pageable);
        Page<PaymentDTO> paymentDTOs = payments.map(paymentMapper::toDto);

        return ResponseEntity.ok(PageResponse.of(paymentDTOs));
    }

    /**
     * Met à jour un paiement (PUT - remplacement complet).
     *
     * @param id l'ID du paiement
     * @return le paiement mis à jour
     */
    @PutMapping("/{id}")
    public ResponseEntity<PaymentDTO> updatePayment(@PathVariable Long id) {
        LOGGER.info("Updating payment: {}", id);

        PaymentEntity updatedPayment = paymentCrudService.updatePayment(id);
        return ResponseEntity.ok(paymentMapper.toDto(updatedPayment));
    }

    /**
     * Traite un paiement pour une série complète de sessions.
     *
     * PHASE 2: Utilise PaymentProcessingService au lieu du service monolithique.
     *
     * Le montant est imputé sur la série visée à hauteur de son montant dû, puis la part
     * excédentaire est reportée sur les séries suivantes du groupe. La réponse décrit la
     * répartition complète : un versement ne crédite plus forcément une seule série
     * (exigence 6.3).
     *
     * @param paymentDto les informations du paiement
     * @return la répartition du versement : part imputée, parts reportées et séries destinataires
     */
    @PostMapping("/process")
    public ResponseEntity<PaymentAllocationResultDTO> processPayment(@Valid @RequestBody PaymentDTO paymentDto) {
        LOGGER.info("Processing payment - student: {}, group: {}, series: {}, amount: {}",
                paymentDto.getStudentId(), paymentDto.getGroupId(),
                paymentDto.getSessionSeriesId(), paymentDto.getAmountPaid());

        // PHASE 2: Utilise PaymentProcessingService
        PaymentAllocationResult result = paymentProcessingService.processPayment(
                paymentDto.getStudentId(),
                paymentDto.getGroupId(),
                paymentDto.getSessionSeriesId(),
                paymentDto.getAmountPaid());

        return ResponseEntity.ok(toDto(result));
    }

    /** Traduit le résultat d'encaissement en contrat d'API, la ligne de paiement comprise. */
    private PaymentAllocationResultDTO toDto(PaymentAllocationResult result) {
        return new PaymentAllocationResultDTO(
                result.studentId(),
                result.groupId(),
                result.seriesId(),
                result.amountReceived(),
                result.amountAllocated(),
                result.amountCarriedOver(),
                result.carryOvers().stream()
                        .map(carryOver -> new PaymentAllocationResultDTO.CarriedOverAmountDTO(
                                carryOver.seriesId(), carryOver.seriesName(), carryOver.amount()))
                        .toList(),
                paymentMapper.toDto(result.payment()));
    }

    /**
     * Traite un paiement de rattrapage pour une session précise.
     *
     * PHASE 2: Utilise PaymentProcessingService.processCatchUpPayment.
     *
     * @param paymentDto les informations du paiement (studentId, sessionId,
     *                   amountPaid)
     * @return le paiement traité
     */
    @PostMapping("/process/catch-up")
    public ResponseEntity<PaymentDTO> processCatchUpPayment(@Valid @RequestBody PaymentDTO paymentDto) {
        LOGGER.info("Processing catch-up payment - student: {}, session: {}, amount: {}",
                paymentDto.getStudentId(), paymentDto.getSessionId(), paymentDto.getAmountPaid());

        PaymentEntity processedPayment = paymentProcessingService.processCatchUpPayment(
                paymentDto.getStudentId(),
                paymentDto.getSessionId(),
                paymentDto.getAmountPaid());

        PaymentDTO responseDto = paymentMapper.toDto(processedPayment);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * Récupère le statut de paiement pour tous les étudiants d'un groupe.
     *
     * PHASE 2: Utilise PaymentStatusService.
     *
     * @param groupId l'ID du groupe
     * @return la liste des statuts de paiement des étudiants
     */
    @GetMapping("/{groupId}/students-payment-status")
    public ResponseEntity<List<StudentPaymentStatus>> getStudentsPaymentStatus(@PathVariable Long groupId) {
        LOGGER.info("Fetching payment status for group: {}", groupId);

        // PHASE 2: Utilise PaymentStatusService
        List<StudentPaymentStatus> paymentStatusList = paymentStatusService.getPaymentStatusForGroup(groupId);

        return ResponseEntity.ok(paymentStatusList);
    }

    /**
     * Récupère les sessions auxquelles un étudiant a assisté mais qu'il n'a pas
     * payées.
     *
     * PHASE 2: Utilise PaymentStatusService.
     *
     * @param studentId l'ID de l'étudiant
     * @return la liste des sessions impayées
     */
    @GetMapping("/students/{studentId}/unpaid-sessions")
    public ResponseEntity<List<SessionDTO>> getUnpaidAttendedSessions(@PathVariable Long studentId) {
        LOGGER.info("Fetching unpaid sessions for student: {}", studentId);

        // PHASE 2: Utilise PaymentStatusService
        List<SessionEntity> unpaidSessions = paymentStatusService.getUnpaidAttendedSessions(studentId);
        List<SessionDTO> sessionDTOs = unpaidSessions.stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();

        return ResponseEntity.ok(sessionDTOs);
    }

    /**
     * Récupère le statut de paiement détaillé pour un étudiant.
     *
     * PHASE 2: Utilise PaymentStatusService.
     *
     * Retourne le statut pour chaque groupe, série et session.
     *
     * @param studentId l'ID de l'étudiant
     * @return le statut de paiement par groupe
     */
    @Transactional
    @GetMapping("/students/{studentId}/payment-status")
    public ResponseEntity<List<GroupPaymentStatus>> getStudentPaymentStatus(@PathVariable Long studentId) {
        LOGGER.info("Fetching detailed payment status for student: {}", studentId);

        // PHASE 2: Utilise PaymentStatusService
        List<GroupPaymentStatus> paymentStatus = paymentStatusService.getPaymentStatusForStudent(studentId);

        return ResponseEntity.ok(paymentStatus);
    }

    /**
     * Récupère les détails de paiement pour une série.
     *
     * PHASE 2: Utilise PaymentCrudService.
     *
     * @param studentId       l'ID de l'étudiant
     * @param sessionSeriesId l'ID de la série de sessions
     * @return la liste des détails de paiement
     */
    @GetMapping("/process/{studentId}/series/{sessionSeriesId}/payment-details")
    public ResponseEntity<List<PaymentDetailDTO>> getPaymentDetailsForSeries(
            @PathVariable Long studentId,
            @PathVariable Long sessionSeriesId) {

        LOGGER.info("Fetching payment details for student: {} and series: {}", studentId, sessionSeriesId);

        // PHASE 2: Utilise PaymentCrudService
        List<PaymentDetailDTO> paymentDetails = paymentCrudService.getPaymentDetailsForSeries(studentId,
                sessionSeriesId);

        return ResponseEntity.ok(paymentDetails);
    }

    /**
     * Récupère l'historique des paiements pour une série.
     *
     * PHASE 2: Utilise PaymentCrudService.
     *
     * @param studentId       l'ID de l'étudiant
     * @param sessionSeriesId l'ID de la série de sessions
     * @return l'historique des paiements
     */
    @GetMapping("/process/{studentId}/series/{sessionSeriesId}/payment-history")
    public ResponseEntity<List<PaymentDTO>> getPaymentHistoryForSeries(
            @PathVariable Long studentId,
            @PathVariable Long sessionSeriesId) {

        LOGGER.info("Fetching payment history for student: {} and series: {}", studentId, sessionSeriesId);

        // PHASE 2: Utilise PaymentCrudService
        List<PaymentDTO> paymentHistory = paymentCrudService.getPaymentHistoryForSeries(studentId, sessionSeriesId);

        return ResponseEntity.ok(paymentHistory);
    }

    /**
     * Historique de paiement d'un étudiant, reports compris (exigences 6.2, 6.4).
     *
     * <p>Chaque ligne de série distingue la part saisie directement sur elle de la part reçue par
     * report depuis une autre série ; la liste des reports restitue chacun d'eux avec son montant,
     * sa série source et sa série destination. Sans cette distinction, une série créditée par
     * report affiche un montant sans origine, injustifiable lors d'un contrôle.</p>
     *
     * @param studentId l'identifiant de l'étudiant
     * @return l'historique complet, listes vides si l'étudiant n'a aucun versement
     */
    @GetMapping("/students/{studentId}/payment-history")
    public ResponseEntity<StudentPaymentHistoryDTO> getStudentPaymentHistory(@PathVariable Long studentId) {
        LOGGER.info("Fetching payment history with carry-overs for student: {}", studentId);
        return ResponseEntity.ok(paymentHistoryService.getStudentPaymentHistory(studentId));
    }

    /**
     * Reports produits par les versements d'un étudiant (exigence 6.2).
     *
     * @param studentId l'identifiant de l'étudiant
     * @return les reports actifs, par identifiant croissant ; liste vide si aucun
     */
    @GetMapping("/students/{studentId}/carry-overs")
    public ResponseEntity<List<PaymentCarryOverDTO>> getStudentCarryOvers(@PathVariable Long studentId) {
        LOGGER.info("Fetching carry-overs for student: {}", studentId);
        return ResponseEntity.ok(paymentHistoryService.getCarryOversForStudent(studentId));
    }

    /**
     * Gère les exceptions CustomServiceException.
     *
     * @param ex l'exception
     * @return la réponse d'erreur
     */
    @ExceptionHandler(CustomServiceException.class)
    public ResponseEntity<Map<String, String>> handleCustomServiceException(CustomServiceException ex) {
        LOGGER.error("CustomServiceException: {}", ex.getMessage());

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", ex.getMessage());

        return new ResponseEntity<>(errorResponse, Objects.requireNonNull(ex.getStatus()));
    }
}
