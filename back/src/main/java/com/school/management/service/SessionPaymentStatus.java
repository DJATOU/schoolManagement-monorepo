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
    private boolean isPaymentOverdue;

    public SessionPaymentStatus(Long id, String title, boolean isOverdue) {
        this.sessionId = id;
        this.sessionName = title;
        this.isPaymentOverdue = isOverdue;
    }

    // Getters and setters

}