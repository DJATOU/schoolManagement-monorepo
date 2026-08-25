package com.school.management.controller;

import com.school.management.dto.RefundRequestDTO;
import com.school.management.dto.RefundResponseDTO;
import com.school.management.mapper.RefundMapper;
import com.school.management.persistance.RefundEntity;
import com.school.management.service.RefundService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST des remboursements (refunds).
 *
 * <p>Contrôleur mince : la validation métier (montant ≤ montant versé du paiement
 * rattaché, aucun geste commercial) est entièrement déléguée à {@link RefundService}.
 * Le mapping entité → DTO passe par {@link RefundMapper}.</p>
 */
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;
    private final RefundMapper refundMapper;

    public RefundController(RefundService refundService, RefundMapper refundMapper) {
        this.refundService = refundService;
        this.refundMapper = refundMapper;
    }

    /**
     * Crée un remboursement après validation.
     *
     * @param request données de création
     * @return le remboursement créé (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<RefundResponseDTO> createRefund(@RequestBody RefundRequestDTO request) {
        RefundEntity created = refundService.create(request);
        return new ResponseEntity<>(refundMapper.toDto(created), HttpStatus.CREATED);
    }
}
