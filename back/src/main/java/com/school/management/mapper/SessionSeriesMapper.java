package com.school.management.mapper;

import com.school.management.dto.SessionSeriesDto;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.shared.exception.ResourceNotFoundException;
import com.school.management.shared.mapper.MappingContext;
import org.mapstruct.*;

// SessionSeriesMapper.java

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface SessionSeriesMapper {

    @Mapping(source = "groupId", target = "group", qualifiedByName = "idToGroup")
    SessionSeriesEntity toEntity(SessionSeriesDto sessionSeriesDto, @Context MappingContext context);

    @Mapping(source = "group.id", target = "groupId")
    @Mapping(target = "numberOfSessionsCreated", expression = "java(getNumberOfSessionsCreated(sessionSeriesEntity))")
    SessionSeriesDto toDto(SessionSeriesEntity sessionSeriesEntity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    SessionSeriesEntity partialUpdate(SessionSeriesDto sessionSeriesDto, @MappingTarget SessionSeriesEntity sessionSeriesEntity, @Context MappingContext context);

    @Named("idToGroup")
    default GroupEntity idToGroup(Long id, @Context MappingContext context) {
        System.out.println("Mapping groupId: " + id);
        if (id == null) return null;
        return context.getGroupRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
    }

    // Ajouter cette méthode par défaut pour calculer numberOfSessionsCreated
    default int getNumberOfSessionsCreated(SessionSeriesEntity sessionSeriesEntity) {
        if (sessionSeriesEntity.getSessions() != null) {
            return sessionSeriesEntity.getSessions().size();
        } else {
            return 0;
        }
    }
}
