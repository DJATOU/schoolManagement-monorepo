package com.school.management.dto.session;

import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionHistoryDTO {
    private Long sessionId;
    private String sessionName;
    private Date sessionDate;
    private String paymentStatus;
    // TODO(tâche 16) : migrer ce champ monétaire vers BigDecimal en même temps que
    // StudentHistoryService, pour rester cohérent avec le PaymentCostCalculator.
    private Double amountPaid;
    private String attendanceStatus;
    private Boolean isJustified;
    private String description;
    private Date paymentDate;
    // Indicateur de séance de rattrapage (sert d'indicateur catch-up pour la légende)
    private Boolean catchUpSession;
    // Présence exemptée : vrai lorsque l'étudiant bénéficie d'une exemption (réduction 100 %)
    // sur cette séance ; pilote la légende « Présent et exempté ».
    private Boolean isExempted;
    // Montant remboursé rattaché à cette séance (BigDecimal, échelle 2)
    private BigDecimal refundedAmount;
    /**
     * Séance facturable à cet étudiant : vrai lorsqu'elle appartient à
     * {@code BillableSessions.billable()}, faux lorsqu'elle appartient à {@code .excluded()}
     * (exigences 11.3, 11.4).
     *
     * <p>Le verdict était auparavant approximé côté interface (« aucune assiduité renseignée et
     * aucun montant affecté »), ce qui classait à tort non facturée une séance future encore
     * sans feuille de présence. La règle du prorata vit dans le backend : le verdict aussi.</p>
     */
    private Boolean billable;
    /**
     * Motif d'inclusion (ou d'exclusion) de la séance dans la facturation.
     *
     * <p>Complète {@code billable} : il ne suffit pas de savoir qu'une séance est facturée, il
     * faut savoir <em>pourquoi</em> pour n'étiqueter « rattrapage » que les séances antérieures
     * à l'inscription facturées parce que suivies (exigence 11.5).</p>
     */
    private BillingInclusionReason inclusionReason;
    /**
     * Montant net dû pour cette séance, réduction appliquée. Nul pour une séance non
     * facturable ou dévalidée.
     *
     * <p>Distinct de {@link #amountPaid}, qui est la part des versements affectée à cette
     * séance. L'interface a besoin des deux : une séance suivie et impayée doit annoncer ce
     * qu'il reste à régler, or {@code amountPaid} vaut alors zéro et ne dit rien du montant
     * attendu.</p>
     */
    private BigDecimal amountDue;
    /**
     * Reste à régler sur cette séance : {@link #amountDue} diminué de la part déjà affectée,
     * jamais négatif. Nul pour une séance non facturable ou dévalidée.
     *
     * <p>Exposé plutôt que laissé à la charge de l'interface : sur une séance partiellement
     * couverte, annoncer le montant dû complet surévaluerait la dette, et faire la
     * soustraction dans le gabarit y installerait de l'arithmétique de facturation.</p>
     */
    private BigDecimal amountRemaining;
    /**
     * Séance manquée par l'étudiant, puis rattrapée dans un autre groupe (exigence 1.4).
     *
     * <p>Vrai sur la séance <strong>manquée</strong>, pas sur celle de rattrapage. La présence reste
     * une absence : la mention « Rattrapée » est un affichage dérivé, jamais une réécriture de la
     * feuille de présence.</p>
     */
    private Boolean caughtUpElsewhere;
    /** Date de la séance de rattrapage, pour situer la mention « Rattrapée » (exigence 1.4). */
    private Date caughtUpOnDate;
    /** Groupe où la séance a été rattrapée (exigence 1.4). */
    private String caughtUpInGroupName;
    /**
     * Séance de la série d'accueil écartée parce qu'elle est déjà facturée dans la série d'origine
     * du rattrapage compensatoire qui la couvre (exigence 2.9).
     *
     * <p>Sans ce motif, une séance suivie mais non facturée ressemble à une erreur de calcul. Le
     * distinguer de {@code billable = false} importe : les deux se lisent « non facturée », mais
     * seul celui-ci s'explique par « déjà payée ailleurs ».</p>
     */
    private Boolean billedInOriginSeries;
    /** Nom de la série d'origine qui facture cette séance (exigence 2.9). */
    private String originSeriesName;
    /** Groupe de la série d'origine (exigence 2.9). */
    private String originGroupName;
    /** Date de la séance manquée correspondante, côté série d'origine (exigence 2.9). */
    private Date originSessionDate;
    /**
     * Séance de rattrapage dont la séance manquée n'est pas déterminable (exigence 1.10).
     *
     * <p>Le signaler explicitement évite de laisser croire à une donnée manquante à l'affichage :
     * l'historique reste complet, c'est le lien vers la séance d'origine qui est absent.</p>
     */
    private Boolean missedSessionUnknown;
    /**
     * Auteur de la dernière modification de la justification, nul si jamais modifiée (exigence 5.9).
     */
    private String justificationUpdatedBy;
    /** Horodatage de la dernière modification de la justification (exigence 5.9). */
    private Date justificationUpdatedAt;
}
