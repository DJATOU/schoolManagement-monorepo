package com.school.management.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * DTO for Payment Management Search Results
 * Contains complete payment information including student, group, series, and session details
 *
 * <p>Les trois champs de remboursement ne viennent pas de la requête : ils sont renseignés après
 * coup par {@code PaymentLineRefundAnnotator}, un remboursement vivant dans une autre table que
 * les lignes. Le statut {@code paymentStatus} est alors celui du versement <strong>net</strong> des
 * remboursements (décision du propriétaire produit : on déduit ce qui a été rendu).</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class PaymentDetailSearchDTO {
    private Long id;

    // Student information
    private String studentFirstName;
    private String studentLastName;
    private Long studentId;

    // Group information
    private String groupName;
    private Long groupId;

    // Series information
    private String seriesName;
    private Long seriesId;

    // Session information
    private String sessionName;
    private Long sessionId;

    // Payment details
    private Double amountPaid;
    private Boolean active;
    private Boolean permanentlyDeleted;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateCreation;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Timestamp paymentDate;

    // Payment parent information
    private Long paymentId;
    private String paymentStatus;

    // Additional info
    private Boolean isCatchUp;

    /**
     * Part des remboursements du versement imputée à cette ligne, échelle 2. Un remboursement porte
     * sur le versement d'une série : il est imputé aux lignes les plus récentes d'abord, comme dans
     * l'historique de l'étudiant, où l'argent rendu découvre les dernières séances.
     */
    private BigDecimal refundedAmount;

    /** Montant de la ligne diminué de sa part remboursée, jamais négatif. */
    private BigDecimal netAmount;

    /** Total remboursé sur le versement dont la ligne fait partie, toutes lignes confondues. */
    private BigDecimal paymentRefunded;

    /**
     * Constructor matching the JPQL query field order and types
     * IMPORTANT: This constructor must match EXACTLY the order and types from Hibernate
     * Based on error message, Hibernate returns:
     * - dateCreation as LocalDateTime (from BaseEntity)
     * - paymentDate as Timestamp (from PaymentDetailEntity)
     */
    public PaymentDetailSearchDTO(
            Long id,
            String studentFirstName,
            String studentLastName,
            Long studentId,
            String groupName,
            Long groupId,
            String seriesName,
            Long seriesId,
            String sessionName,
            Long sessionId,
            Double amountPaid,
            Boolean active,
            Boolean permanentlyDeleted,
            LocalDateTime dateCreation,
            Timestamp paymentDate,
            Long paymentId,
            String paymentStatus,
            Boolean isCatchUp
    ) {
        this.id = id;
        this.studentFirstName = studentFirstName;
        this.studentLastName = studentLastName;
        this.studentId = studentId;
        this.groupName = groupName;
        this.groupId = groupId;
        this.seriesName = seriesName;
        this.seriesId = seriesId;
        this.sessionName = sessionName;
        this.sessionId = sessionId;
        this.amountPaid = amountPaid;
        this.active = active;
        this.permanentlyDeleted = permanentlyDeleted;
        this.dateCreation = dateCreation;
        this.paymentDate = paymentDate;
        this.paymentId = paymentId;
        this.paymentStatus = paymentStatus;
        this.isCatchUp = isCatchUp;
    }
}
