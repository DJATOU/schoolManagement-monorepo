package com.school.management.repository;

import com.school.management.persistance.CorrectionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Accès aux traces de correction. Aucune méthode de modification n'est ajoutée : une trace est
 * écrite une fois, puis seulement lue. Les lectures du Journal arrivent avec le lot D.
 */
@Repository
public interface CorrectionAuditRepository extends JpaRepository<CorrectionAuditEntity, Long> {
}
