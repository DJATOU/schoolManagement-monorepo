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
}
