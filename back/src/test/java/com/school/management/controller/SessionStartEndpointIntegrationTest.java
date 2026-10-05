package com.school.management.controller;

import com.school.management.persistance.SessionEntity;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Une séance ne se valide pas avant d'avoir commencé, par aucun point d'entrée : la feuille de
 * présence ({@code POST /api/attendances/bulk}), la validation ({@code PATCH .../finish}) et la
 * modification générique ({@code PATCH /api/sessions/{id}} avec {@code isFinished}).
 *
 * <p>Les séances sont datées par rapport au {@link Clock} de l'application, figé pour les tests :
 * « dans une heure » et « il y a une minute » restent vrais quel que soit le jour où la suite tourne.</p>
 */
@AutoConfigureMockMvc
@DisplayName("Validation d'une séance avant son heure de début")
class SessionStartEndpointIntegrationTest extends CorrectionIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private Clock clock;

    private SessionEntity sessionIn(Duration fromNow) {
        return sessionRepository.save(SessionEntity.builder()
                .title("Séance de test").group(group).sessionSeries(s2)
                .sessionTimeStart(Date.from(clock.instant().plus(fromNow))).build());
    }

    @Test
    @DisplayName("séance dans une heure : feuille de présence refusée, 409 nommant l'heure, rien d'écrit")
    void sheetOfAFutureSessionIsRefused() throws Exception {
        SessionEntity later = sessionIn(Duration.ofHours(1));

        bulk(later)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("n'a pas encore eu lieu")))
                .andExpect(jsonPath("$.message").value(containsString("à partir de son heure de début")));

        assertThat(attendanceRepository.findAll())
                .noneMatch(attendance -> attendance.getSession().getId().equals(later.getId()));
    }

    @Test
    @DisplayName("séance dans une heure : validation refusée, la séance reste à valider")
    void finishingAFutureSessionIsRefused() throws Exception {
        SessionEntity later = sessionIn(Duration.ofHours(1));

        send(patch("/api/sessions/" + later.getId() + "/finish"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("n'a pas encore eu lieu")));

        assertThat(finished(later)).isFalse();
    }

    @Test
    @DisplayName("modification générique : isFinished refusé avant le début, un titre corrigé accepté")
    void genericPatchOnlyRefusesTheValidation() throws Exception {
        SessionEntity later = sessionIn(Duration.ofHours(1));

        send(patch("/api/sessions/" + later.getId()), "{\"isFinished\":true}")
                .andExpect(status().isConflict());
        assertThat(finished(later)).isFalse();

        send(patch("/api/sessions/" + later.getId()), "{\"title\":\"Séance déplacée\"}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("séance commencée depuis une minute : feuille et validation acceptées")
    void startedSessionValidates() throws Exception {
        SessionEntity started = sessionIn(Duration.ofMinutes(-1));

        bulk(started).andExpect(status().isOk());
        send(patch("/api/sessions/" + started.getId() + "/finish"), null).andExpect(status().isOk());

        assertThat(finished(started)).isTrue();
    }

    private boolean finished(SessionEntity session) {
        return Boolean.TRUE.equals(sessionRepository.findById(session.getId()).orElseThrow().getIsFinished());
    }

    /** Feuille d'Amine, présent, telle que l'écran de validation l'envoie. */
    private ResultActions bulk(SessionEntity session) throws Exception {
        return send(post("/api/attendances/bulk"), "[{\"studentId\":" + student.getId()
                + ",\"sessionId\":" + session.getId()
                + ",\"groupId\":" + group.getId()
                + ",\"sessionSeriesId\":" + s2.getId()
                + ",\"isPresent\":true,\"isJustified\":false,\"isCatchUp\":false,\"description\":\"\"}]");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        MockHttpServletRequestBuilder authenticated = request.with(user("directrice").roles("ADMIN"));
        if (json != null) {
            authenticated = authenticated.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(authenticated);
    }
}
