package com.school.management.mapper;

import com.school.management.dto.RefundResponseDTO;
import com.school.management.persistance.RefundEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct pour {@link RefundEntity} → {@link RefundResponseDTO}.
 *
 * <p><strong>Direction unique (entité → DTO)</strong> : ce mapper produit les réponses en
 * aplatissant les relations paiement et étudiant ({@code payment.id → paymentId},
 * {@code student.id → studentId}). La construction des entités {@code RefundEntity} à
 * partir de {@code RefundRequestDTO} est assurée par {@code RefundService.create}
 * (tâche 11), qui applique la validation métier (montant ≤ montant versé du paiement).
 * La direction DTO → entité n'est donc pas nécessaire ici.</p>
 */
@Mapper(componentModel = "spring")
public interface RefundMapper {

    @Mapping(source = "payment.id", target = "paymentId")
    @Mapping(source = "student.id", target = "studentId")
    RefundResponseDTO toDto(RefundEntity entity);
}
