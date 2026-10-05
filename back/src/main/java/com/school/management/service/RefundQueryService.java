package com.school.management.service;

import com.school.management.dto.StudentRefundDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Lecture des remboursements, pour les historiques. Aucune écriture ici.
 *
 * <p>Séparé de {@link RefundService}, qui porte les règles d'écriture (plafond, motif, numéro de
 * pièce) : une lecture n'a besoin d'aucune d'elles, et les y mêler obligerait chaque test d'écriture
 * à construire aussi les dépôts lus ici. Même découpage que les Encaissements
 * ({@code EncashmentQueryService}).</p>
 *
 * <p>Données financières : les routes sont réservées au rôle ADMIN, lecture comprise
 * ({@code SecurityConfig}), comme les reçus de versement et le Journal.</p>
 */
@Service
public class RefundQueryService {

    private final RefundRepository refundRepository;
    private final StudentRepository studentRepository;
    private final PaymentRepository paymentRepository;

    public RefundQueryService(RefundRepository refundRepository, StudentRepository studentRepository,
                              PaymentRepository paymentRepository) {
        this.refundRepository = refundRepository;
        this.studentRepository = studentRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Remboursements actifs d'un étudiant, du plus ancien au plus récent.
     *
     * @throws CustomServiceException 404 si l'étudiant est introuvable : une liste vide laisserait
     *                                croire qu'il existe et n'a rien reçu
     */
    @Transactional(readOnly = true)
    public List<StudentRefundDTO> forStudent(Long studentId) {
        if (!studentRepository.existsById(Objects.requireNonNull(studentId, "studentId"))) {
            throw new CustomServiceException("Étudiant introuvable : " + studentId, HttpStatus.NOT_FOUND);
        }
        return refundRepository.findActiveForStudent(studentId).stream()
                .map(RefundQueryService::toDto)
                .toList();
    }

    /**
     * Remboursements actifs d'un versement, du plus ancien au plus récent : l'historique d'une ligne
     * de l'écran « Gestion des paiements », dont le montant affiché en est diminué.
     *
     * @throws CustomServiceException 404 si le versement est introuvable
     */
    @Transactional(readOnly = true)
    public List<StudentRefundDTO> forPayment(Long paymentId) {
        if (!paymentRepository.existsById(Objects.requireNonNull(paymentId, "paymentId"))) {
            throw new CustomServiceException("Versement introuvable : " + paymentId, HttpStatus.NOT_FOUND);
        }
        return refundRepository.findActiveForPayment(paymentId).stream()
                .map(RefundQueryService::toDto)
                .toList();
    }

    private static StudentRefundDTO toDto(RefundEntity refund) {
        PaymentEntity payment = refund.getPayment();
        SessionSeriesEntity series = payment != null ? payment.getSessionSeries() : null;
        GroupEntity group = payment != null ? payment.getGroup() : null;
        return new StudentRefundDTO(
                refund.getId(),
                refund.getRefundNumber(),
                refund.getRefundDate(),
                refund.getAmount() != null
                        ? refund.getAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2),
                refund.getReason(),
                series != null ? series.getId() : null,
                series != null ? series.getName() : null,
                group != null ? group.getId() : null,
                group != null ? group.getName() : null,
                payment != null ? payment.getId() : null,
                RefundReceiptService.recordedBy(refund));
    }
}
