package com.school.management.dto.group;

import com.school.management.dto.serie.SeriesHistoryDTO;
import lombok.*;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupHistoryDTO {
    private Long groupId;
    private String groupName;
    private List<SeriesHistoryDTO> series;
    private boolean isCatchUp;
}
