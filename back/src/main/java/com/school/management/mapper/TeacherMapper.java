package com.school.management.mapper;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.TeacherDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.TeacherEntity;
import java.util.Collections;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = { GroupMapper.class })
public interface TeacherMapper {

    TeacherDTO teacherToTeacherDTO(TeacherEntity teacher);

    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateUpdate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "description", ignore = true)
    TeacherEntity teacherDTOToTeacher(TeacherDTO teacherDto);

    @Mapping(target = "groupTypeId", ignore = true)
    @Mapping(target = "groupTypeName", ignore = true)
    @Mapping(target = "levelId", ignore = true)
    @Mapping(target = "levelName", ignore = true)
    @Mapping(target = "subjectId", ignore = true)
    @Mapping(target = "subjectName", ignore = true)
    @Mapping(target = "priceId", ignore = true)
    @Mapping(target = "priceAmount", ignore = true)
    @Mapping(target = "teacherId", ignore = true)
    @Mapping(target = "teacherName", ignore = true)
    @Mapping(target = "studentIds", ignore = true)
    @Mapping(target = "isCatchUp", ignore = true)
    GroupDTO groupEntityToGroupDTO(GroupEntity groupEntity);

    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "groupType", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "teacher", ignore = true)
    @Mapping(target = "students", ignore = true)
    @Mapping(target = "series", ignore = true)
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
