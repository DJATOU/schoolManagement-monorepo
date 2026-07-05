package com.school.management.mapper;

import com.school.management.dto.TutorDTO;
import com.school.management.persistance.TutorEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TutorMapper {

    TutorDTO tutorToTutorDTO(TutorEntity tutor);

    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "communicationPreference", ignore = true)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "occupation", ignore = true)
    @Mapping(target = "students", ignore = true)
    TutorEntity tutorDTOToTutor(TutorDTO tutorDto);
}
