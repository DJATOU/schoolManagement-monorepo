package com.school.management.mapper;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.TeacherDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.TeacherEntity;
import java.util.Collections;
import org.mapstruct.Mapper;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {GroupMapper.class})
public interface TeacherMapper {


    TeacherDTO teacherToTeacherDTO(TeacherEntity teacher);

    TeacherEntity teacherDTOToTeacher(TeacherDTO teacherDto);


    GroupDTO groupEntityToGroupDTO(GroupEntity groupEntity);
    GroupEntity groupDTOToGroupEntity(GroupDTO groupDto);


    default Set<GroupDTO> groupEntitiesToGroupDTOs(Set<GroupEntity> groups) {
        if (groups == null) {
            return Collections.emptySet();
        }
        return groups.stream()
                .map(this::groupEntityToGroupDTO)
                .collect(Collectors.toSet());
    }

    // Use this method to map the set of GroupDTO to a set of GroupEntity
    default Set<GroupEntity> groupDTOsToGroupEntities(Set<GroupDTO> groupDTOs) {
        if (groupDTOs == null) {
            return Collections.emptySet();
        }
        return groupDTOs.stream()
                .map(this::groupDTOToGroupEntity)
                .collect(Collectors.toSet());
    }


    // Define other mappings if needed
}
