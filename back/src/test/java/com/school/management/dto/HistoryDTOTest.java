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
 * exemption ({@code isExempted}) et montants remboursés en {@link BigDecimal}
 * ({@code refundedAmount} / {@code totalRefunded}).</p>
 */
class HistoryDTOTest {

    @Test
    void sessionHistory_carriesCatchUpExemptionAndRefundFields() {
        SessionHistoryDTO dto = SessionHistoryDTO.builder()
                .sessionId(1L)
                .sessionName("Séance 1")
                .catchUpSession(true)
                .isExempted(true)
                .refundedAmount(new BigDecimal("15.50"))
                .build();

        assertThat(dto.getCatchUpSession()).isTrue();
        assertThat(dto.getIsExempted()).isTrue();
        assertThat(dto.getRefundedAmount()).isEqualByComparingTo("15.50");
    }

    @Test
    void sessionHistory_defaultsForNewFieldsAreNull() {
        SessionHistoryDTO dto = SessionHistoryDTO.builder()
                .sessionId(2L)
                .build();

        assertThat(dto.getIsExempted()).isNull();
        assertThat(dto.getRefundedAmount()).isNull();
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
