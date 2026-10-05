package com.school.management.controller.correction;

import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Passe transverse T.1 (spec admin-corrections) : les routes réelles de l'application, relues dans
 * Spring MVC plutôt que listées à la main, face au rôle VIEWER et à l'anonyme.
 *
 * <p>Une route ajoutée demain est contrôlée sans qu'on y pense. Les corrections attendues sont tout
 * de même nommées : une route renommée ne doit pas sortir du contrôle sans bruit.</p>
 */
@AutoConfigureMockMvc
@DisplayName("Routes d'écriture et de correction : VIEWER 403, anonyme 401")
class WriteRoutesAuthorizationIntegrationTest extends CorrectionIntegrationTestSupport {

    /** Toutes les corrections, en Aperçu et en confirmation (exigence 11.7). */
    private static final Set<String> CORRECTIONS = Set.of(
            "POST /api/encashments/{id}/cancel/preview", "POST /api/encashments/{id}/cancel/confirm",
            "POST /api/encashments/{id}/correct/preview", "POST /api/encashments/{id}/correct/confirm",
            "POST /api/enrolments/{id}/arrival/preview", "POST /api/enrolments/{id}/arrival/confirm",
            "POST /api/enrolments/{id}/departure/preview", "POST /api/enrolments/{id}/departure/confirm",
            "POST /api/enrolments/{id}/reopen/preview", "POST /api/enrolments/{id}/reopen/confirm",
            "POST /api/attendances/{id}/correct/preview", "POST /api/attendances/{id}/correct/confirm",
            "POST /api/attendances/{id}/remove/preview", "POST /api/attendances/{id}/remove/confirm",
            "POST /api/sessions/{id}/attendances/add/preview", "POST /api/sessions/{id}/attendances/add/confirm",
            "POST /api/sessions/{id}/unvalidate/preview", "POST /api/sessions/{id}/unvalidate/confirm");

    /** Lectures financières : ADMIN seul, VIEWER compris en lecture. */
    private static final List<String> FINANCIAL_READS = List.of(
            "/api/encashments/1", "/api/students/1/encashments", "/api/students/1/journal",
            "/api/students/1/refunds", "/api/refunds/payment/1", "/api/groups/1/revenue");

    /** Seul point d'écriture ouvert à tous : on s'y connecte. */
    private static final String LOGIN = "/api/v1/auth/login";

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    @Test
    @DisplayName("chaque correction existe, en Aperçu et en confirmation ; VIEWER 403, anonyme 401, rien d'écrit")
    void everyCorrectionIsAdminOnly() throws Exception {
        Set<String> found = new TreeSet<>();
        for (String route : writeRoutes()) {
            if (route.endsWith("/preview") || route.endsWith("/confirm")) {
                found.add(route);
            }
        }
        assertThat(found).as("routes de correction relues dans Spring MVC").containsAll(CORRECTIONS);

        Ledger before = ledger();
        List<String> wrong = new ArrayList<>();
        for (String route : CORRECTIONS) {
            expect(route, user("lecteur").roles("VIEWER"), 403, wrong);
            expect(route, anonymous(), 401, wrong);
        }
        assertThat(wrong).as("statuts inattendus").isEmpty();
        assertThat(ledger()).as("aucun refus n'écrit").isEqualTo(before);
    }

    @Test
    @DisplayName("toute écriture sous /api, connexion exceptée : VIEWER 403, rien d'écrit")
    void everyWriteIsAdminOnly() throws Exception {
        List<String> routes = writeRoutes();
        assertThat(routes).as("écritures relues dans Spring MVC").hasSizeGreaterThan(CORRECTIONS.size());

        Ledger before = ledger();
        List<String> wrong = new ArrayList<>();
        for (String route : routes) {
            if (!route.endsWith(" " + LOGIN)) {
                expect(route, user("lecteur").roles("VIEWER"), 403, wrong);
            }
        }
        assertThat(wrong).as("écritures ouvertes au rôle VIEWER").isEmpty();
        assertThat(ledger()).as("aucun refus n'écrit").isEqualTo(before);
    }

    @Test
    @DisplayName("lectures financières : VIEWER 403, ADMIN autorisé")
    void financialReadsAreAdminOnly() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (String path : FINANCIAL_READS) {
            expect("GET " + path, user("lecteur").roles("VIEWER"), 403, wrong);
            int admin = mockMvc.perform(MockMvcRequestBuilders.get(path).with(user("directrice").roles("ADMIN")))
                    .andReturn().getResponse().getStatus();
            if (admin == 401 || admin == 403) {
                wrong.add("GET " + path + " ADMIN : " + admin);
            }
        }
        assertThat(wrong).as("statuts inattendus").isEmpty();
    }

    @Test
    @DisplayName("le plafond d'un versement reste lisible par VIEWER : la règle des remboursements d'un versement ne le couvre pas")
    void refundCapStaysReadableByViewer() throws Exception {
        int viewer = mockMvc.perform(MockMvcRequestBuilders.get("/api/refunds/payment/1/cap")
                        .with(user("lecteur").roles("VIEWER")))
                .andReturn().getResponse().getStatus();
        assertThat(viewer).as("GET /api/refunds/payment/1/cap en VIEWER").isNotIn(401, 403);
    }

    /** « POST /api/… » de chaque écriture déclarée, variables de chemin comprises. */
    private List<String> writeRoutes() {
        Set<String> routes = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                if (method == RequestMethod.GET || method == RequestMethod.HEAD || method == RequestMethod.OPTIONS) {
                    continue;
                }
                for (String pattern : info.getPatternValues()) {
                    if (pattern.startsWith("/api/")) {
                        routes.add(method.name() + " " + pattern);
                    }
                }
            }
        }
        return new ArrayList<>(routes);
    }

    /** Appelle la route, variables à 1, corps JSON vide ; note l'écart au statut attendu. */
    private void expect(String route, RequestPostProcessor who, int status, List<String> wrong) throws Exception {
        String[] parts = route.split(" ", 2);
        String path = parts[1].replaceAll("\\{[^}]+}", "1");
        int actual = mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(parts[0]), path).with(who)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        if (actual != status) {
            wrong.add(route + " : " + actual + " au lieu de " + status);
        }
    }
}
