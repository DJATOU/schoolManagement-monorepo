package com.school.management.service.payroll;

import com.school.management.repository.SessionRepository;
import com.school.management.service.payroll.SeriesCompletionService.SeriesCompletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Série terminée : toutes ses séances actives validées, et au moins une. */
class SeriesCompletionServiceTest {

    private SessionRepository sessionRepository;
    private SeriesCompletionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        service = new SeriesCompletionService(sessionRepository);
    }

    private void counts(Object[]... rows) {
        when(sessionRepository.countCompletionBySeries(anyCollection())).thenReturn(new ArrayList<>(List.of(rows)));
    }

    @Test
    @DisplayName("huit séances, huit validées : terminée, rien ne reste")
    void allValidated() {
        counts(new Object[]{10L, 8L, 8L});

        SeriesCompletion completion = service.of(10L);

        assertThat(completion.finished()).isTrue();
        assertThat(completion.remaining()).isZero();
    }

    @Test
    @DisplayName("une séance non validée : pas terminée, et on dit combien restent")
    void oneLeft() {
        counts(new Object[]{10L, 8L, 7L});

        SeriesCompletion completion = service.of(10L);

        assertThat(completion.finished()).isFalse();
        assertThat(completion.remaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("sans séance active : pas terminée, une série vide n'a rien produit")
    void noSessionIsNotFinished() {
        counts();

        SeriesCompletion completion = service.of(10L);

        assertThat(completion).isEqualTo(new SeriesCompletion(0, 0));
        assertThat(completion.finished()).isFalse();
    }

    @Test
    @DisplayName("plusieurs séries en une requête ; une série absente du résultat vaut (0, 0)")
    void severalSeries() {
        counts(new Object[]{10L, 4L, 4L}, new Object[]{11L, 4L, null});

        Map<Long, SeriesCompletion> completions = service.of(List.of(10L, 11L, 12L));

        assertThat(completions.get(10L).finished()).isTrue();
        // Somme nulle remontée par la base : aucune séance validée.
        assertThat(completions.get(11L)).isEqualTo(new SeriesCompletion(4, 0));
        assertThat(completions.get(12L)).isEqualTo(new SeriesCompletion(0, 0));
    }

    @Test
    @DisplayName("aucune série demandée : aucune requête")
    void emptyRequest() {
        assertThat(service.of(List.<Long>of())).isEmpty();
        verifyNoInteractions(sessionRepository);
    }
}
