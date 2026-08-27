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

    /**
     * Réponse sans plafond restant, pour les lectures où il n'a pas de sens (historique, listes).
     *
     * <p>{@code refundableCap} est laissé nul plutôt que rempli à zéro : un plafond à zéro signifie
     * « plus rien à rembourser », affirmation que ce mapper n'est pas en mesure de faire puisqu'il
     * ne connaît pas les autres remboursements du paiement.</p>
     */
    @Mapping(source = "payment.id", target = "paymentId")
    @Mapping(source = "student.id", target = "studentId")
    @Mapping(target = "refundableCap", ignore = true)
    RefundResponseDTO toDto(RefundEntity entity);

    /**
     * Réponse de création, portant le plafond restant après enregistrement (exigence 7.2), afin que
     * l'interface puisse actualiser ce qu'elle affiche sans un second appel.
     */
    default RefundResponseDTO toDto(RefundEntity entity, java.math.BigDecimal refundableCap) {
        RefundResponseDTO base = toDto(entity);
        return new RefundResponseDTO(base.id(), base.paymentId(), base.studentId(), base.amount(),
                base.refundDate(), base.refundNumber(), base.reason(), refundableCap);
    }
}
