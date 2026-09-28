package com.school.management.controller;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.service.payment.PaymentQuoteService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Devis de paiement : ce qu'un étudiant doit pour une série, réduction appliquée.
 *
 * <p>Contrôleur mince : tout le calcul appartient au {@link PaymentQuoteService}. Le
 * formulaire de saisie consomme cet endpoint plutôt que de refaire le calcul à partir du
 * tarif catalogue, ce qui lui faisait ignorer les réductions.</p>
 */
@RestController
@RequestMapping("/api/payments/quote")
public class PaymentQuoteController {

    private final PaymentQuoteService paymentQuoteService;

    public PaymentQuoteController(PaymentQuoteService paymentQuoteService) {
        this.paymentQuoteService = paymentQuoteService;
    }

    /**
     * Retourne le devis d'un étudiant pour une série.
     *
     * @param studentId identifiant de l'étudiant
     * @param seriesId  identifiant de la série
     * @return le devis (tarif brut, taux de réduction, prix net, coûts, versé, plafond)
     */
    @GetMapping
    public ResponseEntity<PaymentQuoteDTO> getQuote(@RequestParam Long studentId,
            @RequestParam Long seriesId) {
        return ResponseEntity.ok(paymentQuoteService.quote(studentId, seriesId));
    }

    /**
     * Devis de toutes les séries d'un groupe pour un étudiant, dans l'ordre d'ajout des séries.
     *
     * <p>Permet au formulaire de saisie de savoir, avant tout choix, quelles séries n'ont plus
     * rien à encaisser — pour les présenter grisées plutôt que de laisser l'administrateur en
     * sélectionner une et découvrir le refus au moment de valider. Une seule requête, là où
     * interroger chaque série séparément en aurait demandé autant que de séries.</p>
     */
    @GetMapping("/group")
    public ResponseEntity<List<PaymentQuoteDTO>> getQuotesForGroup(@RequestParam Long studentId,
            @RequestParam Long groupId) {
        return ResponseEntity.ok(paymentQuoteService.quotesForGroup(studentId, groupId));
    }
}
