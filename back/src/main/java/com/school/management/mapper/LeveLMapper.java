package com.school.management.mapper;

import com.school.management.dto.LevelDto;
import com.school.management.persistance.LevelEntity;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface LeveLMapper {
    LevelEntity toEntity(LevelDto levelDto);

    LevelDto toDto(LevelEntity levelEntity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    LevelEntity partialUpdate(LevelDto levelDto, @MappingTarget LevelEntity levelEntity);
}