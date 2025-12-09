package com.school.management.dto.session;

import lombok.*;

import java.time.LocalDate;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionSearchCriteriaDTO {
    private String title;
    private String sessionType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long teacherId;
    private Long groupId;
    private Boolean isFinished;

    private Long roomId;

}
