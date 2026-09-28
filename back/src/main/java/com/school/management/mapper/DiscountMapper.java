package com.school.management.mapper;

import com.school.management.dto.DiscountResponseDTO;
import com.school.management.persistance.DiscountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct pour {@link DiscountEntity} → {@link DiscountResponseDTO}.
 *
 * <p><strong>Direction unique (entité → DTO)</strong> : ce mapper produit les réponses en
 * aplatissant la relation étudiant ({@code student.id → studentId}). La construction des
 * entités {@code DiscountEntity} à partir de {@code DiscountRequestDTO} est assurée par
 * {@code DiscountService.create} (tâche 5), qui applique la validation métier (portée
 * unique, taux dans [0.00, 1.00], absence de conflit). La direction DTO → entité n'est
 * donc pas nécessaire ici, ce qui évite d'étendre {@code MappingContext}.</p>
 */
@Mapper(componentModel = "spring")
public interface DiscountMapper {

    /**
     * Mappe l'entité vers le DTO de réponse. Les libellés d'affichage
     * ({@code studentName} / {@code targetName}) ne sont pas mappés ici : ils sont résolus
     * dans la transaction par {@code DiscountViewService}, qui interroge les dépôts.
     */
    @Mapping(source = "student.id", target = "studentId")
    @Mapping(target = "studentName", ignore = true)
    @Mapping(target = "targetName", ignore = true)
    DiscountResponseDTO toDto(DiscountEntity entity);
}
