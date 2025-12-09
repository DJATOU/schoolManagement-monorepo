package com.school.management.domain.model;

import java.util.List;

public record StudentFullHistory(
        Long studentId,
        String studentName,
        List<GroupHistory> groupHistories
) {}
