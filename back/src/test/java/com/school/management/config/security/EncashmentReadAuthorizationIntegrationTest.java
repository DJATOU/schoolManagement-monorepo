package com.school.management.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reçus et historique des versements : données financières, réservées au rôle ADMIN même en
 * lecture, comme les relevés de recettes (spec admin-corrections, A.8 et A.9).
 *
 * <p>La règle générique ouvre toute lecture {@code /api/**} aux deux rôles : sans la règle
 * dédiée, placée avant elle dans {@code SecurityConfig}, un compte VIEWER lirait chaque reçu. Les
 * filtres de sécurité sont donc actifs ici, contrairement aux tests de contrat métier.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:encashment-read-security;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("Lecture des Encaissements : ADMIN seulement")
class EncashmentReadAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("VIEWER : 403 sur un reçu et sur l'historique des versements")
    void viewerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/encashments/1").with(user("lecteur").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/students/1/encashments").with(user("lecteur").roles("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN : autorisé (404 ici, aucun encaissement n'existe)")
    void adminIsAllowed() throws Exception {
        mockMvc.perform(get("/api/encashments/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/students/1/encashments").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sans authentification : 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/encashments/1")).andExpect(status().isUnauthorized());
    }
}
