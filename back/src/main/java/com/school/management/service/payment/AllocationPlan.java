package com.school.management.service.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Plan de répartition d'un versement sur la chaîne des séries d'un groupe (exigences 4.1, 4.2,
 * 5.1, 5.2, 5.8, 5.9).
 *
 * <p>Le plan est le résultat d'un calcul <strong>en lecture seule</strong> : il décrit ce qui
 * <em>serait</em> imputé, sans rien écrire. C'est ce qui rend le refus total de l'exigence 5.11
 * trivial — lorsque le plan ne couvre pas le versement, aucune écriture n'a encore eu lieu, il
 * n'y a aucune annulation à orchestrer.</p>
 *
 * <h2>Pourquoi le plan porte aussi les séries écartées</h2>
 * <strong>Trois</strong> situations très différentes donnent un plafond nul, et les confondre
 * produirait un message de refus trompeur — pire, une action corrective qui ne corrige rien :
 * <ul>
 *   <li>une série <strong>soldée</strong> (ou intégralement exemptée) : elle a des séances
 *       facturables, mais plus rien à encaisser. Rien à faire, le report continue ;</li>
 *   <li>une série <strong>sans aucune séance planifiée</strong> : elle existe mais est vide.
 *       Elle bloque le report, et l'action corrective est bien de créer ses séances ;</li>
 *   <li>une série <strong>dont aucune séance n'est facturable à cet étudiant</strong> : elle a
 *       des séances, toutes antérieures à son inscription et non suivies. Créer des séances
 *       supplémentaires n'y changerait rien ; il faut une séance postérieure à l'inscription,
 *       ou constater que l'étudiant ne doit rien sur cette série.</li>
 * </ul>
 * La liste {@code skipped} et son motif permettent donc au message de refus construit à
 * l'encaissement de <em>nommer</em> la série bloquante <em>et</em> l'action qui la débloque
 * réellement (exigence 5.12). Annoncer « créez ses séances » devant une série qui en compte
 * déjà quatre serait un conseil faux, donc pire qu'un conseil absent.
 *
 * @param allocations montants imputables, dans l'ordre de parcours de la chaîne (identifiant
 *                    de série croissant) ; vide lorsque aucune série ne peut rien recevoir
 * @param skipped     séries écartées de la chaîne, avec le motif de leur écartement
 * @param unplaceable reliquat qui n'a pu être placé sur aucune série, jamais négatif ; nul
 *                    lorsque le plan couvre la totalité du versement
 */
public record AllocationPlan(
        List<SeriesAllocation> allocations,
        List<SkippedSeries> skipped,
        BigDecimal unplaceable) {

    public AllocationPlan {
        allocations = List.copyOf(Objects.requireNonNull(allocations, "allocations ne doit pas être nul."));
        skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped ne doit pas être nul."));
        Objects.requireNonNull(unplaceable, "unplaceable ne doit pas être nul.");
    }

    /**
     * Montant imputable sur une série donnée.
     *
     * @param seriesId    identifiant de la série créditée
     * @param seriesName  nom de la série, pour que le reçu et les messages puissent la nommer
     *                    sans relire la base
     * @param amount      montant imputé, strictement positif et inférieur ou égal au plafond
     *                    encaissable de la série
     * @param carriedOver vrai lorsque la série n'est pas celle visée à la saisie : le montant
     *                    est alors un report et doit être tracé (exigence 6.1)
     */
    public record SeriesAllocation(Long seriesId, String seriesName, BigDecimal amount,
                                   boolean carriedOver) {
    }

    /**
     * Série écartée du plan, avec le motif exact de son écartement.
     *
     * @param seriesId   identifiant de la série écartée
     * @param seriesName nom de la série, nécessaire pour nommer la série à ouvrir
     * @param reason     motif de l'écartement
     */
    public record SkippedSeries(Long seriesId, String seriesName, SkipReason reason) {
    }

    /**
     * Les trois motifs d'écartement d'une série, à ne jamais confondre : chacun appelle une
     * action corrective différente, et deux d'entre eux appelleraient une action inutile s'ils
     * étaient fusionnés.
     */
    public enum SkipReason {

        /**
         * Série soldée ou intégralement exemptée : son plafond encaissable est nul alors
         * qu'elle comporte des séances facturables. Elle est sautée et le report continue ;
         * aucune action de l'administrateur n'est attendue.
         */
        SETTLED,

        /**
         * Série <strong>sans aucune séance planifiée</strong> : ni facturable, ni écartée. Elle
         * n'est donc pas ouverte et ne peut rien accueillir, quel que soit son plafond théorique
         * (exigence 5.8). L'action corrective est de créer ses séances.
         */
        NO_SESSIONS_PLANNED,

        /**
         * Série <strong>dont aucune séance n'est facturable à cet étudiant</strong> : elle
         * comporte des séances, mais toutes antérieures à son inscription et non suivies
         * (exigences 1.1, 1.3). Elle ne peut rien accueillir non plus, mais créer des séances
         * supplémentaires n'y changerait rien : il faut une séance postérieure à l'inscription,
         * ou constater que l'étudiant ne doit rien sur cette série.
         */
        NO_BILLABLE_SESSION_FOR_STUDENT
    }

    /** Vrai lorsque la totalité du versement a trouvé une série à créditer. */
    public boolean isComplete() {
        return unplaceable.signum() == 0;
    }

    /**
     * Somme des montants imputables. Lorsque le plan est incomplet, c'est exactement le
     * <strong>maximum encaissable</strong> sur la chaîne, montant que le message de refus doit
     * annoncer à l'administrateur (exigence 5.12).
     */
    public BigDecimal totalAllocated() {
        return allocations.stream()
                .map(SeriesAllocation::amount)
                .reduce(BigDecimal.ZERO.setScale(PaymentCostCalculator.MONEY_SCALE,
                        PaymentCostCalculator.MONEY_ROUNDING), BigDecimal::add);
    }

    /**
     * Première série <strong>bloquante</strong> rencontrée sur la chaîne : celle dont l'écart
     * demande une action de l'administrateur, par opposition à une série soldée qui n'en demande
     * aucune.
     *
     * <p>C'est l'unique accesseur dont le message de refus a besoin : son
     * {@link SkippedSeries#reason()} choisit la formulation, et son
     * {@link SkippedSeries#seriesName()} la nomme. Renvoyer directement le motif évite que
     * l'appelant reconstitue la distinction à partir de la liste brute — c'est exactement cette
     * reconstitution approximative qui produisait « la série ne comporte aucune séance » devant
     * une série de quatre séances.</p>
     *
     * @return la série bloquante, vide lorsque le blocage vient d'ailleurs — toutes les séries
     *         soldées, ou aucune série au-delà de celle visée
     */
    public Optional<SkippedSeries> firstBlockingSeries() {
        return skipped.stream()
                .filter(series -> series.reason() != SkipReason.SETTLED)
                .findFirst();
    }
}
