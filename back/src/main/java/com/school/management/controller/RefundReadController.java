package com.school.management.controller;

import com.school.management.dto.StudentRefundDTO;
import com.school.management.service.RefundQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lecture des remboursements pour les historiques : numéro de pièce, date, montant, motif, auteur,
 * et de quoi réimprimer le reçu.
 *
 * <ul>
 *   <li>{@code GET /api/students/{id}/refunds} : ceux d'un élève, sous chaque série de son
 *       historique complet ;</li>
 *   <li>{@code GET /api/refunds/payment/{id}} : ceux d'un versement, dans l'historique d'une ligne
 *       de l'écran « Gestion des paiements ».</li>
 * </ul>
 *
 * <p>Réservées au rôle ADMIN en lecture ({@code SecurityConfig}) : ce sont des pièces de caisse,
 * comme les reçus de versement. Le total remboursé par série reste dans l'historique, visible des deux
 * rôles. Les écritures (création, reçu) restent dans {@link RefundController}.</p>
 */
@RestController
@RequestMapping("/api")
public class RefundReadController {

    private final RefundQueryService refundQueryService;

    public RefundReadController(RefundQueryService refundQueryService) {
        this.refundQueryService = refundQueryService;
    }

    /** Remboursements actifs de l'élève, du plus ancien au plus récent. */
    @GetMapping("/students/{studentId}/refunds")
    public ResponseEntity<List<StudentRefundDTO>> getStudentRefunds(@PathVariable Long studentId) {
        return ResponseEntity.ok(refundQueryService.forStudent(studentId));
    }

    /** Remboursements actifs d'un versement, du plus ancien au plus récent. */
    @GetMapping("/refunds/payment/{paymentId}")
    public ResponseEntity<List<StudentRefundDTO>> getPaymentRefunds(@PathVariable Long paymentId) {
        return ResponseEntity.ok(refundQueryService.forPayment(paymentId));
    }
}
