package com.school.management.controller;

import com.school.management.dto.revenue.GroupRevenueDTO;
import com.school.management.service.payment.GroupRevenueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relevé d'encaissements d'un groupe.
 *
 * <p>Réservé au rôle ADMIN : la règle est posée dans {@code SecurityConfig} pour ce chemin,
 * avant la règle générique qui ouvre les lectures {@code GET /api/**} aux deux rôles. Le
 * masquage côté interface n'est qu'un confort, la restriction vit ici.</p>
 */
@RestController
@RequestMapping("/api/groups")
public class GroupRevenueController {

    private final GroupRevenueService groupRevenueService;

    public GroupRevenueController(GroupRevenueService groupRevenueService) {
        this.groupRevenueService = groupRevenueService;
    }

    @GetMapping("/{groupId}/revenue")
    public ResponseEntity<GroupRevenueDTO> getGroupRevenue(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupRevenueService.getGroupRevenue(groupId));
    }
}
