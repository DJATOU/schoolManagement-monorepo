package com.school.management.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
@Getter
@Setter
@NoArgsConstructor
public class SeriesPaymentStatus {
    private Long sessionSeriesId;
    private String seriesName;
    private List<SessionPaymentStatus> sessions;

    /**
     * Exemption totale : l'étudiant bénéficie d'une réduction de 100 % sur cette série.
     *
     * <p>Indispensable pour distinguer « rien à payer parce qu'exempté » de « rien à payer
     * parce qu'aucune séance n'a encore été suivie » : dans les deux cas le montant dû vaut
     * zéro, mais le statut affiché n'est pas le même.</p>
     */
    private boolean exempted;

    /**
     * Coût_Série_Prorata : séances facturables × prix net.
     *
     * <p>Ce n'est <strong>jamais</strong> {@code total_sessions × prix} (exigence 11.1). Un
     * étudiant arrivé à la dernière séance d'une série de quatre doit une seule séance, et le
     * statut de sa série s'évalue contre ce montant. Nul lorsque le coût n'a pas pu être
     * résolu.</p>
     */
    private BigDecimal prorataCost;

    /** Montant versé sur la série : registre des paiements minoré des remboursements. */
    private BigDecimal amountPaid;

    /** Nombre de séances facturables à l'étudiant sur la série. */
    private int billableSessions;

    /**
     * Série soldée : le montant versé atteint le Coût_Série_Prorata (exigence 11.2).
     *
     * <p>Sans ce champ, le seul verdict disponible était le retard séance par séance, qui ne
     * répond pas à la question « cette série est-elle soldée ? » : une séance sans fiche de
     * présence est ignorée, si bien qu'un étudiant arrivé en fin de série et ayant tout réglé
     * n'affichait aucun statut de série.</p>
     */
    private boolean fullyPaid;

    /** En retard : le montant versé n'atteint pas le Montant_Dû_À_Ce_Jour de la série. */
    private boolean late;

    public SeriesPaymentStatus(Long id, List<SessionPaymentStatus> sessionStatuses, boolean exempted) {
        this.sessionSeriesId = id;
        this.sessions = sessionStatuses;
        this.exempted = exempted;
    }

    // getters, setters, et constructeurs

}