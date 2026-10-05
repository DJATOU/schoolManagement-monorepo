package com.school.management.service.payroll;

import com.school.management.service.payroll.PayoutCalculator.Shares;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 (spec teacher-payroll) : quelle que soit la suite d'encaissés nets d'une série, payée puis
 * régularisée à chaque changement, l'argent se partage sans perte ni dérive.
 *
 * <ul>
 *   <li>Σ part enseignant + Σ part école = encaissé net couvert : pas un centime ne se perd ;</li>
 *   <li>Σ part enseignant = arrondi(encaissé × p / 100) : l'enseignant a reçu exactement sa part, et
 *       l'arrondi ne dérive pas d'une régularisation à l'autre.</li>
 * </ul>
 */
class PayoutCalculatorPropertyTest {

    @Provide
    Arbitrary<BigDecimal> percents() {
        return Arbitraries.integers().between(1, 9_999).map(cents -> BigDecimal.valueOf(cents, 2));
    }

    /** Encaissés nets successifs : le premier positif, les suivants quelconques au-dessus de zéro. */
    @Provide
    Arbitrary<List<BigDecimal>> nets() {
        return Arbitraries.longs().between(1, 50_000_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2))
                .list().ofMinSize(1).ofMaxSize(8);
    }

    @Property(tries = 1_000)
    @Label("P1 : après toute suite de paies, les parts somment l'encaissé et l'enseignant a sa part exacte")
    void sharesNeverLoseOrDrift(@ForAll("nets") List<BigDecimal> nets, @ForAll("percents") BigDecimal percent) {
        Shares initial = PayoutCalculator.initial(nets.get(0), percent);
        BigDecimal teacherPaid = initial.teacherAmount();
        BigDecimal schoolKept = initial.schoolAmount();
        BigDecimal covered = initial.baseDelta();

        for (BigDecimal net : nets.subList(1, nets.size())) {
            if (PayoutCalculator.gap(net, percent, teacherPaid).signum() == 0) {
                continue; // rien à régulariser : la paie suivante n'existe pas
            }
            Shares regularization = PayoutCalculator.regularization(net, covered, percent, teacherPaid);
            teacherPaid = teacherPaid.add(regularization.teacherAmount());
            schoolKept = schoolKept.add(regularization.schoolAmount());
            covered = covered.add(regularization.baseDelta());
            assertThat(covered).isEqualByComparingTo(net);
        }

        assertThat(teacherPaid.add(schoolKept)).isEqualByComparingTo(covered);
        assertThat(teacherPaid).isEqualByComparingTo(
                covered.multiply(percent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
    }

    @Property(tries = 1_000)
    @Label("chaque paie : ses deux parts somment exactement ce qu'elle partage")
    void eachPayoutIsExact(@ForAll("nets") List<BigDecimal> nets, @ForAll("percents") BigDecimal percent) {
        Shares shares = PayoutCalculator.initial(nets.get(0), percent);

        assertThat(shares.teacherAmount().add(shares.schoolAmount())).isEqualByComparingTo(shares.baseDelta());
        assertThat(shares.teacherAmount().scale()).isEqualTo(2);
        assertThat(shares.schoolAmount().scale()).isEqualTo(2);
        // Ni l'enseignant ni l'école ne reçoivent plus que l'encaissé.
        assertThat(shares.teacherAmount()).isBetween(BigDecimal.ZERO, shares.baseDelta());
    }
}
