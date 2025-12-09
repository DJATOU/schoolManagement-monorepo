package com.school.management.domain.model;

import java.util.List;

public record SeriesHistory(
        Long seriesId,
        String seriesName,
        List<SessionHistory> sessionHistories
) {}