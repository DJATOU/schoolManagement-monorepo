package com.school.management.dto;

import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.school.management.persistance.PricingEntity}
 */
@Value
public class PricingDto implements Serializable {
    LocalDateTime dateCreation;
    LocalDateTime dateUpdate;
    String createdBy;
    String updatedBy;
    Boolean active;
    String description;
    Long id;
    BigDecimal price;
    LocalDateTime effectiveDate;
    LocalDateTime expirationDate;
}