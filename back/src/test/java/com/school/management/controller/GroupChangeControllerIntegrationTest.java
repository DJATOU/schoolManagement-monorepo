package com.school.management.controller;

import com.school.management.service.group.GroupChangeDetector;
import com.school.management.service.group.GroupChangeDetector.GroupActivity;
import com.school.management.service.group.GroupChangeDetector.GroupChange;
import com.school.management.service.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat du point d'entrée des signalements de changement de groupe (exigence 10.2).
 *
 * <p>Ce test fixe la <strong>forme du JSON</strong> autant que le routage : la fiche étudiant et le
 * formulaire de versement consomment ces noms de champs. Une renommage silencieux côté serveur
 * casserait l'affichage sans qu'aucun test de service ne s'en aperçoive.</p>
 */
@WebMvcTest(GroupChangeController.class)
@AutoConfigureMockMvc(addFilters = false)
class GroupChangeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupChangeDetector groupChangeDetector;

    // Filtre de sécurité auto-détecté par @WebMvcTest ; mocké pour satisfaire sa dépendance.
    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("GET /api/students/{id}/group-changes : mois, groupes et décomptes")
    void getGroupChanges_exposesMonthGroupsAndAttendanceCounts() throws Exception {
        when(groupChangeDetector.detect(7L)).thenReturn(List.of(new GroupChange(
                2026, 8,
                new GroupActivity(12L, "Maths 1B", 2),
                new GroupActivity(15L, "Maths 1C", 1))));

        mockMvc.perform(get("/api/students/{studentId}/group-changes", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].year").value(2026))
                .andExpect(jsonPath("$[0].month").value(8))
                .andExpect(jsonPath("$[0].leftGroup.groupId").value(12))
                .andExpect(jsonPath("$[0].leftGroup.groupName").value("Maths 1B"))
                .andExpect(jsonPath("$[0].leftGroup.attendedCount").value(2))
                .andExpect(jsonPath("$[0].joinedGroup.groupId").value(15))
                .andExpect(jsonPath("$[0].joinedGroup.groupName").value("Maths 1C"))
                .andExpect(jsonPath("$[0].joinedGroup.attendedCount").value(1));
    }

    @Test
    @DisplayName("Aucun changement : liste vide et 200, l'absence de signalement n'est pas une erreur")
    void getGroupChanges_returnsEmptyListWhenNothingToFlag() throws Exception {
        when(groupChangeDetector.detect(9L)).thenReturn(List.of());

        mockMvc.perform(get("/api/students/{studentId}/group-changes", 9L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
