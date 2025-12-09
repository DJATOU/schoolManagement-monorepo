package com.school.management.domain.model;

import java.time.LocalDateTime;

public record SessionHistory(
        Long sessionId,
        String sessionName,
        LocalDateTime sessionDate,
        String attendanceStatus,
        Double amountPaid,
        LocalDateTime paymentDate
) {}