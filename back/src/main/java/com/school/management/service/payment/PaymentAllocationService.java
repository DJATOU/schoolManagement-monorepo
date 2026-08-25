package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Répartition d'un versement sur la chaîne des séries d'un groupe : plafonnement sur la série
 * visée puis report du surplus sur les séries suivantes (exigences 4.1, 4.2, 5.1, 5.2, 5.4,
 * 5.8, 5.9).
 *
 * <h2>Lecture seule, par conception</h2>
 * Ce service ne fait <strong>aucune écriture</strong>. Il isole la seule décision de
 * répartition, ce qui a deux conséquences voulues : il se teste sans base de données, et le
 * refus total du versement non plaçable (exigence 5.11) intervient avant qu'une seule ligne
 * n'ait été écrite. L'application du plan appartient au {@code PaymentProcessingService}.
 *
 * <h2>Le plafond est relu série par série</h2>
 * Chaque étape interroge le {@link PaymentQuoteService}, donc l'exigence 3 s'applique
 * automatiquement à chaque série : réductions, exemptions, rattrapage seul et excédent déjà
 * encaissé sont pris en compte sans traitement particulier ici. Un étudiant exempté sur la
 * série suivante donne simplement un plafond nul, et le surplus poursuit sa route.
 *
 * <h2>Trois motifs distincts d'écartement</h2>
 * Une série soldée, une série vide et une série dont aucune séance n'est facturable à cet
 * étudiant donnent toutes trois un plafond nul, mais n'appellent pas la même réaction : la
 * première est sautée en silence, la deuxième demande la création de ses séances, la troisième
 * une séance postérieure à l'inscription — créer des séances n'y changerait rien. Le plan
 * conserve donc le motif de chaque écartement afin que le message de refus soit exact
 * (exigence 5.12). Le devis fournit les deux décomptes nécessaires pour trancher :
 * {@code billableSessions} et {@code excludedSessions}.
 */
@Service
public class PaymentAllocationService {

    private static final int MONEY_SCALE = PaymentCostCalculator.MONEY_SCALE;
    private static final RoundingMode MONEY_ROUNDING = PaymentCostCalculator.MONEY_ROUNDING;

    private final SessionSeriesRepository sessionSeriesRepository;
    private final PaymentQuoteService paymentQuoteService;

    public PaymentAllocationService(SessionSeriesRepository sessionSeriesRepository,
                                    PaymentQuoteService paymentQuoteService) {
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.paymentQuoteService = paymentQuoteService;
    }

    /**
     * Calcule la répartition d'un versement sans rien écrire.
     *
     * <p>Parcourt les séries du groupe par identifiant croissant à partir de la série visée
     * (celle-ci incluse). Pour chaque série apte, impute le plus petit du reliquat et du plafond
     * encaissable, puis continue jusqu'à épuisement du reliquat ou de la chaîne — sans limite de
     * profondeur (exigence 5.9).</p>
     *
     * @param studentId     l'étudiant qui verse
     * @param groupId       le groupe dont la chaîne de séries est parcourue
     * @param startSeriesId la série visée à la saisie, première de la chaîne
     * @param amount        le montant versé, strictement positif
     * @return le plan de répartition, complet ou porteur d'un reliquat non plaçable
     * @throws CustomServiceException 400 si le montant est nul ou négatif ; 404 si la série
     *                                visée n'appartient pas au groupe
     */
    @Transactional(readOnly = true)
    public AllocationPlan plan(Long studentId, Long groupId, Long startSeriesId, BigDecimal amount) {
        Objects.requireNonNull(studentId, "studentId ne doit pas être nul.");
        Objects.requireNonNull(groupId, "groupId ne doit pas être nul.");
        Objects.requireNonNull(startSeriesId, "startSeriesId ne doit pas être nul.");
        Objects.requireNonNull(amount, "amount ne doit pas être nul.");

        BigDecimal remaining = amount.setScale(MONEY_SCALE, MONEY_ROUNDING);
        if (remaining.signum() <= 0) {
            throw new CustomServiceException(
                    "Le montant à répartir doit être strictement positif, reçu : "
                            + amount.toPlainString() + " DA.",
                    HttpStatus.BAD_REQUEST);
        }

        List<SessionSeriesEntity> chain = chainFrom(groupId, startSeriesId);
        List<AllocationPlan.SeriesAllocation> allocations = new ArrayList<>();
        List<AllocationPlan.SkippedSeries> skipped = new ArrayList<>();

        for (SessionSeriesEntity series : chain) {
            if (remaining.signum() == 0) {
                break;
            }
            PaymentQuoteDTO quote = paymentQuoteService.quote(studentId, series.getId());

            // Exigence 5.8 : une série sans séance facturable ne peut rien accueillir. Ce test
            // est distinct de celui du plafond, et les deux sont nécessaires — c'est lui qui
            // déclenche le message d'action corrective.
            if (quote.billableSessions() == 0) {
                skipped.add(new AllocationPlan.SkippedSeries(
                        series.getId(), series.getName(), unbillableReason(quote)));
                continue;
            }

            BigDecimal take = remaining.min(quote.maxPayable().setScale(MONEY_SCALE, MONEY_ROUNDING));
            if (take.signum() == 0) {
                // Série soldée ou intégralement exemptée : sautée sans être inscrite au plan,
                // le report continue sur la suivante.
                skipped.add(new AllocationPlan.SkippedSeries(
                        series.getId(), series.getName(), AllocationPlan.SkipReason.SETTLED));
                continue;
            }

            allocations.add(new AllocationPlan.SeriesAllocation(
                    series.getId(), series.getName(), take,
                    !series.getId().equals(startSeriesId)));
            remaining = remaining.subtract(take);
        }

        return new AllocationPlan(allocations, skipped, remaining);
    }

    /**
     * Motif d'écartement d'une série sans aucune séance facturable, tranché sur les deux
     * décomptes du devis.
     *
     * <p>Une série <strong>vide</strong> a ses deux décomptes à zéro : créer ses séances
     * l'ouvrira. Une série dont les séances existent mais sont toutes antérieures à
     * l'inscription et non suivies a {@code excludedSessions > 0} : elle n'a rien à ouvrir, et
     * conseiller d'y créer des séances serait faux — c'est le défaut que ce découpage corrige.</p>
     */
    private AllocationPlan.SkipReason unbillableReason(PaymentQuoteDTO quote) {
        return quote.excludedSessions() == 0
                ? AllocationPlan.SkipReason.NO_SESSIONS_PLANNED
                : AllocationPlan.SkipReason.NO_BILLABLE_SESSION_FOR_STUDENT;
    }

    /**
     * Séries du groupe à partir de la série visée, par identifiant croissant.
     *
     * <p>{@code findByGroupId} trie déjà par identifiant croissant : l'ordre du report découle
     * du dépôt et n'est pas reconstitué ici. Une chaîne vide signifie que la série visée
     * n'appartient pas au groupe — si elle en faisait partie, elle serait au minimum sa propre
     * première entrée.</p>
     */
    private List<SessionSeriesEntity> chainFrom(Long groupId, Long startSeriesId) {
        List<SessionSeriesEntity> chain = sessionSeriesRepository.findByGroupId(groupId).stream()
                .filter(series -> series.getId() >= startSeriesId)
                .toList();
        if (chain.isEmpty()) {
            throw new CustomServiceException(
                    "Série " + startSeriesId + " introuvable dans le groupe " + groupId + ".",
                    HttpStatus.NOT_FOUND);
        }
        return chain;
    }
}
