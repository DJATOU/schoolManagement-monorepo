package com.school.management.domain.model.group;

import lombok.*;

@Getter
@Setter(AccessLevel.PRIVATE) // Keeping setters private to control state changes through methods
@NoArgsConstructor(access = AccessLevel.PROTECTED) // For frameworks
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Used by the Builder
@Builder
public class Group {

    private Long id;
    private String groupType;
    private Long levelId;
    private Long subjectId;
    private Integer packageCount;
    private Integer sessionCount;
    private Double sessionPrice;
    private Long teacherId;

}
