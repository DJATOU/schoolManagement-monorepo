package com.school.management.service;

import com.school.management.repository.RefundRepository;
import org.springframework.stereotype.Service;

/**
 * Attribue le numéro de pièce d'un remboursement (exigences 6.4 à 6.7, 6.12).
 *
 * <p>Format {@code REMB-AAAA-NNNN} : {@code AAAA} l'année civile du remboursement, {@code NNNN} son
 * rang dans cette année, complété à quatre chiffres.</p>
 *
 * <h2>Ce que ce service ne garantit pas seul</h2>
 * Le rang est calculé par lecture du maximum de l'année, ce qui n'est pas atomique : deux créations
 * simultanées peuvent calculer le même rang. L'unicité est donc portée par la <strong>contrainte de
 * stockage</strong> {@code uk_refund_number}, et non par ce calcul (exigence 6.6). C'est un choix
 * délibéré : une contrainte de base vaut quel que soit le nombre d'instances de l'application, là où
 * un verrou applicatif ne vaut que dans une seule JVM. Le service appelant rejoue l'enregistrement
 * avec un rang recalculé lorsque la contrainte rejette (exigence 6.14).
 *
 * <h2>Pourquoi les rangs manquants sont tolérés</h2>
 * Un rang consommé par une tentative échouée n'est ni comblé ni réutilisé (exigence 6.5). Combler un
 * trou reviendrait à réattribuer un numéro qui a pu être imprimé sur un reçu remis à une famille, et
 * produirait deux pièces comptables homonymes. Une séquence à trous est un désagrément ; deux pièces
 * portant le même numéro est une erreur de caisse.
 */
@Service
public class RefundNumberService {

    /** Préfixe commun à tous les numéros de pièce de remboursement. */
    private static final String PREFIX = "REMB-";

    /** Largeur minimale du rang. Au-delà de 9999, le rang s'écrit sans troncature. */
    private static final int RANK_WIDTH = 4;

    private final RefundRepository refundRepository;

    public RefundNumberService(RefundRepository refundRepository) {
        this.refundRepository = refundRepository;
    }

    /**
     * Numéro de pièce suivant pour l'année civile donnée.
     *
     * <p>Le rang retourné est strictement supérieur au plus grand rang déjà attribué pour cette
     * année, et vaut 1 lorsque l'année ne porte encore aucun numéro (exigence 6.12). Les années sont
     * indépendantes : le passage d'une année à l'autre fait repartir la séquence à 1.</p>
     *
     * @param year année civile du remboursement
     * @return un numéro de la forme {@code REMB-2026-0001}
     */
    public String nextNumber(int year) {
        String prefix = prefixFor(year);
        int nextRank = refundRepository.findMaxRankForPrefix(prefix) + 1;
        return prefix + formatRank(nextRank);
    }

    /** Préfixe annuel, également utilisé pour borner la recherche du rang maximum. */
    public String prefixFor(int year) {
        return PREFIX + year + "-";
    }

    /**
     * Rang complété à gauche par des zéros jusqu'à {@value #RANK_WIDTH} chiffres, puis écrit
     * intégralement au-delà.
     *
     * <p>Tronquer au-delà de 9999 casserait l'unicité au moment le moins opportun : une école de
     * cette taille n'y arrivera pas, mais un numéro tronqué serait un doublon silencieux, alors
     * qu'un numéro à cinq chiffres n'est qu'inhabituel.</p>
     */
    private String formatRank(int rank) {
        return String.format("%0" + RANK_WIDTH + "d", rank);
    }
}
