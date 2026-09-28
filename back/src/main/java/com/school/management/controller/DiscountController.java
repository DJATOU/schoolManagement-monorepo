package com.school.management.controller;

import com.school.management.dto.DiscountRequestDTO;
import com.school.management.dto.DiscountResponseDTO;
import com.school.management.persistance.DiscountEntity;
import com.school.management.service.DiscountService;
import com.school.management.service.DiscountViewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contrôleur REST des réductions (discounts).
 *
 * <p>Contrôleur mince : la validation métier (portée unique, taux dans
 * {@code [0.00, 1.00]}, absence de conflit) est entièrement déléguée à
 * {@link DiscountService}. L'enrichissement des réponses pour l'affichage (noms de
 * l'étudiant et de la cible) est délégué à {@link DiscountViewService}.</p>
 */
@RestController
@RequestMapping("/api/discounts")
public class DiscountController {

    private final DiscountService discountService;
    private final DiscountViewService discountViewService;

    public DiscountController(DiscountService discountService,
                              DiscountViewService discountViewService) {
        this.discountService = discountService;
        this.discountViewService = discountViewService;
    }

    /** Corps de requête de mise à jour du taux d'une réduction. */
    public record UpdateRateRequest(BigDecimal rate) {
    }

    /**
     * Crée une réduction après validation.
     *
     * @param request données de création
     * @return la réduction créée (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<DiscountResponseDTO> createDiscount(@RequestBody DiscountRequestDTO request) {
        DiscountEntity created = discountService.create(request);
        return new ResponseEntity<>(discountViewService.toDisplayDto(created), HttpStatus.CREATED);
    }

    /**
     * Liste les réductions, enrichies des libellés d'affichage.
     *
     * <p>Avec {@code studentId}, seules les réductions de cet étudiant sont renvoyées : la
     * fiche étudiante doit pouvoir annoncer « 65 % de réduction » sans télécharger puis
     * filtrer la totalité des réductions de l'école.</p>
     *
     * @param studentId identifiant de l'étudiant à filtrer (optionnel)
     * @return la liste des réductions (HTTP 200)
     */
    @GetMapping
    public ResponseEntity<List<DiscountResponseDTO>> getAllDiscounts(
            @RequestParam(required = false) Long studentId) {
        return ResponseEntity.ok(discountViewService.findForDisplay(studentId));
    }

    /**
     * Met à jour le taux d'une réduction.
     *
     * @param id      identifiant de la réduction
     * @param request nouveau taux
     * @return la réduction mise à jour (HTTP 200)
     */
    @PutMapping("/{id}")
    public ResponseEntity<DiscountResponseDTO> updateRate(@PathVariable Long id,
                                                          @RequestBody UpdateRateRequest request) {
        DiscountEntity updated = discountService.updateRate(id, request.rate());
        return ResponseEntity.ok(discountViewService.toDisplayDto(updated));
    }

    /**
     * Supprime une réduction.
     *
     * @param id identifiant de la réduction
     * @return HTTP 204 sans contenu
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDiscount(@PathVariable Long id) {
        discountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
