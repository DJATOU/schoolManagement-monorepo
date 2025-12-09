package com.school.management.repository;

import com.school.management.persistance.PricingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PricingRepository extends JpaRepository<PricingEntity, Long> {

    // Find prices based on a specific value
    List<PricingEntity> findByPrice(BigDecimal price);

    // Find prices within a certain range
    List<PricingEntity> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

}
