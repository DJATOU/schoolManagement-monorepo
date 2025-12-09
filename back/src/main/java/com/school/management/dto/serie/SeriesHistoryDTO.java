package com.school.management.dto.serie;

import com.school.management.dto.session.SessionHistoryDTO;
import lombok.*;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeriesHistoryDTO {
    private Long seriesId;
    private String seriesName;
    private String paymentStatus;
    private Double totalAmountPaid;
    private Double totalCost;
    private List<SessionHistoryDTO> sessions;
}
