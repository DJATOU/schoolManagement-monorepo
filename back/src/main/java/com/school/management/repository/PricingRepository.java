package com.school.management.repository;

import com.school.management.persistance.PricingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PricingRepository extends JpaRepository<PricingEntity, Long> {

    // Tarifs d'un montant donné, par identifiant croissant. Le type suit la colonne (Double) :
    // un paramètre BigDecimal sur un attribut Double ne se lie pas, et la méthode n'aurait
    // échoué qu'au premier appel. Aucune unicité ne porte sur le montant ; l'ordre stable rend
    // le choix du premier tarif reproductible d'un import à l'autre.
    List<PricingEntity> findByPriceOrderByIdAsc(Double price);

    // Find prices within a certain range
    List<PricingEntity> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

}
