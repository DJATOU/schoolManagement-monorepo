package com.school.management.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDTO {

    /**
     * Identifiant de la ligne de paiement. Exposé en lecture pour permettre au client de
     * référencer le paiement (reçu imprimable) ; ignoré en écriture par le mapper.
     */
    private Long id;

    // @NotBlank ne s'applique qu'aux chaînes : sur un Long il provoque une
    // UnexpectedTypeException dès la première requête validée. Remplacé par @NotNull.
    @NotNull(message = "Student ID cannot be null")
    private Long studentId;

    // Volontairement sans contrainte : les deux points d'entrée sont exclusifs. /process
    // renseigne sessionSeriesId sans sessionId, /process/catch-up l'inverse. Les rendre
    // obligatoires rejetterait les deux. Chaque service valide ce dont il a besoin.
    private Long sessionId;

    private Long sessionSeriesId;

    // Strictement positif : un versement nul n'encaisse rien. Les services appliquent le même
    // refus, avec un message contextuel (série soldée, étudiant exempté).
    @NotNull(message = "Amount paid cannot be null")
    @Positive(message = "Amount paid must be greater than 0")
    private Double amountPaid;

    private Date paymentForMonth;

    /**
     * Date d'encaissement retenue par le serveur. Exposée en lecture pour que le reçu porte
     * la date faisant foi plutôt que l'heure du poste client ; ignorée en écriture.
     */
    private Date paymentDate;

    private String status;
    private String paymentMethod;
    private String paymentDescription;

    // Note libre facultative associée au paiement (peut être null si non renseignée)
    private String notes;

    @NotNull(message = "Group ID cannot be null")
    private Long groupId;

    // Additional fields for detailed information
    private Double totalSeriesCost;
    private Double totalPaidForSeries;
    private Double amountOwed;


}
