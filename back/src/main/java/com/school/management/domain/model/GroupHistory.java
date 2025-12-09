package com.school.management.domain.model;

import java.util.List;

public record GroupHistory(
        Long groupId,
        String groupName,
        List<SeriesHistory> seriesHistories
) {}