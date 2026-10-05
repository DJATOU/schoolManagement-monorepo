package com.school.management.service.payroll;

import com.school.management.service.payroll.PayoutCalculator.Shares;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Partage de l'encaissé d'une série entre l'enseignant et l'école.
 *
 * <p>Exemple de référence : 72 000 DA encaissés nets, 60 % à l'enseignant — 43 200 DA pour lui,
 * 28 800 DA pour l'école.</p>
 */
class PayoutCalculatorTest {

    private static BigDecimal dz(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("Pourcentage")
    class Pourcentage {

        @Test
        @DisplayName("0 % et 100 % refusés : il faut deux parts")
        void boundsAreExcluded() {
            assertThatThrownBy(() -> PayoutCalculator.requireValidPercent(dz("0")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Le pourcentage de l'enseignant doit être strictement compris entre 0 et 100 : 0 reçu.");
            assertThatThrownBy(() -> PayoutCalculator.requireValidPercent(dz("100.00")))
                    .hasMessageContaining("100 reçu");
            assertThatThrownBy(() -> PayoutCalculator.requireValidPercent(dz("-5")))
                    .hasMessageContaining("-5 reçu");
        }

        @Test
        @DisplayName("bornes intérieures acceptées, ramenées à deux décimales")
        void innerBoundsAreAccepted() {
            assertThat(PayoutCalculator.requireValidPercent(dz("0.01"))).isEqualTo(dz("0.01"));
            assertThat(PayoutCalculator.requireValidPercent(dz("99.99"))).isEqualTo(dz("99.99"));
            assertThat(PayoutCalculator.requireValidPercent(dz("60"))).isEqualTo(dz("60.00"));
            // Des zéros de fin ne sont pas des décimales.
            assertThat(PayoutCalculator.requireValidPercent(dz("60.5000"))).isEqualTo(dz("60.50"));
        }

        @Test
        @DisplayName("plus de deux décimales refusées, plutôt qu'arrondies en silence")
        void moreThanTwoDecimalsIsRejected() {
            assertThatThrownBy(() -> PayoutCalculator.requireValidPercent(dz("33.333")))
                    .hasMessage("Le pourcentage de l'enseignant admet au plus deux décimales : 33.333 reçu.");
        }
    }

    @Nested
    @DisplayName("Paie initiale")
    class PaieInitiale {

        @Test
        @DisplayName("72 000 DA à 60 % : 43 200 pour l'enseignant, 28 800 pour l'école")
        void referenceExample() {
            Shares shares = PayoutCalculator.initial(dz("72000"), dz("60"));

            assertThat(shares.baseDelta()).isEqualTo(dz("72000.00"));
            assertThat(shares.teacherAmount()).isEqualTo(dz("43200.00"));
            assertThat(shares.schoolAmount()).isEqualTo(dz("28800.00"));
        }

        @Test
        @DisplayName("arrondi au centime, demi supérieur, et l'école reçoit la différence exacte")
        void roundsHalfUpAndSchoolGetsTheExactRemainder() {
            // 1 000,01 × 33,33 % = 333,303333 → 333,30 ; école 666,71.
            Shares down = PayoutCalculator.initial(dz("1000.01"), dz("33.33"));
            assertThat(down.teacherAmount()).isEqualTo(dz("333.30"));
            assertThat(down.schoolAmount()).isEqualTo(dz("666.71"));

            // 0,05 × 50 % = 0,025 → 0,03 (demi supérieur), école 0,02.
            Shares half = PayoutCalculator.initial(dz("0.05"), dz("50"));
            assertThat(half.teacherAmount()).isEqualTo(dz("0.03"));
            assertThat(half.schoolAmount()).isEqualTo(dz("0.02"));
            assertThat(half.teacherAmount().add(half.schoolAmount())).isEqualTo(dz("0.05"));
        }

        @Test
        @DisplayName("rien d'encaissé : refusé, il n'y a rien à partager")
        void nothingCollectedIsRejected() {
            assertThatThrownBy(() -> PayoutCalculator.initial(dz("0"), dz("60")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Rien à partager : l'encaissé net de la série est de 0.00 DA.");
            assertThatThrownBy(() -> PayoutCalculator.initial(dz("-10"), dz("60")))
                    .hasMessageContaining("-10.00 DA");
        }

        @Test
        @DisplayName("le plus petit encaissé partageable est accepté")
        void smallestPositiveIsAccepted() {
            Shares shares = PayoutCalculator.initial(dz("0.01"), dz("60"));
            assertThat(shares.teacherAmount().add(shares.schoolAmount())).isEqualTo(dz("0.01"));
        }

        @Test
        @DisplayName("pourcentage invalide refusé avant tout calcul")
        void invalidPercentIsRejected() {
            assertThatThrownBy(() -> PayoutCalculator.initial(dz("72000"), dz("100")))
                    .hasMessageContaining("strictement compris entre 0 et 100");
        }
    }

    @Nested
    @DisplayName("Régularisation")
    class Regularisation {

        @Test
        @DisplayName("un élève paie 2 400 DA après la paie : complément de 1 440, l'école 960")
        void lateMoneyGivesAComplement() {
            // Paie initiale sur 72 000 : 43 200 versés.
            Shares shares = PayoutCalculator.regularization(dz("74400"), dz("72000"), dz("60"), dz("43200"));

            assertThat(shares.baseDelta()).isEqualTo(dz("2400.00"));
            assertThat(shares.teacherAmount()).isEqualTo(dz("1440.00"));
            assertThat(shares.schoolAmount()).isEqualTo(dz("960.00"));
        }

        @Test
        @DisplayName("1 000 DA rendus après la paie : retenue de 600, l'école en supporte 400")
        void refundAfterPayoutGivesADeduction() {
            Shares shares = PayoutCalculator.regularization(dz("71000"), dz("72000"), dz("60"), dz("43200"));

            assertThat(shares.baseDelta()).isEqualTo(dz("-1000.00"));
            assertThat(shares.teacherAmount()).isEqualTo(dz("-600.00"));
            assertThat(shares.schoolAmount()).isEqualTo(dz("-400.00"));
        }

        @Test
        @DisplayName("rien de changé : refusé, rien à régulariser")
        void noChangeIsRejected() {
            assertThat(PayoutCalculator.gap(dz("72000"), dz("60"), dz("43200"))).isEqualByComparingTo("0");
            assertThatThrownBy(() -> PayoutCalculator.regularization(dz("72000"), dz("72000"), dz("60"), dz("43200")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Rien à régulariser : l'enseignant a déjà reçu sa part de l'encaissé actuel.");
        }

        @Test
        @DisplayName("l'écart rattrape l'arrondi du cumul, pas celui de la tranche")
        void gapIsComputedOnTheCumulativeShare() {
            // 0,05 à 50 % : 0,03 versés (arrondi supérieur). Encaissé porté à 0,10 : part exacte 0,05.
            // Arrondir la tranche de 0,05 donnerait encore 0,03, soit 0,06 au total : un centime de trop.
            Shares shares = PayoutCalculator.regularization(dz("0.10"), dz("0.05"), dz("50"), dz("0.03"));

            assertThat(shares.teacherAmount()).isEqualTo(dz("0.02"));
            assertThat(shares.schoolAmount()).isEqualTo(dz("0.03"));
        }
    }
}
