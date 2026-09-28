package com.school.management.service.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calculateur de coûts de paiement — source unique de vérité pour les deux
 * montants distincts définis par les règles métier (business-rules.md §5).
 *
 * <p>Cette classe est <b>pure</b> : aucune dépendance vers les entités JPA, les
 * repositories ou le schéma. Elle reçoit quatre valeurs déjà résolues en amont
 * et ne fait que du calcul. Le rattachement aux entités (gathering des séances
 * assistées cross-group, lecture du taux d'exemption, définition du "mois")
 * est volontairement laissé à l'étape de câblage (task 1.3).
 *
 * <h2>Les deux quantités — NE PAS CONFONDRE (audit H5)</h2>
 * <ul>
 *   <li><b>monthTotalCost</b> = plannedSessions × pricePerSession — ce que coûte
 *       le mois complet. Sert aux reçus et à "le mois est-il soldé".</li>
 *   <li><b>amountDueSoFar</b> = attendedSessions × pricePerSession — le seuil de
 *       retard. Seules les séances <em>présentes</em> comptent (le comptage
 *       isPresent / cross-group est fait en amont).</li>
 * </ul>
 *
 * <h2>Exemption / réduction (business-rules.md §10, requirement 12)</h2>
 * Le paramètre {@code exemptionRate} reçoit désormais le <b>taux de réduction
 * résolu</b> pour le contexte de facturation : {@code DiscountService} sélectionne
 * l'unique portée applicable la plus spécifique (Session &gt; Série &gt; Groupe) et
 * fournit ce taux. Une exemption totale (membre d'un groupe exempté) n'est qu'un
 * cas particulier : une réduction de portée Groupe au taux 1.00. Le calcul reste
 * identique quelle que soit la portée d'origine.
 *
 * <p>Le taux est une fraction [0.00 ; 1.00] appliquée <b>uniquement au montant dû</b> :
 * {@code montant = base × (1 − taux)}. Un taux de 1.00 ramène les deux montants à
 * 0 — l'étudiant est donc toujours "à jour" (jamais en retard) quelle que soit la
 * présence.
 *
 * <h2>Précision monétaire (audit H4)</h2>
 * Tous les montants sont des {@link BigDecimal} arrondis à 2 décimales en
 * {@link RoundingMode#HALF_UP}.
 */
public final class PaymentCostCalculator {

    /** Échelle (nombre de décimales) appliquée à tous les montants monétaires. */
    public static final int MONEY_SCALE = 2;

    /** Politique d'arrondi appliquée à tous les montants monétaires. */
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final int plannedSessions;
    private final int attendedSessions;
    private final BigDecimal pricePerSession;
    private final BigDecimal exemptionRate;

    /**
     * @param plannedSessions  nombre de séances planifiées pour la série (valeur
     *                         admin, série-level, ex. {@code series.getTotalSessions()}).
     *                         Doit être >= 0.
     * @param attendedSessions nombre de séances <b>présentes</b> déjà comptées
     *                         (cross-group / même matière, résolu en amont). Doit être >= 0.
     * @param pricePerSession  prix unitaire d'une séance. Non null, >= 0.
     * @param exemptionRate    taux de réduction résolu (portée Session/Série/Groupe
     *                         résolue par {@code DiscountService}), sous forme de
     *                         fraction [0.00 ; 1.00]. Une exemption totale est le cas
     *                         particulier d'un taux de 1.00. Non null.
     * @throws IllegalArgumentException si une contrainte ci-dessus est violée.
     */
    public PaymentCostCalculator(int plannedSessions,
                                 int attendedSessions,
                                 BigDecimal pricePerSession,
                                 BigDecimal exemptionRate) {
        if (plannedSessions < 0) {
            throw new IllegalArgumentException("plannedSessions must be >= 0, got: " + plannedSessions);
        }
        if (attendedSessions < 0) {
            throw new IllegalArgumentException("attendedSessions must be >= 0, got: " + attendedSessions);
        }
        Objects.requireNonNull(pricePerSession, "pricePerSession must not be null");
        if (pricePerSession.signum() < 0) {
            throw new IllegalArgumentException("pricePerSession must be >= 0, got: " + pricePerSession);
        }
        Objects.requireNonNull(exemptionRate, "exemptionRate must not be null");
        if (exemptionRate.compareTo(BigDecimal.ZERO) < 0 || exemptionRate.compareTo(ONE) > 0) {
            throw new IllegalArgumentException(
                    "exemptionRate must be a fraction between 0.00 and 1.00, got: " + exemptionRate);
        }

        this.plannedSessions = plannedSessions;
        this.attendedSessions = attendedSessions;
        this.pricePerSession = pricePerSession;
        this.exemptionRate = exemptionRate;
    }

    /**
     * Coût total du mois après exemption : {@code plannedSessions × price × (1 − rate)}.
     * Utilisé pour {@link #isMonthFullyPaid(BigDecimal)} et les reçus.
     *
     * @return le coût total, échelle 2, HALF_UP.
     */
    public BigDecimal monthTotalCost() {
        return applyExemption(baseAmount(plannedSessions));
    }

    /**
     * Montant dû à ce jour après exemption : {@code attendedSessions × price × (1 − rate)}.
     * Seuil de retard. Un taux de 1.00 rend ce montant nul.
     *
     * @return le montant dû, échelle 2, HALF_UP.
     */
    public BigDecimal amountDueSoFar() {
        return applyExemption(baseAmount(attendedSessions));
    }

    /**
     * Indique si l'étudiant est en retard : {@code amountPaid < amountDueSoFar}.
     * À un taux d'exemption de 1.00, {@code amountDueSoFar} vaut 0, donc l'étudiant
     * n'est jamais en retard.
     *
     * @param amountPaid montant déjà versé. Non null, >= 0.
     * @return true si en retard.
     */
    public boolean isLate(BigDecimal amountPaid) {
        return normalizedPaid(amountPaid).compareTo(amountDueSoFar()) < 0;
    }

    /**
     * Indique si le mois est intégralement soldé : {@code amountPaid >= monthTotalCost}.
     *
     * @param amountPaid montant déjà versé. Non null, >= 0.
     * @return true si le mois est soldé.
     */
    public boolean isMonthFullyPaid(BigDecimal amountPaid) {
        return normalizedPaid(amountPaid).compareTo(monthTotalCost()) >= 0;
    }

    /**
     * Point d'extension différé — réconciliation de fin d'année (business-rules.md
     * §DEFERRED). La question "un étudiant doit-il payer les séances où il était
     * absent en fin d'année, et l'absence justifiée change-t-elle ce calcul ?" est
     * <b>volontairement non implémentée</b> ici. Le calculateur quotidien ne compte
     * que les séances présentes ({@link #amountDueSoFar()}).
     *
     * <p>Quand la décision produit sera prise, exposer la logique de réconciliation
     * via une méthode dédiée (ex. {@code reconciledYearEndDue(...)}) plutôt que
     * d'altérer {@link #amountDueSoFar()}, afin de préserver la sémantique du statut
     * quotidien.
     */
    // Intentionnellement laissé vide : ne PAS implémenter sans décision produit.

    private BigDecimal baseAmount(int sessions) {
        return pricePerSession.multiply(BigDecimal.valueOf(sessions));
    }

    private BigDecimal applyExemption(BigDecimal base) {
        BigDecimal multiplier = ONE.subtract(exemptionRate);
        return base.multiply(multiplier).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal normalizedPaid(BigDecimal amountPaid) {
        Objects.requireNonNull(amountPaid, "amountPaid must not be null");
        if (amountPaid.signum() < 0) {
            throw new IllegalArgumentException("amountPaid must be >= 0, got: " + amountPaid);
        }
        return amountPaid.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
