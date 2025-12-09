package com.school.management.mapper;

import com.school.management.dto.TutorDTO;
import com.school.management.persistance.TutorEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TutorMapper {

    TutorDTO tutorToTutorDTO(TutorEntity tutor);

    TutorEntity tutorDTOToTutor(TutorDTO tutorDto);
}
