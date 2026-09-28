package com.school.management.mapper;

import com.school.management.dto.CatchUpResponseDTO;
import com.school.management.persistance.CatchUpRequestEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct pour {@link CatchUpRequestEntity} → {@link CatchUpResponseDTO}.
 *
 * <p><strong>Direction unique (entité → DTO)</strong> : ce mapper est dédié à la
 * production des réponses. Il aplatit les relations de l'entité vers leurs identifiants
 * ({@code student.id → studentId}, {@code originalSession.id → originalSessionId}, etc.).</p>
 *
 * <p><strong>Choix d'architecture (option b du plan)</strong> : la construction des entités
 * {@code CatchUpRequestEntity} (résolution des références student / session / group /
 * attendance) est entièrement assurée par {@code CatchUpService} (tâche 12), qui dispose
 * déjà des repositories nécessaires et applique la validation métier du cycle de vie.
 * Le mapper ne réalise donc PAS la direction DTO → entité. Cela évite d'étendre
 * {@code MappingContext} avec un {@code AttendanceRepository} (aujourd'hui absent) juste
 * pour résoudre {@code originalAttendance} : la convention {@code MappingContext} reste
 * inchangée et le service demeure la seule source de vérité pour la construction des
 * demandes de rattrapage.</p>
 */
@Mapper(componentModel = "spring")
public interface CatchUpRequestMapper {

    @Mapping(source = "student.id", target = "studentId")
    @Mapping(source = "originalSession.id", target = "originalSessionId")
    @Mapping(source = "originalGroup.id", target = "originalGroupId")
    @Mapping(source = "originalAttendance.id", target = "originalAttendanceId")
    @Mapping(source = "catchUpSession.id", target = "catchUpSessionId")
    @Mapping(source = "catchUpGroup.id", target = "catchUpGroupId")
    // Les libellés (noms) ne sont pas résolus ici : les relations sont LAZY et le mapping
    // peut avoir lieu hors transaction (controller). L'enrichissement des noms est assuré
    // par CatchUpService dans le contexte transactionnel (endpoint liste).
    @Mapping(target = "studentName", ignore = true)
    @Mapping(target = "originalSessionName", ignore = true)
    @Mapping(target = "originalGroupName", ignore = true)
    @Mapping(target = "catchUpSessionName", ignore = true)
    @Mapping(target = "catchUpGroupName", ignore = true)
    CatchUpResponseDTO toDto(CatchUpRequestEntity entity);
}
