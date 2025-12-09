package com.school.management.controller;

import com.school.management.persistance.PricingEntity;
import com.school.management.service.PricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricings")
public class PricingController {

    private final PricingService pricingService;

    @Autowired
    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping
    public ResponseEntity<List<PricingEntity>> getAllPricings() {
        return ResponseEntity.ok(pricingService.getAllPricing());
    }

    @PostMapping
    public ResponseEntity<PricingEntity> createPricing(@RequestBody PricingEntity pricing) {
        return ResponseEntity.ok(pricingService.createPricing(pricing));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PricingEntity> updatePricing(@PathVariable Long id, @RequestBody PricingEntity pricing) {
        return ResponseEntity.ok(pricingService.updatePricing(id, pricing));
    }

    //disable pricings
    @DeleteMapping("disable/{id_list}")
    public ResponseEntity<Boolean> disablePricings(@PathVariable String id_list) {
        System.out.println("Request recieved: " + id_list);
        for (String id : id_list.split(",")) {
            pricingService.disablePricings(Long.parseLong(id));
        }
        return ResponseEntity.ok(true);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PricingEntity> getPricingById(@PathVariable Long id) {
        PricingEntity pricing = pricingService.getPricingById(id);
        if (pricing != null) {
            return ResponseEntity.ok(pricing);
        } else {
            return ResponseEntity.notFound().build();
        }
    }


}
