package com.school.management.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
public class GroupPaymentStatus {
    private Long groupId;
    private String groupName;
    private List<SeriesPaymentStatus> series;

    public GroupPaymentStatus(Long id, String name, List<SeriesPaymentStatus> seriesStatuses) {
        this.groupId = id;
        this.groupName = name;
        this.series = seriesStatuses;
    }

    // getters, setters, et constructeurs
}
