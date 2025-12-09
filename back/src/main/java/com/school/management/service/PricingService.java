package com.school.management.service;

import com.school.management.persistance.PricingEntity;
import com.school.management.repository.PricingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PricingService {

    private final PricingRepository pricingRepository;

    @Autowired
    public PricingService(PricingRepository pricingRepository) {
        this.pricingRepository = pricingRepository;
    }

    public List<PricingEntity> getAllPricing() {
        return pricingRepository.findAll().stream().filter(PricingEntity::isActive).toList();
    }

    public PricingEntity createPricing(PricingEntity pricing) {
        return pricingRepository.save(pricing);
    }

    public PricingEntity updatePricing(Long id, PricingEntity pricing) {
        PricingEntity pricingToUpdate = pricingRepository.findById(id).orElseThrow();
        pricingToUpdate.setPrice(pricing.getPrice());
        return pricingRepository.save(pricingToUpdate);
    }


    public PricingEntity getPricingById(Long id) {
        return pricingRepository.findById(id).orElse(null);
    }
    public void disablePricings(long id) {
        PricingEntity pricing = pricingRepository.findById(id).orElseThrow();
        pricing.setActive(false);
        pricingRepository.save(pricing);
    }
}
