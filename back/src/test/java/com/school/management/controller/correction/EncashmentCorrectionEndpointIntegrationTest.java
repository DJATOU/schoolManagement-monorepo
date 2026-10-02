package com.school.management.controller.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.RefundRequestDTO;
import com.school.management.persistance.PaymentEntity;
import com.school.management.service.RefundService;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Points d'entrée de correction d'un Encaissement, de bout en bout, filtres de sécurité actifs
 * (spec admin-corrections, B.5 ; exigences 2, 3, 4 et 11.7).
 *
 * <p>Ce que les tests de service ne voient pas : la vraie requête HTTP — sérialisation de l'Aperçu,
 * corps des refus, et surtout l'Aperçu annulé alors que la requête garde son contexte de persistance
 * ouvert jusqu'à la réponse ({@code open-in-view}). Les états sont relus en SQL.</p>
 */
@AutoConfigureMockMvc
@DisplayName("POST /api/encashments/{id}/cancel|correct/preview|confirm")
class EncashmentCorrectionEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    private static final String RECEIPT_TOKEN = "[0-9a-f]{64}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RefundService refundService;

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Annulation")
    class Annulation {

        @Test
        @DisplayName("Aperçu : 200, montants avant/après et jeton, rien d'écrit")
        void preview() throws Exception {
            attend(s1First);
            Long id = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"WRONG_AMOUNT\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value(nullValue()))
                    .andExpect(jsonPath("$.previewToken").value(matchesPattern(RECEIPT_TOKEN)))
                    .andExpect(jsonPath("$.preview.amountsUnchanged").value(false))
                    .andExpect(jsonPath("$.preview.series[0].seriesName").value("Janvier"))
                    .andExpect(jsonPath("$.preview.series[0].groupName").value("Math 1ère A"))
                    .andExpect(jsonPath("$.preview.series[0].before.paid").value(3000.0))
                    .andExpect(jsonPath("$.preview.series[0].before.late").value(false))
                    .andExpect(jsonPath("$.preview.series[0].after.paid").value(0.0))
                    .andExpect(jsonPath("$.preview.series[0].after.remaining").value(4000.0))
                    .andExpect(jsonPath("$.preview.series[0].after.late").value(true))
                    .andExpect(jsonPath("$.preview.effects[0].type").value("ENCASHMENT_CANCELLED"));

            assertThat(ledger()).as("l'Aperçu est annulé, même requête HTTP ouverte").isEqualTo(before);
        }

        @Test
        @DisplayName("confirmation : 200, Encaissement annulé, trace au nom de l'administrateur connecté")
        void confirm() throws Exception {
            Long id = pay(s1, 3000).encashment().getId();
            String token = token(send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"WRONG_AMOUNT\"}"));

            send(post(cancelUrl(id, "confirm")),
                    "{\"reasonType\":\"other\",\"reasonText\":\"Doublon\",\"previewToken\":\"" + token + "\"}")
                    .andExpect(status().is(409));
            // Le Motif fait partie de l'empreinte : il faut confirmer ce qui a été prévisualisé.
            send(post(cancelUrl(id, "confirm")),
                    "{\"reasonType\":\"WRONG_AMOUNT\",\"previewToken\":\"" + token + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.result.cancelledBy").value("directrice"))
                    .andExpect(jsonPath("$.result.cancelReasonType").value("WRONG_AMOUNT"));

            assertThat(statusOf(id)).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT performed_by FROM correction_audit", String.class))
                    .isEqualTo("directrice");
        }

        @Test
        @DisplayName("Aperçu périmé : 409 avec le nouvel Aperçu et son jeton")
        void stalePreview() throws Exception {
            Long id = pay(s1, 3000).encashment().getId();
            String token = token(send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"WRONG_AMOUNT\"}"));
            pay(s1, 500);

            send(post(cancelUrl(id, "confirm")), "{\"reasonType\":\"WRONG_AMOUNT\",\"previewToken\":\"" + token + "\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("STALE_PREVIEW"))
                    .andExpect(jsonPath("$.preview.series[0].before.paid").value(3500.0))
                    .andExpect(jsonPath("$.previewToken").value(matchesPattern(RECEIPT_TOKEN)));
            assertThat(statusOf(id)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("passer sous le remboursé : 409 nommant le remboursement")
        void refundFloor() throws Exception {
            Long id = pay(s1, 3000).encashment().getId();
            PaymentEntity line = paymentRepository.findAll().get(0);
            refundService.create(new RefundRequestDTO(line.getId(), student.getId(), new BigDecimal("2000"), null,
                    "Départ anticipé"));

            send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"WRONG_AMOUNT\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("REFUND_FLOOR"))
                    .andExpect(jsonPath("$.blockingRefunds[0].refundNumber").value(matchesPattern("REMB-\\d{4}-\\d{4}")))
                    .andExpect(jsonPath("$.blockingRefunds[0].seriesName").value("Janvier"));
        }

        @Test
        @DisplayName("Motif absent, inconnu, « Autre » sans texte ou sans rapport : 400 en français")
        void reasonRefusals() throws Exception {
            Long id = pay(s1, 3000).encashment().getId();

            send(post(cancelUrl(id, "preview")), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Motif obligatoire")));
            send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"  \"}")
                    .andExpect(status().isBadRequest());
            send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"FOO\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Motif inconnu : « FOO »."));
            send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"OTHER\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("« Autre »")));
            send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"STUDENT_LEFT\"}")
                    .andExpect(status().isBadRequest());
            assertThat(statusOf(id)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("confirmation sans jeton : 400 ; Encaissement inconnu : 404 ; déjà annulé : 409")
        void otherRefusals() throws Exception {
            Long id = pay(s1, 3000).encashment().getId();

            send(post(cancelUrl(id, "confirm")), "{\"reasonType\":\"WRONG_AMOUNT\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("prévisualisez")));
            send(post(cancelUrl(999_999L, "preview")), "{\"reasonType\":\"WRONG_AMOUNT\"}")
                    .andExpect(status().isNotFound());

            String token = token(send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"WRONG_AMOUNT\"}"));
            send(post(cancelUrl(id, "confirm")), "{\"reasonType\":\"WRONG_AMOUNT\",\"previewToken\":\"" + token + "\"}")
                    .andExpect(status().isOk());
            send(post(cancelUrl(id, "preview")), "{\"reasonType\":\"WRONG_AMOUNT\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("est déjà annulé")));
        }
    }

    // ------------------------------------------------------------------
    // Correction
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Correction")
    class Correction {

        @Test
        @DisplayName("remplacement : Aperçu sans écriture, puis confirmation qui rend le reçu à imprimer")
        void replacement() throws Exception {
            Long id = pay(s1, 5000).encashment().getId();
            String body = correction("2000", s1.getId(), null, null, "WRONG_AMOUNT");
            Ledger before = ledger();

            String token = token(send(post(correctUrl(id, "preview")), body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value(nullValue()))
                    .andExpect(jsonPath("$.preview.series.length()").value(2))
                    .andExpect(jsonPath("$.preview.effects[3].type").value("ENCASHMENT_CREATED")));
            assertThat(ledger()).isEqualTo(before);

            send(post(correctUrl(id, "confirm")), withToken(body, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.original.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.result.replacement.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.result.replacement.amountReceived").value(2000.0))
                    .andExpect(jsonPath("$.result.replacement.replacesReceiptNumber").value(receiptOf(id)));
            assertThat(cumulOf(s1)).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("mode et note seuls : corrigés en place, sans reçu de remplacement")
        void detailsOnly() throws Exception {
            Long id = pay(s1, 3000).encashment().getId();
            String body = correction("3000", s1.getId(), "cheque", "Versé par le père", "DATA_ENTRY_ERROR");

            String token = token(send(post(correctUrl(id, "preview")), body)
                    .andExpect(jsonPath("$.preview.amountsUnchanged").value(true)));
            send(post(correctUrl(id, "confirm")), withToken(body, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.replacement").value(nullValue()))
                    .andExpect(jsonPath("$.result.original.paymentMethod").value("cheque"))
                    .andExpect(jsonPath("$.result.original.notes").value("Versé par le père"));
        }

        @Test
        @DisplayName("correction incomplète ou sans corps : 400 ; remplacement refusé : 400, rien d'écrit")
        void refusals() throws Exception {
            Long id = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            send(post(correctUrl(id, "preview")), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Correction incomplète")));
            send(post(correctUrl(id, "preview")), "{\"reasonType\":\"WRONG_AMOUNT\",\"studentId\":" + student.getId() + "}")
                    .andExpect(status().isBadRequest());
            send(post(correctUrl(id, "preview")), correction("20000", s1.getId(), null, null, "WRONG_AMOUNT"))
                    .andExpect(status().isBadRequest());
            send(post(correctUrl(id, "preview")), correction("3000", s1.getId(), null, null, "WRONG_AMOUNT"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("sans changement effectif")));
            assertThat(ledger()).isEqualTo(before);
        }
    }

    // ------------------------------------------------------------------
    // Accès
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Accès")
    class Acces {

        @Test
        @DisplayName("VIEWER : 403 sur chaque Aperçu et chaque confirmation, rien d'écrit")
        void viewerIsForbiddenEverywhere() throws Exception {
            Long id = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            for (String url : new String[] { cancelUrl(id, "preview"), cancelUrl(id, "confirm"),
                    correctUrl(id, "preview"), correctUrl(id, "confirm") }) {
                mockMvc.perform(post(url).with(user("lecteur").roles("VIEWER"))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonType\":\"WRONG_AMOUNT\"}"))
                        .andExpect(status().isForbidden());
            }
            mockMvc.perform(get("/api/encashments/correction-reasons").with(user("lecteur").roles("VIEWER")))
                    .andExpect(status().isForbidden());
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("sans authentification : 401")
        void anonymousIsUnauthorized() throws Exception {
            // Le socle authentifie l'administrateur sur ce fil pour les appels de service directs ;
            // MockMvc reprendrait ce contexte pour la requête.
            org.springframework.security.core.context.SecurityContextHolder.clearContext();

            mockMvc.perform(post(cancelUrl(1L, "preview")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ADMIN : les Motifs proposés, dans l'ordre d'affichage")
        void reasonsForAdmin() throws Exception {
            mockMvc.perform(get("/api/encashments/correction-reasons").with(user("directrice").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0]").value("DATA_ENTRY_ERROR"))
                    .andExpect(jsonPath("$[1]").value("WRONG_STUDENT"))
                    .andExpect(jsonPath("$[2]").value("WRONG_AMOUNT"))
                    .andExpect(jsonPath("$[3]").value("OTHER"));
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private static String cancelUrl(Long id, String mode) {
        return "/api/encashments/" + id + "/cancel/" + mode;
    }

    private static String correctUrl(Long id, String mode) {
        return "/api/encashments/" + id + "/correct/" + mode;
    }

    /** Requête d'un administrateur, « directrice », avec ou sans corps JSON. */
    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        MockHttpServletRequestBuilder admin = request.with(user("directrice").roles("ADMIN"));
        if (json != null) {
            admin = admin.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(admin);
    }

    private String token(ResultActions preview) throws Exception {
        JsonNode body = objectMapper.readTree(preview.andReturn().getResponse().getContentAsString());
        return body.get("previewToken").asText();
    }

    private String correction(String amount, Long seriesId, String method, String notes, String reason) {
        return String.format("{\"amount\":%s,\"studentId\":%d,\"groupId\":%d,\"targetSeriesId\":%d,"
                        + "\"paymentMethod\":%s,\"notes\":%s,\"reasonType\":\"%s\"}",
                amount, student.getId(), group.getId(), seriesId, quoted(method), quoted(notes), reason);
    }

    private static String withToken(String body, String token) {
        return body.substring(0, body.length() - 1) + ",\"previewToken\":\"" + token + "\"}";
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
