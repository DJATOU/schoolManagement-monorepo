package com.school.management.repository;

import com.school.management.persistance.CorrectionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Accès aux traces de correction. Aucune méthode de modification n'est ajoutée : une trace est
 * écrite une fois, puis seulement lue.
 */
@Repository
public interface CorrectionAuditRepository extends JpaRepository<CorrectionAuditEntity, Long> {

    /**
     * Traces d'un élève, pour son Journal (exigence 12.1). Une Trace de séance (dévalidation) n'a pas
     * d'élève : chaque ligne retirée a la sienne.
     */
    List<CorrectionAuditEntity> findByStudentId(Long studentId);
}
