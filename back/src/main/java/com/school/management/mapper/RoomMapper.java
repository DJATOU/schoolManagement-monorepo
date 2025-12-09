package com.school.management.mapper;

import com.school.management.dto.RoomDto;
import com.school.management.persistance.RoomEntity;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface RoomMapper {
    RoomEntity toEntity(RoomDto roomDto);

    RoomDto toDto(RoomEntity roomEntity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    RoomEntity partialUpdate(RoomDto roomDto, @MappingTarget RoomEntity roomEntity);
}