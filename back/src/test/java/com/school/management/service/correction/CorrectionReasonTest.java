package com.school.management.service.correction;

import com.school.management.persistance.CorrectionReasonType;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Motif d'une correction")
class CorrectionReasonTest {

    @Test
    @DisplayName("type seul accepté, sans texte")
    void typeAloneIsEnough() {
        CorrectionReason reason = CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT);
        assertThat(reason.type()).isEqualTo(CorrectionReasonType.WRONG_AMOUNT);
        assertThat(reason.text()).isNull();
    }

    @Test
    @DisplayName("type absent refusé")
    void typeIsMandatory() {
        assertThatThrownBy(() -> new CorrectionReason(null, "texte"))
                .isInstanceOf(CustomServiceException.class)
                .extracting(e -> ((CustomServiceException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("« Autre » sans texte, ou avec des espaces seulement, refusé")
    void otherRequiresRealText() {
        for (String text : new String[] { null, "", "   \n\t" }) {
            assertThatThrownBy(() -> new CorrectionReason(CorrectionReasonType.OTHER, text))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("Autre");
        }
    }

    @Test
    @DisplayName("texte débarrassé des espaces de tête et de fin ; texte vide devenu absent")
    void textIsStripped() {
        assertThat(new CorrectionReason(CorrectionReasonType.OTHER, "  Parent venu deux fois  ").text())
                .isEqualTo("Parent venu deux fois");
        assertThat(new CorrectionReason(CorrectionReasonType.DATA_ENTRY_ERROR, "   ").text()).isNull();
    }

    @Test
    @DisplayName("500 caractères acceptés, 501 refusés")
    void textIsBounded() {
        assertThat(new CorrectionReason(CorrectionReasonType.OTHER, "x".repeat(500)).text()).hasSize(500);
        assertThatThrownBy(() -> new CorrectionReason(CorrectionReasonType.OTHER, "x".repeat(501)))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("501");
    }
}
