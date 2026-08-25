package com.school.management.dto.serie;

import com.school.management.dto.session.SessionHistoryDTO;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeriesHistoryDTO {
    private Long seriesId;
    private String seriesName;
    private String paymentStatus;
    // TODO(tâche 16) : migrer ces champs monétaires vers BigDecimal en même temps que
    // StudentHistoryService, pour rester cohérent avec le PaymentCostCalculator.
    private Double totalAmountPaid;
    private Double totalCost;
    /**
     * Part du montant versé qui couvre effectivement des séances (somme des montants
     * affectés). Distinct de {@code totalAmountPaid} : quand l'étudiant a versé plus que le
     * coût de la série, l'écart n'est pas affecté. Sans ce champ, l'en-tête annonçait un
     * versement que le détail des séances ne pouvait pas justifier.
     */
    private Double totalAllocated;
    /** Trop-perçu : part versée au-delà du coût de la série ({@code versé − coût}), ou 0. */
    private Double totalOverpaid;
    private List<SessionHistoryDTO> sessions;
    // Exemption : vrai lorsque l'étudiant est exempté (réduction 100 %) pour cette série ;
    // pilote la légende « Présent et exempté ».
    private Boolean isExempted;
    // Total remboursé sur la série (BigDecimal, échelle 2)
    private BigDecimal totalRefunded;
    /**
     * Nombre de séances facturables à cet étudiant sur la série
     * ({@code BillableSessions.billableCount()}), exigence 11.6.
     *
     * <p>{@code totalCost} seul ne permet pas de justifier un coût inférieur au coût nominal de
     * la série : le récapitulatif doit dire sur combien de séances ce coût porte. Le décompte
     * n'est pas déductible du nombre de lignes affichées, qui inclut les séances écartées.</p>
     */
    private Integer billableSessions;

    /**
     * Prix d'une séance après réduction, tel que retenu pour facturer cet étudiant.
     *
     * <p>Relayé depuis {@code PaymentQuoteDTO.netPricePerSession()} sans recalcul. L'interface
     * en a besoin pour énoncer le coût en clair — « 2 séances × 6 000 DA = 12 000 DA » — au lieu
     * du terme « prorata », que la famille ne peut pas interpréter. Le déduire de
     * {@code totalCost / billableSessions} serait à la fois une division par zéro potentielle et
     * une présentation du prix net comme s'il était le tarif catalogue.</p>
     */
    private BigDecimal unitPriceNet;

    /**
     * Tarif catalogue d'une séance, avant réduction.
     *
     * <p>Relayé depuis {@code PaymentQuoteDTO.grossPricePerSession()}. Il n'entre dans aucun
     * calcul : il sert uniquement à afficher le tarif barré à côté du prix net lorsqu'une
     * réduction s'applique. Sans lui, un étudiant réduit de moitié voit « 3 000 DA » sans que
     * rien n'explique pourquoi ce n'est pas 6 000, et l'administrateur ne peut pas le justifier
     * à la famille.</p>
     */
    private BigDecimal unitPriceGross;
}
