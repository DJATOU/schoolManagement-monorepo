package com.school.management.mapper;

import com.school.management.dto.PricingDto;
import com.school.management.persistance.PricingEntity;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface PricingMapper {
    PricingEntity toEntity(PricingDto pricingDto);

    PricingDto toDto(PricingEntity pricingEntity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    PricingEntity partialUpdate(PricingDto pricingDto, @MappingTarget PricingEntity pricingEntity);
}