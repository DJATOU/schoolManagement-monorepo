package com.school.management.dto.session;

import lombok.*;

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
    private Double amountPaid;
    private String attendanceStatus;
    private Boolean isJustified;
    private String description;
    private Date paymentDate;
    private Boolean catchUpSession;
}
