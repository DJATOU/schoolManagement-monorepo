package com.school.management.service.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour {@link PaymentCostCalculator}.
 *
 * Scénarios couverts : payé intégralement, partiel, surpayé, zéro présent,
 * forte présence (rattrapage cross-group), réduction 50 %, exemption 100 %,
 * cas d'arrondi.
 */
class PaymentCostCalculatorTest {

    private static final BigDecimal PRICE_30 = new BigDecimal("30.00");
    private static final BigDecimal NO_EXEMPTION = new BigDecimal("0.00");

    private static BigDecimal money(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("Mois soldé : 8 séances planifiées et présentes, payé en totalité")
    void fullyPaid() {
        // 8 planifiées, 8 présentes, 30 € → month total = 240, due = 240
        PaymentCostCalculator calc = new PaymentCostCalculator(8, 8, PRICE_30, NO_EXEMPTION);

        assertEquals(money("240.00"), calc.monthTotalCost());
        assertEquals(money("240.00"), calc.amountDueSoFar());
        assertFalse(calc.isLate(money("240.00")), "payé = dû → pas en retard");
        assertTrue(calc.isMonthFullyPaid(money("240.00")), "payé = total du mois → soldé");
    }

    @Test
    @DisplayName("Paiement partiel : à jour sur les séances présentes mais mois non soldé")
    void partialPaymentUpToDateButMonthNotPaid() {
        // 8 planifiées, 4 présentes, payé 120 (= 4 × 30)
        PaymentCostCalculator calc = new PaymentCostCalculator(8, 4, PRICE_30, NO_EXEMPTION);

        assertEquals(money("240.00"), calc.monthTotalCost());
        assertEquals(money("120.00"), calc.amountDueSoFar());
        assertFalse(calc.isLate(money("120.00")), "payé = dû à ce jour → pas en retard (par facilité)");
        assertFalse(calc.isMonthFullyPaid(money("120.00")), "mois pas encore soldé");
    }

    @Test
    @DisplayName("En retard : payé moins que les séances présentes")
    void lateWhenPaidLessThanDue() {
        PaymentCostCalculator calc = new PaymentCostCalculator(8, 4, PRICE_30, NO_EXEMPTION);

        // dû à ce jour = 120, payé 90 → en retard
        assertTrue(calc.isLate(money("90.00")));
        assertFalse(calc.isMonthFullyPaid(money("90.00")));
    }

    @Test
    @DisplayName("Surpayé : payé plus que le total du mois")
    void overpaid() {
        PaymentCostCalculator calc = new PaymentCostCalculator(8, 8, PRICE_30, NO_EXEMPTION);

        assertFalse(calc.isLate(money("300.00")), "payé > dû → pas en retard");
        assertTrue(calc.isMonthFullyPaid(money("300.00")), "payé > total → soldé");
    }

    @Test
    @DisplayName("Zéro séance présente : rien dû à ce jour, jamais en retard même sans paiement")
    void zeroAttended() {
        // 8 planifiées, 0 présentes
        PaymentCostCalculator calc = new PaymentCostCalculator(8, 0, PRICE_30, NO_EXEMPTION);

        assertEquals(money("240.00"), calc.monthTotalCost());
        assertEquals(money("0.00"), calc.amountDueSoFar());
        assertFalse(calc.isLate(money("0.00")), "rien dû → pas en retard");
        assertFalse(calc.isMonthFullyPaid(money("0.00")), "mois non soldé");
    }

    @Test
    @DisplayName("Forte présence (rattrapage cross-group) : présent > planifié")
    void highAttendedCountFromCatchUp() {
        // Mois planifié à 8, mais l'étudiant a assisté à 10 séances (rattrapages
        // dans un autre groupe, même matière, comptés en amont).
        PaymentCostCalculator calc = new PaymentCostCalculator(8, 10, PRICE_30, NO_EXEMPTION);

        assertEquals(money("240.00"), calc.monthTotalCost(), "total du mois reste basé sur le planifié");
        assertEquals(money("300.00"), calc.amountDueSoFar(), "dû basé sur 10 séances présentes");
        // payé seulement le total du mois (240) alors qu'il doit 300 → en retard
        assertTrue(calc.isLate(money("240.00")));
        assertTrue(calc.isMonthFullyPaid(money("240.00")), "le mois 'planifié' est soldé même si dû à ce jour > total");
    }

    @Test
    @DisplayName("Réduction 50 % : montants divisés par deux")
    void fiftyPercentDiscount() {
        BigDecimal half = money("0.50");
        PaymentCostCalculator calc = new PaymentCostCalculator(8, 8, PRICE_30, half);

        assertEquals(money("120.00"), calc.monthTotalCost(), "240 × (1 − 0.50)");
        assertEquals(money("120.00"), calc.amountDueSoFar());
        assertFalse(calc.isLate(money("120.00")), "payé = dû réduit → pas en retard");
        assertTrue(calc.isMonthFullyPaid(money("120.00")));
        // payer le plein tarif non réduit n'est évidemment pas en retard non plus
        assertFalse(calc.isLate(money("240.00")));
    }

    @Test
    @DisplayName("Exemption 100 % : rien dû, toujours à jour, mois considéré soldé à 0")
    void hundredPercentExemption() {
        BigDecimal full = money("1.00");
        PaymentCostCalculator calc = new PaymentCostCalculator(8, 5, PRICE_30, full);

        assertEquals(money("0.00"), calc.monthTotalCost());
        assertEquals(money("0.00"), calc.amountDueSoFar());
        assertFalse(calc.isLate(money("0.00")), "taux 100 % → jamais en retard, quelle que soit la présence");
        assertTrue(calc.isMonthFullyPaid(money("0.00")), "rien à payer → soldé");
    }

    @Test
    @DisplayName("Cas d'arrondi : prix non entier et taux 1/3 → HALF_UP, échelle 2")
    void roundingEdgeCase() {
        // prix 33.33, 3 séances, réduction 33,333 % → base = 99.99
        // 99.99 × (1 − 0.33333) = 99.99 × 0.66667 = 66.66333... → 66.66 (HALF_UP)
        BigDecimal price = money("33.33");
        BigDecimal thirdIsh = money("0.33333");
        PaymentCostCalculator calc = new PaymentCostCalculator(3, 3, price, thirdIsh);

        assertEquals(money("66.66"), calc.amountDueSoFar());
        assertEquals(money("66.66"), calc.monthTotalCost());
        assertEquals(2, calc.amountDueSoFar().scale(), "échelle figée à 2 décimales");
    }

    @Test
    @DisplayName("Arrondi HALF_UP explicite : 0.005 arrondi à 0.01")
    void roundingHalfUpRoundsUp() {
        // prix 0.01, 1 séance, réduction 0.50 → 0.01 × 0.50 = 0.005 → 0.01 (HALF_UP)
        PaymentCostCalculator calc = new PaymentCostCalculator(1, 1, money("0.01"), money("0.50"));

        assertEquals(money("0.01"), calc.amountDueSoFar());
    }

    @Test
    @DisplayName("Validation des entrées invalides")
    void invalidInputsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentCostCalculator(-1, 0, PRICE_30, NO_EXEMPTION), "plannedSessions négatif");
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentCostCalculator(8, -1, PRICE_30, NO_EXEMPTION), "attendedSessions négatif");
        assertThrows(NullPointerException.class,
                () -> new PaymentCostCalculator(8, 8, null, NO_EXEMPTION), "prix null");
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentCostCalculator(8, 8, money("-1.00"), NO_EXEMPTION), "prix négatif");
        assertThrows(NullPointerException.class,
                () -> new PaymentCostCalculator(8, 8, PRICE_30, null), "taux null");
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentCostCalculator(8, 8, PRICE_30, money("1.01")), "taux > 1");
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentCostCalculator(8, 8, PRICE_30, money("-0.01")), "taux < 0");
    }

    @Test
    @DisplayName("Validation : amountPaid null ou négatif rejeté")
    void invalidAmountPaidRejected() {
        PaymentCostCalculator calc = new PaymentCostCalculator(8, 8, PRICE_30, NO_EXEMPTION);

        assertThrows(NullPointerException.class, () -> calc.isLate(null));
        assertThrows(IllegalArgumentException.class, () -> calc.isLate(money("-1.00")));
        assertThrows(NullPointerException.class, () -> calc.isMonthFullyPaid(null));
        assertThrows(IllegalArgumentException.class, () -> calc.isMonthFullyPaid(money("-5.00")));
    }
}
