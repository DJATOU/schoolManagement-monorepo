package com.school.management.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SessionPaymentStatus {
    private Long sessionId;
    private String sessionName;
    private boolean paymentOverdue;

    // Nouvelles propriétés pour calcul précis
    private Boolean isPresent;  // Étudiant présent à cette session
    private Boolean isPaidEvenIfAbsent;  // Session payable même si absent
    private Double amountDue;  // Montant dû pour cette session
    private Double amountPaid;  // Montant payé pour cette session

    public SessionPaymentStatus(Long id, String title, boolean isOverdue) {
        this.sessionId = id;
        this.sessionName = title;
        this.paymentOverdue = isOverdue;
    }

    public SessionPaymentStatus(Long id, String title, boolean isOverdue,
                               Boolean isPresent, Boolean isPaidEvenIfAbsent,
                               Double amountDue, Double amountPaid) {
        this.sessionId = id;
        this.sessionName = title;
        this.paymentOverdue = isOverdue;
        this.isPresent = isPresent;
        this.isPaidEvenIfAbsent = isPaidEvenIfAbsent;
        this.amountDue = amountDue;
        this.amountPaid = amountPaid;
    }

    // Getters and setters

}