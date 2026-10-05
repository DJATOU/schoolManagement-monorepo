package com.school.management.dto;

import com.school.management.dto.serie.SeriesHistoryDTO;
import com.school.management.dto.session.SessionHistoryDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des DTO d'historique enrichis pour le rendu de la légende.
 *
 * <p>Vérifie les nouveaux champs : indicateur de rattrapage ({@code catchUpSession}),
 * exemption ({@code isExempted}) et total remboursé de la série en {@link BigDecimal}
 * ({@code totalRefunded}). Il n'y a pas de montant remboursé par séance : un remboursement porte
 * sur le versement d'une série.</p>
 */
class HistoryDTOTest {

    @Test
    void sessionHistory_carriesCatchUpAndExemptionFields() {
        SessionHistoryDTO dto = SessionHistoryDTO.builder()
                .sessionId(1L)
                .sessionName("Séance 1")
                .catchUpSession(true)
                .isExempted(true)
                .build();

        assertThat(dto.getCatchUpSession()).isTrue();
        assertThat(dto.getIsExempted()).isTrue();
    }

    @Test
    void sessionHistory_defaultsForNewFieldsAreNull() {
        SessionHistoryDTO dto = SessionHistoryDTO.builder()
                .sessionId(2L)
                .build();

        assertThat(dto.getIsExempted()).isNull();
        assertThat(dto.getCatchUpSession()).isNull();
    }

    @Test
    void seriesHistory_carriesExemptionAndTotalRefunded() {
        SessionHistoryDTO session = SessionHistoryDTO.builder().sessionId(1L).build();
        SeriesHistoryDTO dto = SeriesHistoryDTO.builder()
                .seriesId(10L)
                .seriesName("Série A")
                .isExempted(true)
                .totalRefunded(new BigDecimal("30.00"))
                .sessions(List.of(session))
                .build();

        assertThat(dto.getIsExempted()).isTrue();
        assertThat(dto.getTotalRefunded()).isEqualByComparingTo("30.00");
        assertThat(dto.getSessions()).hasSize(1);
    }
}
