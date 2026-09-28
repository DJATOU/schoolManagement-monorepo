package com.school.management.repository;

import com.school.management.persistance.DiscountEntity;
import com.school.management.persistance.DiscountScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Accès aux données des réductions (discounts).
 *
 * <p>Fournit les recherches nécessaires à la résolution de la réduction applicable
 * pour un étudiant. La logique de sélection de la portée la plus spécifique
 * (Session &gt; Series &gt; Group) est assurée par la couche service.</p>
 */
@Repository
public interface DiscountRepository extends JpaRepository<DiscountEntity, Long> {

    // Toutes les réductions d'un étudiant (le service choisit la portée applicable)
    List<DiscountEntity> findByStudentId(Long studentId);

    // Réductions au niveau groupe pour un étudiant et un groupe donnés
    List<DiscountEntity> findByStudentIdAndScopeAndGroupId(Long studentId, DiscountScope scope, Long groupId);

    // Réductions au niveau série pour un étudiant et une série donnés
    List<DiscountEntity> findByStudentIdAndScopeAndSeriesId(Long studentId, DiscountScope scope, Long seriesId);

    // Réductions au niveau séance pour un étudiant et une séance donnés
    List<DiscountEntity> findByStudentIdAndScopeAndSessionId(Long studentId, DiscountScope scope, Long sessionId);
}
