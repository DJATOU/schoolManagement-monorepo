package com.school.management.persistance;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@SuperBuilder
public abstract class BaseEntity {

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_update")
    private LocalDateTime  dateUpdate;

    // Renseigné automatiquement avec l'identifiant de l'utilisateur courant (audit JPA)
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    // Renseigné automatiquement à chaque modification avec l'utilisateur courant
    @LastModifiedBy
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
        // createdBy est désormais renseigné par l'audit JPA (SecurityAuditorAware),
        // et non plus codé en dur.
    }

    @PreUpdate
    protected void onUpdate() {
        dateUpdate = LocalDateTime.now();
    }

    public boolean isActive() {
        return active;
    }
}
