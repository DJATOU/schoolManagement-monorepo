package com.school.management.dto;

import com.school.management.persistance.CatchUpBillingState;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceDTO {

    private Long id;
    private Long studentId; // ID of the student
    private Long sessionId; // ID of the session
    private Boolean isPresent;
    private Boolean isJustified;

    private Long sessionSeriesId; // ID of the session series
    private Long groupId; // ID of the group

    // Dates from BaseEntity
    private Date dateCreation;
    private Date dateUpdate;
    private String createdBy;
    private String updatedBy;
    private Boolean active;
    private String description;
    private Boolean isCatchUp;

    /**
     * Séance manquée que ce rattrapage vient compenser.
     *
     * <p>Ce champ manquait, et son absence était l'un des trois verrous qui rendaient la
     * facturation d'un rattrapage impossible à décider correctement : le client ne pouvait pas
     * transmettre le lien, {@code AttendanceMapper} ne le mappait pas, et le calcul retombait donc
     * sur le groupe d'accueil sans que personne ne l'ait choisi.</p>
     *
     * <p>Nul est <strong>légitime</strong> lorsque l'état est {@code HOST_BILLED} : aucune place
     * n'est réservée ailleurs, il n'y a donc aucune séance à rattraper.</p>
     */
    private Long missedSessionId;

    /** État de facturation du rattrapage ; nul pour une présence ordinaire. */
    private CatchUpBillingState catchUpBillingState;

    /**
     * Décision « la séance manquée était-elle déjà payée ? ». Nul signifie « non tranché », et non
     * « non » : c'est ce qui rend l'absence de décision représentable, donc le choix explicite.
     */
    private Boolean missedSessionAlreadyPaid;
}
