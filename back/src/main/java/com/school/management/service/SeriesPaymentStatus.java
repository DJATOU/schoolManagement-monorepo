package com.school.management.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
public class SeriesPaymentStatus {
    private Long sessionSeriesId;
    private String seriesName;
    private List<SessionPaymentStatus> sessions;

    public SeriesPaymentStatus(Long id, List<SessionPaymentStatus> sessionStatuses) {
        this.sessionSeriesId = id;
        this.sessions = sessionStatuses;
    }

    // getters, setters, et constructeurs

}