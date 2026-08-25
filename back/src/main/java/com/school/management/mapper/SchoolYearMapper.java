package com.school.management.mapper;

import com.school.management.dto.SchoolYearDTO;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.shared.mapper.MappingContext;
import org.mapstruct.*;

/**
 * Mapper MapStruct pour convertir entre {@link SchoolYearEntity} et {@link SchoolYearDTO}.
 *
 * <p>Conforme à la convention du projet : la résolution des relations lors du
 * mapping DTO → Entity passe par {@link MappingContext} (et non par
 * {@code ApplicationContextProvider}). L'entité {@code SchoolYearEntity} ne
 * possède pas de relation à résoudre ; le contexte est conservé dans la
 * signature pour rester homogène avec les autres mappers (GroupMapper,
 * StudentMapper).
 */
@Mapper(componentModel = "spring")
public interface SchoolYearMapper {

    /**
     * Convertit une {@link SchoolYearEntity} vers un {@link SchoolYearDTO} (lecture).
     * Aucun contexte nécessaire pour cette direction.
     *
     * @param schoolYear l'entité source
     * @return le DTO de sortie
     */
    SchoolYearDTO schoolYearToSchoolYearDTO(SchoolYearEntity schoolYear);

    /**
     * Convertit un {@link SchoolYearDTO} vers une {@link SchoolYearEntity} (écriture).
     * Le {@link MappingContext} est fourni conformément à la convention de mapping.
     *
     * @param dto     le DTO source
     * @param context contexte de mapping (repositories)
     * @return l'entité hydratée
     */
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateUpdate", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "description", ignore = true)
    SchoolYearEntity schoolYearDTOToSchoolYear(SchoolYearDTO dto, @Context MappingContext context);

    /**
     * Met à jour une entité existante avec les données du DTO.
     * Ignore l'identifiant et les champs techniques.
     *
     * @param dto     le DTO source avec les nouvelles valeurs
     * @param entity  l'entité cible à mettre à jour
     * @param context contexte de mapping (repositories)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateUpdate", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "description", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateSchoolYearFromDTO(SchoolYearDTO dto, @MappingTarget SchoolYearEntity entity, @Context MappingContext context);
}
