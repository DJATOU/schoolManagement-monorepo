package com.school.management.persistance;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@SuperBuilder
public abstract class BaseEntity {

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_update")
    private LocalDateTime  dateUpdate;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "description")
    private String description;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        active = true;
        // createdBy should be set based on the current user context
        createdBy = "admin";
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdate = LocalDateTime.now();
    }

    public boolean isActive() {
        return active;
    }
}