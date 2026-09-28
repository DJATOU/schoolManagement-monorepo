package com.school.management.service;

import com.school.management.persistance.SubjectEntity;
import com.school.management.repository.SubjectRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;

    @Autowired
    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    // return all subjects which are active
    public List<SubjectEntity> getAllSubjects() {
        return subjectRepository.findAll().stream().filter(SubjectEntity::isActive).toList();
    }

    /**
     * Récupère une matière par son identifiant (nécessaire au pré-remplissage du
     * formulaire de modification).
     *
     * @param id identifiant de la matière
     * @return la matière
     * @throws CustomServiceException (404) si la matière est introuvable
     */
    public SubjectEntity getSubjectById(Long id) {
        return subjectRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new CustomServiceException(
                        "Matière introuvable pour l'identifiant : " + id, HttpStatus.NOT_FOUND));
    }

    // create a new subject
    public SubjectEntity createSubject(SubjectEntity subject) {
        return subjectRepository.save(Objects.requireNonNull(subject));
    }

    public SubjectEntity updateSubject(Long id, SubjectEntity subject) {
        SubjectEntity subjectToUpdate = getSubjectById(id);
        subjectToUpdate.setName(subject.getName());
        // La description était ignorée : elle est désormais bien enregistrée.
        subjectToUpdate.setDescription(subject.getDescription());
        return subjectRepository.save(subjectToUpdate);
    }

    public void disableSubjects(long subjectId) {
        SubjectEntity subject = subjectRepository.findById(subjectId).orElseThrow();
        subject.setActive(false);
        subjectRepository.save(subject);
    }
}
