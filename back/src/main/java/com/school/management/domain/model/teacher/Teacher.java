package com.school.management.domain.model.teacher;

import com.school.management.domain.model.group.Group;
import lombok.*;

import java.util.Set;

@Getter
@Setter(AccessLevel.PRIVATE) // Keeping setters private to control state changes through methods
@NoArgsConstructor(access = AccessLevel.PROTECTED) // For frameworks
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Used by the Builder
@Builder
public class Teacher {

    private Long id;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;
    private Set<Group> assignedGroups;

}
