package com.school.management.service;

import com.school.management.config.ImageUrlService;
import com.school.management.dto.TeacherDTO;
import com.school.management.infrastructure.storage.FileManagementService;
import com.school.management.mapper.TeacherMapper;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.exception.CustomServiceException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class TeacherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TeacherService.class);

    @PersistenceContext
    private EntityManager entityManager;
    private final TeacherRepository teacherRepository;
    private final TeacherMapper teacherMapper;
    private final ImageUrlService imageUrlService;
    private final FileManagementService fileManagementService;

    @Autowired
    public TeacherService(TeacherRepository teacherRepository,
            TeacherMapper teacherMapper,
            ImageUrlService imageUrlService,
            FileManagementService fileManagementService) {
        this.teacherRepository = teacherRepository;
        this.teacherMapper = teacherMapper;
        this.imageUrlService = imageUrlService;
        this.fileManagementService = fileManagementService;
    }

    @Transactional
    public TeacherEntity save(TeacherEntity teacher) {
        return teacherRepository.save(Objects.requireNonNull(teacher));
    }

    public List<TeacherEntity> getAllTeachers() {
        LOGGER.info("Fetching all teachers...");
        return teacherRepository.findAll();
    }

    public List<TeacherEntity> findByLastName(String lastName) {
        return teacherRepository.findByLastName(lastName);
    }

    public List<TeacherEntity> findByFirstNameAndLastName(String firstName, String lastName) {
        return teacherRepository.findByFirstNameAndLastName(firstName, lastName);
    }

    public List<TeacherEntity> findByGroupsId(Long groupId) {
        return teacherRepository.findByGroups_Id(groupId);
    }

    public TeacherEntity createTeacher(TeacherEntity teacher) {
        return teacherRepository.save(Objects.requireNonNull(teacher));
    }

    public TeacherEntity updateTeacher(Long id, TeacherEntity teacher) {
        TeacherEntity teacherToUpdate = teacherRepository.findById(Objects.requireNonNull(id)).orElseThrow();
        teacherToUpdate.setFirstName(teacher.getFirstName());
        teacherToUpdate.setLastName(teacher.getLastName());
        teacherToUpdate.setEmail(teacher.getEmail());
        teacherToUpdate.setPhoneNumber(teacher.getPhoneNumber());
        teacherToUpdate.setDateOfBirth(teacher.getDateOfBirth());
        teacherToUpdate.setPlaceOfBirth(teacher.getPlaceOfBirth());
        teacherToUpdate.setGender(teacher.getGender());
        teacherToUpdate.setSpecialization(teacher.getSpecialization());
        teacherToUpdate.setQualifications(teacher.getQualifications());
        teacherToUpdate.setYearsOfExperience(teacher.getYearsOfExperience());
        teacherToUpdate.setCommunicationPreference(teacher.getCommunicationPreference());
        // Ne pas mettre à jour groups ici - géré séparément
        return teacherRepository.save(teacherToUpdate);
    }

    @Transactional(readOnly = true)
    public List<TeacherDTO> searchTeachersByNameStartingWithDTO(String name) {
        List<TeacherEntity> teacherEntities = searchTeachersByNameStartingWith(name);
        return teacherEntities.stream()
                .map(entity -> {
                    TeacherDTO dto = teacherMapper.teacherToTeacherDTO(entity);
                    // Utiliser ImageUrlService pour générer l'URL de manière centralisée
                    String photoUrl = imageUrlService.getTeacherPhotoUrl(
                            imageUrlService.extractFilename(entity.getPhoto()));
                    dto.setPhoto(photoUrl);
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherEntity> searchTeachersByNameStartingWith(String input) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TeacherEntity> cq = cb.createQuery(TeacherEntity.class);
        Root<TeacherEntity> teacher = cq.from(TeacherEntity.class);

        String[] nameParts = input.trim().toLowerCase().split("\\s+");
        Predicate finalPredicate;

        if (nameParts.length == 1) {
            String pattern = nameParts[0] + "%";
            Predicate firstNamePredicate = cb.like(cb.lower(teacher.get("firstName")), pattern);
            Predicate lastNamePredicate = cb.like(cb.lower(teacher.get("lastName")), pattern);
            finalPredicate = cb.or(firstNamePredicate, lastNamePredicate);
        } else {
            String firstNamePattern = nameParts[0] + "%";
            String lastNamePattern = nameParts[1] + "%";
            Predicate firstNamePredicate = cb.like(cb.lower(teacher.get("firstName")), firstNamePattern);
            Predicate lastNamePredicate = cb.like(cb.lower(teacher.get("lastName")), lastNamePattern);
            finalPredicate = cb.and(firstNamePredicate, lastNamePredicate);
        }

        cq.where(finalPredicate);

        TypedQuery<TeacherEntity> query = entityManager.createQuery(cq);
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<TeacherEntity> findById(Long id) {
        try {
            return teacherRepository.findById(Objects.requireNonNull(id));
        } catch (DataAccessException e) {
            throw new CustomServiceException("Error fetching teacher with ID " + id, e);
        }
    }

    public void desactivateTeacher(Long id) {
        teacherRepository.findById(Objects.requireNonNull(id)).ifPresent(teacher -> {
            teacher.setActive(false);
            teacherRepository.save(teacher);
        });
    }

    /**
     * PHASE 3A: Upload photo pour un enseignant
     * 
     * @param teacherId ID de l'enseignant
     * @param file      Fichier photo à uploader
     * @return Le nom du fichier uploadé
     * @throws IOException Si erreur d'upload
     */
    @Transactional
    public String uploadPhoto(Long teacherId, MultipartFile file) throws IOException {
        LOGGER.info("Uploading photo for teacher ID: {}", teacherId);

        TeacherEntity teacher = teacherRepository.findById(Objects.requireNonNull(teacherId))
                .orElseThrow(() -> new CustomServiceException("Teacher not found with id: " + teacherId));

        // Supprimer l'ancienne photo si elle existe
        if (teacher.getPhoto() != null && !teacher.getPhoto().isEmpty()) {
            try {
                fileManagementService.deleteFile(teacher.getPhoto());
                LOGGER.debug("Deleted old photo: {}", teacher.getPhoto());
            } catch (IOException e) {
                LOGGER.warn("Failed to delete old photo: {}", teacher.getPhoto(), e);
                // Continue malgré l'erreur - on veut quand même uploader la nouvelle photo
            }
        }

        // Upload la nouvelle photo avec rollback automatique
        FileManagementService.FileUploadResult result = fileManagementService.uploadWithRollback(file);

        if (!result.isSuccess()) {
            throw new IOException("Photo upload failed: " + result.getErrorMessage());
        }

        // Mettre à jour l'entité avec le nom du fichier
        teacher.setPhoto(result.getFilename());
        teacherRepository.save(teacher);

        LOGGER.info("Photo uploaded successfully for teacher ID {}: {}", teacherId, result.getFilename());
        return result.getFilename();
    }

    /**
     * PHASE 3A: Récupère la photo d'un enseignant
     * 
     * @param teacherId ID de l'enseignant
     * @return Resource contenant la photo
     * @throws IOException Si erreur de lecture
     */
    @Transactional(readOnly = true)
    public Resource getPhoto(Long teacherId) throws IOException {
        LOGGER.debug("Fetching photo for teacher ID: {}", teacherId);

        TeacherEntity teacher = teacherRepository.findById(Objects.requireNonNull(teacherId))
                .orElseThrow(() -> new CustomServiceException("Teacher not found with id: " + teacherId));

        if (teacher.getPhoto() == null || teacher.getPhoto().isEmpty()) {
            throw new CustomServiceException("Teacher " + teacherId + " has no photo");
        }

        return fileManagementService.getFile(teacher.getPhoto());
    }
}
