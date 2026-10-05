package com.school.management.service.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Partage de l'encaissé d'une série entre l'enseignant et l'école (spec teacher-payroll, D2).
 *
 * <p>Calcul pur, sans dépôt ni état : ce qui se teste ici, se teste sur des nombres.</p>
 *
 * <h2>La règle</h2>
 * <pre>
 * Part_Enseignant = arrondi(Encaissé_Net × pourcentage / 100)    au centime, demi supérieur
 * Part_École      = base partagée − Part_Enseignant              différence exacte
 * </pre>
 *
 * <h2>Pourquoi l'écart est calculé sur le cumul</h2>
 * Une Régularisation ne partage pas l'argent arrivé depuis la paie comme une paie à part : elle
 * ramène le cumul versé à l'enseignant à {@code arrondi(Encaissé_Net actuel × p / 100)}. Arrondir
 * chaque tranche séparément ferait dériver le cumul d'un centime à chaque régularisation ; arrondir
 * le cumul garantit qu'après n'importe quelle suite de paies, l'enseignant a reçu exactement sa part
 * de l'encaissé (propriété P1).
 */
public final class PayoutCalculator {

    /** Plus grand pourcentage admis, exclu : 100 % ne laisserait rien à l'école. */
    public static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private PayoutCalculator() {
    }

    /**
     * Les deux parts d'une paie.
     *
     * @param baseDelta     encaissé que la paie partage
     * @param teacherAmount part de l'enseignant ; négative pour une retenue
     * @param schoolAmount  part de l'école : {@code baseDelta − teacherAmount}
     */
    public record Shares(BigDecimal baseDelta, BigDecimal teacherAmount, BigDecimal schoolAmount) {
    }

    /**
     * Vérifie un pourcentage d'enseignant : strictement entre 0 et 100, au plus deux décimales.
     *
     * @return le pourcentage à l'échelle 2
     * @throws IllegalArgumentException en nommant la borne
     */
    public static BigDecimal requireValidPercent(BigDecimal percent) {
        Objects.requireNonNull(percent, "pourcentage");
        if (percent.signum() <= 0 || percent.compareTo(MAX_PERCENT) >= 0) {
            throw new IllegalArgumentException(
                    "Le pourcentage de l'enseignant doit être strictement compris entre 0 et 100 : "
                            + percent.stripTrailingZeros().toPlainString() + " reçu.");
        }
        if (percent.stripTrailingZeros().scale() > PERCENT_SCALE) {
            throw new IllegalArgumentException(
                    "Le pourcentage de l'enseignant admet au plus deux décimales : "
                            + percent.toPlainString() + " reçu.");
        }
        return percent.setScale(PERCENT_SCALE, ROUNDING);
    }

    /** Part de l'enseignant sur un encaissé donné, arrondie au centime. */
    public static BigDecimal teacherShareOf(BigDecimal net, BigDecimal percent) {
        return money(net).multiply(requireValidPercent(percent))
                .divide(MAX_PERCENT, MONEY_SCALE, ROUNDING);
    }

    /**
     * Paie initiale : partage tout l'encaissé de la série.
     *
     * @throws IllegalArgumentException si l'encaissé n'est pas strictement positif : il n'y a rien à
     *                                  partager (exigence 3.4)
     */
    public static Shares initial(BigDecimal net, BigDecimal percent) {
        BigDecimal base = money(net);
        if (base.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Rien à partager : l'encaissé net de la série est de " + base.toPlainString() + " DA.");
        }
        BigDecimal teacher = teacherShareOf(base, percent);
        return new Shares(base, teacher, base.subtract(teacher));
    }

    /**
     * Écart à régulariser : ce que l'enseignant doit avoir reçu sur l'encaissé actuel, moins ce qu'il
     * a déjà reçu. Positif : complément ; négatif : retenue ; nul : rien à faire.
     *
     * @param netNow       encaissé net actuel de la série
     * @param percent      pourcentage figé de la paie initiale
     * @param teacherPaid  somme des parts enseignant des paies actives de la série
     */
    public static BigDecimal gap(BigDecimal netNow, BigDecimal percent, BigDecimal teacherPaid) {
        return teacherShareOf(netNow, percent).subtract(money(teacherPaid));
    }

    /**
     * Régularisation : couvre l'encaissé apparu depuis la dernière paie, et ramène le cumul de
     * l'enseignant à sa part exacte de l'encaissé actuel.
     *
     * @param netNow      encaissé net actuel
     * @param netCovered  encaissé net couvert par la dernière paie active de la série
     * @param percent     pourcentage figé de la paie initiale
     * @param teacherPaid somme des parts enseignant des paies actives
     * @throws IllegalArgumentException si l'écart est nul : rien à régulariser (exigence 6.5)
     */
    public static Shares regularization(BigDecimal netNow, BigDecimal netCovered, BigDecimal percent,
                                        BigDecimal teacherPaid) {
        BigDecimal teacher = gap(netNow, percent, teacherPaid);
        if (teacher.signum() == 0) {
            throw new IllegalArgumentException(
                    "Rien à régulariser : l'enseignant a déjà reçu sa part de l'encaissé actuel.");
        }
        BigDecimal base = money(netNow).subtract(money(netCovered));
        return new Shares(base, teacher, base.subtract(teacher));
    }

    private static BigDecimal money(BigDecimal value) {
        return Objects.requireNonNull(value, "montant").setScale(MONEY_SCALE, ROUNDING);
    }
}
