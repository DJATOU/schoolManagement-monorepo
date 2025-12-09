package com.school.management.service.student;

import com.school.management.config.ImageUrlService;
import com.school.management.dto.StudentDTO;
import com.school.management.mapper.StudentMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.TutorRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.shared.mapper.MappingContext;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Service métier pour la gestion des étudiants.
 *
 * REFACTORÉ Phase 1 : Utilise maintenant MappingContext au lieu de ApplicationContextProvider
 * pour résoudre les dépendances lors du mapping DTO → Entity.
 *
 * @author Claude Code
 * @since Phase 1 Refactoring
 */
@Service
public class StudentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudentService.class);
    private static final String LASTNAME = "lastName";
    private static final String FIRSTNAME = "firstName";

    @PersistenceContext
    private EntityManager entityManager;

    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;
    private final StudentSearchService studentSearchService;
    private final ImageUrlService imageUrlService;

    // Repositories nécessaires pour le MappingContext
    private final LevelRepository levelRepository;
    private final TutorRepository tutorRepository;

    // MappingContext réutilisable (créé une seule fois)
    private MappingContext mappingContext;

    @Autowired
    public StudentService(StudentRepository studentRepository,
                         StudentMapper studentMapper,
                         StudentSearchService studentSearchService,
                         ImageUrlService imageUrlService,
                         LevelRepository levelRepository,
                         TutorRepository tutorRepository) {
        this.studentMapper = studentMapper;
        this.studentRepository = studentRepository;
        this.studentSearchService = studentSearchService;
        this.imageUrlService = imageUrlService;
        this.levelRepository = levelRepository;
        this.tutorRepository = tutorRepository;
    }

    /**
     * Initialise le MappingContext après l'injection des dépendances.
     * Permet de le réutiliser dans toutes les méthodes de mapping.
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.forStudent(levelRepository, tutorRepository);
        LOGGER.debug("MappingContext initialized for StudentService");
    }

    /**
     * Retourne le MappingContext pour utilisation dans les controllers si nécessaire.
     * @return le contexte de mapping configuré
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    @Transactional(readOnly = true)
    public Optional<StudentEntity> findById(Long id) {
        try {
            return studentRepository.findById(id);
        } catch (DataAccessException e) {
            throw new CustomServiceException("Error fetching student with ID " + id, e);
        }
    }

    @Transactional(readOnly = true)
    public List<StudentEntity> findAll() {
        LOGGER.info("Fetching all students....");
        return studentRepository.findAll();
    }

    @Transactional
    public StudentEntity save(StudentEntity student) {
        return studentRepository.save(student);
    }

    @Transactional
    public List<StudentEntity> searchStudents(String firstName, String lastName, Long level, Long groupId, String establishment) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<StudentEntity> cq = cb.createQuery(StudentEntity.class);
        Root<StudentEntity> student = cq.from(StudentEntity.class);

        Predicate[] predicates = Stream.of(
                        buildPredicate(firstName, name -> cb.equal(cb.lower(student.get(FIRSTNAME)), name.toLowerCase())),
                        buildPredicate(lastName, name -> cb.equal(cb.lower(student.get(LASTNAME)), name.toLowerCase())),
                        buildPredicate(level, lev -> cb.equal(student.get("level"), lev)),
                        buildPredicate(groupId, id -> {
                            Join<StudentEntity, GroupEntity> groupsJoin = student.join("groups");
                            return cb.equal(groupsJoin.get("id"), id);
                        }),
                        buildPredicate(establishment, est -> cb.equal(student.get("establishment"), est))
                )
                .filter(Objects::nonNull)
                .toArray(Predicate[]::new);

        cq.where(cb.and(predicates));
        return entityManager.createQuery(cq).getResultList();
    }

    @Transactional(readOnly = true)
    public List<StudentDTO> searchStudentsByNameStartingWithDTO(String name) {
        List<StudentEntity> studentEntities = studentSearchService.searchStudentsByNameStartingWith(name);
        return studentEntities.stream()
                .map(entity -> {
                    StudentDTO dto = studentMapper.studentToStudentDTO(entity);
                    // Utiliser ImageUrlService pour générer l'URL de manière centralisée
                    String photoUrl = imageUrlService.getStudentPhotoUrl(
                            imageUrlService.extractFilename(entity.getPhoto())
                    );
                    dto.setPhoto(photoUrl);
                    return dto;
                })
                .toList();
    }

    private <T> Predicate buildPredicate(T value, Function<T, Predicate> predicateFunction) {
        return (value != null) ? predicateFunction.apply(value) : null;
    }

    @Transactional(readOnly = true)
    public List<StudentEntity> findByLastName(String lastName) {
        return studentRepository.findByLastName(lastName);
    }

    @Transactional(readOnly = true)
    public List<StudentEntity> findByFirstNameAndLastName(String firstName, String lastName) {
        return studentRepository.findByFirstNameAndLastName(firstName, lastName);
    }

    @Transactional(readOnly = true)
    public List<StudentEntity> findByGroupsId(Long groupId) {
        return studentRepository.findByGroups_Id(groupId);
    }

    @Transactional(readOnly = true)
    public List<StudentEntity> findByLevel(Long level) {
        return studentRepository.findByLevelId(level);
    }

    @Transactional(readOnly = true)
    public List<StudentEntity> findByEstablishment(String establishment) {
        return studentRepository.findByEstablishment(establishment);
    }

    public List<StudentEntity> findAllActiveStudents() {
        return studentRepository.findAllByActiveTrue();
    }

    @Transactional
    public void desactivateStudent(Long id) {
        StudentEntity student = studentRepository.findById(id)
                .orElseThrow(() -> new CustomServiceException("Student not found with id " + id));
        student.setActive(false);
        studentRepository.save(student);
    }
}