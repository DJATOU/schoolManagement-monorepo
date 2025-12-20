package com.school.management.mapper;

import com.school.management.dto.StudentDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.TutorEntity;
import com.school.management.shared.exception.ResourceNotFoundException;
import com.school.management.shared.mapper.MappingContext;
import org.mapstruct.*;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper MapStruct pour convertir entre StudentEntity et StudentDTO.
 *
 * REFACTORÉ Phase 1 : Utilise maintenant MappingContext au lieu de
 * ApplicationContextProvider
 * pour résoudre les dépendances (Level, Tutor).
 *
 * @author Claude Code
 * @since Phase 1 Refactoring
 */
@Mapper(componentModel = "spring")
public interface StudentMapper {

    /**
     * Convertit StudentEntity vers StudentDTO (lecture).
     * Pas besoin de contexte pour cette direction.
     *
     * @param student l'entité source
     * @return le DTO de sortie
     */
    @Mapping(source = "tutor.id", target = "tutorId")
    @Mapping(source = "groups", target = "groupIds", qualifiedByName = "groupSetToIdSet")
    @Mapping(source = "level.id", target = "levelId")
    StudentDTO studentToStudentDTO(StudentEntity student);

    /**
     * Convertit StudentDTO vers StudentEntity (écriture).
     * NÉCESSITE MappingContext pour résoudre les relations.
     *
     * @param studentDto le DTO source
     * @param context    contexte contenant les repositories nécessaires
     * @return l'entité hydratée
     */
    @Mapping(source = "tutorId", target = "tutor", qualifiedByName = "idToTutor")
    @Mapping(target = "groups", ignore = true)
    @Mapping(target = "attendances", ignore = true)
    @Mapping(source = "levelId", target = "level", qualifiedByName = "loadLevelEntity")
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateUpdate", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "communicationPreference", ignore = true)
    StudentEntity studentDTOToStudent(StudentDTO studentDto, @Context MappingContext context);

    /**
     * Met à jour une entité existante avec les données du DTO.
     * Ignore les champs techniques (id, active).
     *
     * @param dto     le DTO source avec les nouvelles valeurs
     * @param entity  l'entité cible à mettre à jour
     * @param context contexte pour résoudre les relations
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(source = "tutorId", target = "tutor", qualifiedByName = "idToTutor")
    @Mapping(target = "groups", ignore = true)
    @Mapping(target = "attendances", ignore = true)
    @Mapping(source = "levelId", target = "level", qualifiedByName = "loadLevelEntity")
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateUpdate", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "communicationPreference", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateStudentFromDTO(StudentDTO dto, @MappingTarget StudentEntity entity, @Context MappingContext context);

    /**
     * Convertit un Set de GroupEntity en Set d'IDs.
     * Utilisé pour Entity → DTO.
     */
    @Named("groupSetToIdSet")
    default Set<Long> groupSetToIdSet(Set<GroupEntity> groups) {
        if (groups == null) {
            return Collections.emptySet();
        }
        return groups.stream()
                .map(GroupEntity::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Résout un TutorEntity depuis son ID en utilisant le MappingContext.
     * Remplace l'ancien accès via ApplicationContextProvider.
     *
     * @param id      l'ID du tuteur
     * @param context contexte contenant TutorRepository
     * @return l'entité TutorEntity ou null si id est null
     * @throws ResourceNotFoundException si le tuteur n'existe pas
     */
    @Named("idToTutor")
    default TutorEntity idToTutor(Long id, @Context MappingContext context) {
        if (id == null) {
            return null;
        }

        return context.getTutorRepository()
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tutor", id));
    }

    /**
     * Résout un LevelEntity depuis son ID en utilisant le MappingContext.
     * Remplace l'ancien accès via ApplicationContextProvider.
     *
     * @param id      l'ID du niveau
     * @param context contexte contenant LevelRepository
     * @return l'entité LevelEntity ou null si id est null
     * @throws ResourceNotFoundException si le niveau n'existe pas
     */
    @Named("loadLevelEntity")
    default LevelEntity loadLevelEntity(Long id, @Context MappingContext context) {
        if (id == null) {
            return null;
        }

        return context.getLevelRepository()
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Level", id));
    }
}