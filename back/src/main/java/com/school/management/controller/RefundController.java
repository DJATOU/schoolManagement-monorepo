package com.school.management.controller;

import com.school.management.dto.RefundCapDTO;
import com.school.management.dto.RefundReceiptDTO;
import com.school.management.dto.RefundRequestDTO;
import com.school.management.dto.RefundResponseDTO;
import com.school.management.mapper.RefundMapper;
import com.school.management.persistance.RefundEntity;
import com.school.management.service.RefundReceiptService;
import com.school.management.service.RefundService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST des remboursements (refunds).
 *
 * <p>Contrôleur mince : toute la validation métier — montant, motif, plafond cumulé par paiement —
 * est déléguée à {@link RefundService}. Le mapping entité → DTO passe par {@link RefundMapper}.</p>
 *
 * <p><strong>Autorisations.</strong> Aucune annotation de sécurité n'est posée ici, et c'est
 * volontaire : les règles de {@code SecurityConfig} portent sur la méthode HTTP appliquée à
 * {@code /api/**}. La création (POST) est donc réservée à ADMIN, et la lecture du plafond (GET) est
 * ouverte aux deux rôles — constater ce qui reste remboursable n'est pas une écriture
 * (exigences 6.11, 7.10). Ajouter des annotations dupliquerait la règle en deux endroits qui
 * pourraient ensuite diverger.</p>
 */
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;
    private final RefundMapper refundMapper;
    private final RefundReceiptService refundReceiptService;

    public RefundController(RefundService refundService,
                           RefundMapper refundMapper,
                           RefundReceiptService refundReceiptService) {
        this.refundService = refundService;
        this.refundMapper = refundMapper;
        this.refundReceiptService = refundReceiptService;
    }

    /**
     * Crée un remboursement après validation du montant, du motif et du plafond cumulé.
     *
     * <p>La réponse porte le numéro de pièce, le motif et le plafond restant après enregistrement,
     * afin que l'interface puisse proposer le reçu et actualiser ses montants sans second appel.</p>
     *
     * @param request données de création
     * @return le remboursement créé (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<RefundResponseDTO> createRefund(@RequestBody RefundRequestDTO request) {
        RefundEntity created = refundService.create(request);
        RefundCapDTO cap = refundService.cap(created.getPayment().getId());
        return new ResponseEntity<>(
                refundMapper.toDto(created, cap.refundableCap()), HttpStatus.CREATED);
    }

    /**
     * Montant versé, somme déjà remboursée et plafond restant d'un paiement (exigence 7.10).
     *
     * <p>Les trois montants sont retournés ensemble : afficher un plafond sans dire ce qui l'a
     * consommé obligerait l'administrateur à chercher l'information ailleurs.</p>
     */
    @GetMapping("/payment/{paymentId}/cap")
    public ResponseEntity<RefundCapDTO> refundableCap(@PathVariable Long paymentId) {
        return ResponseEntity.ok(refundService.cap(paymentId));
    }

    /**
     * Émet le reçu d'un remboursement et retourne ses données (exigence 8).
     *
     * <p><strong>POST et non GET</strong> : chaque émission est enregistrée, afin de signaler les
     * réimpressions par la mention « Duplicata » (exigence 8.10). Un reçu de caisse réimprimé sans
     * mention peut servir deux fois, c'est donc bien une création de ressource.</p>
     *
     * <p>Le rendu du document reste côté client, par cohérence avec le reçu de versement : ce
     * service fournit les données, y compris les mentions de repli, déjà résolues.</p>
     */
    @PostMapping("/{id}/receipts")
    public ResponseEntity<RefundReceiptDTO> issueReceipt(@PathVariable Long id) {
        return new ResponseEntity<>(refundReceiptService.issue(id), HttpStatus.CREATED);
    }
}
