package com.school.management.mapper;

import com.school.management.dto.GroupDTO;
import com.school.management.persistance.*;
import com.school.management.shared.exception.ResourceNotFoundException;
import com.school.management.shared.mapper.MappingContext;
import org.mapstruct.*;

@Mapper(componentModel = "spring",  builder = @Builder())
public interface GroupMapper {

    @Mapping(source = "groupType.id", target = "groupTypeId")
    @Mapping(source = "level.id", target = "levelId")
    @Mapping(source = "subject.id", target = "subjectId")
    @Mapping(source = "price.id", target = "priceId")
    @Mapping(source = "teacher.id", target = "teacherId")
    @Mapping(source = "teacher.firstName", target = "teacherName")
    @Mapping(source = "groupType.name", target = "groupTypeName")  // Map group type name
    @Mapping(source = "level.name", target = "levelName")          // Map level name
    @Mapping(source = "subject.name", target = "subjectName")      // Map subject name
    @Mapping(source = "price.price", target = "priceAmount")      // Map price amount
    GroupDTO groupToGroupDTO(GroupEntity group);

    @Mapping(source = "groupTypeId", target = "groupType", qualifiedByName = "idToGroupType")
    @Mapping(source = "levelId", target = "level", qualifiedByName = "idToLevel")
    @Mapping(source = "subjectId", target = "subject", qualifiedByName = "idToSubject")
    @Mapping(source = "priceId", target = "price", qualifiedByName = "idToPricing")
    @Mapping(source = "teacherId", target = "teacher", qualifiedByName = "idToTeacher")
    GroupEntity groupDTOToGroup(GroupDTO groupDto, @Context MappingContext context);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateGroupFromDto(GroupDTO dto, @MappingTarget GroupEntity entity, @Context MappingContext context);

    @Named("idToGroupType")
    default GroupTypeEntity idToGroupType(Long id, @Context MappingContext context) {
        if (id == null) {
            return null;
        }
        return context.getGroupTypeRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GroupType", id));
    }


    @Named("idToLevel")
    default LevelEntity idToLevel(Long id, @Context MappingContext context) {
        if (id == null) return null;
        return context.getLevelRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Level", id));
    }

    @Named("idToSubject")
    default SubjectEntity idToSubject(Long id, @Context MappingContext context) {
        if (id == null) return null;
        return context.getSubjectRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", id));
    }

    @Named("idToPricing")
    default PricingEntity idToPricing(Long id, @Context MappingContext context) {
        if (id == null) return null;
        return context.getPricingRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing", id));
    }

    @Named("idToTeacher")
    default TeacherEntity idToTeacher(Long id, @Context MappingContext context) {
        if (id == null) return null;
        return context.getTeacherRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", id));
    }

}