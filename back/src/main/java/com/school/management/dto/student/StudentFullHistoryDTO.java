package com.school.management.dto.student;

import com.school.management.dto.group.GroupHistoryDTO;
import lombok.*;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentFullHistoryDTO {
    private Long studentId;
    private String studentName;
    private List<GroupHistoryDTO> groups;
    private boolean isCatchUp;
}
