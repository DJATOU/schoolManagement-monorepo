package com.school.management.service.student;

import com.school.management.config.ImageUrlService;
import com.school.management.dto.StudentDTO;
import com.school.management.mapper.StudentMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
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
import org.springframework.http.HttpStatus;
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
 * REFACTORÉ Phase 1 : Utilise maintenant MappingContext au lieu de
 * ApplicationContextProvider
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

    // Résolution des étudiants par année scolaire (historique figé vs année courante).
    private final com.school.management.repository.StudentGroupRepository studentGroupRepository;
    private final com.school.management.service.CurrentSchoolYearService currentSchoolYearService;

    // MappingContext réutilisable (créé une seule fois)
    private MappingContext mappingContext;

    @Autowired
    public StudentService(StudentRepository studentRepository,
            StudentMapper studentMapper,
            StudentSearchService studentSearchService,
            ImageUrlService imageUrlService,
            LevelRepository levelRepository,
            TutorRepository tutorRepository,
            com.school.management.repository.StudentGroupRepository studentGroupRepository,
            com.school.management.service.CurrentSchoolYearService currentSchoolYearService) {
        this.studentMapper = studentMapper;
        this.studentRepository = studentRepository;
        this.studentSearchService = studentSearchService;
        this.imageUrlService = imageUrlService;
        this.levelRepository = levelRepository;
        this.tutorRepository = tutorRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.currentSchoolYearService = currentSchoolYearService;
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
     * Retourne le MappingContext pour utilisation dans les controllers si
     * nécessaire.
     * 
     * @return le contexte de mapping configuré
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    @Transactional(readOnly = true)
    public Optional<StudentEntity> findById(Long id) {
        try {
            return studentRepository.findById(Objects.requireNonNull(id));
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
        return studentRepository.save(Objects.requireNonNull(student));
    }

    /**
     * Rattache un tuteur à un étudiant, ou le détache lorsque {@code tutorId} est nul.
     *
     * <p>Point d'entrée dédié car la mise à jour générale de l'étudiant
     * ({@code updateStudentFromDTO}) applique la stratégie
     * {@code NullValuePropertyMappingStrategy.IGNORE} : une valeur nulle y est ignorée, ce
     * qui rend le <strong>détachement impossible</strong> par ce chemin. Un point d'entrée
     * explicite lève l'ambiguïté entre « champ non fourni » et « champ vidé ».</p>
     *
     * <p>Le détachement ne supprime jamais la fiche du tuteur : celle-ci reste réutilisable
     * pour d'autres étudiants (frères et sœurs).</p>
     *
     * @param studentId identifiant de l'étudiant
     * @param tutorId   identifiant du tuteur à rattacher, ou {@code null} pour détacher
     * @return l'étudiant mis à jour
     * @throws CustomServiceException (404) si l'étudiant ou le tuteur est introuvable
     */
    @Transactional
    public StudentEntity setTutor(Long studentId, Long tutorId) {
        StudentEntity student = findById(Objects.requireNonNull(studentId))
                .orElseThrow(() -> new CustomServiceException(
                        "Étudiant introuvable pour l'identifiant : " + studentId,
                        HttpStatus.NOT_FOUND));

        if (tutorId == null) {
            student.setTutor(null);
        } else {
            student.setTutor(tutorRepository.findById(tutorId)
                    .orElseThrow(() -> new CustomServiceException(
                            "Tuteur introuvable pour l'identifiant : " + tutorId,
                            HttpStatus.NOT_FOUND)));
        }

        return studentRepository.save(student);
    }

    @Transactional
    public List<StudentEntity> searchStudents(String firstName, String lastName, Long level, Long groupId,
            String establishment) {
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
                buildPredicate(establishment, est -> cb.equal(student.get("establishment"), est)))
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
                            imageUrlService.extractFilename(entity.getPhoto()));
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

    /**
     * Liste les étudiants pour l'année scolaire courante en filtrant sur leur
     * statut d'inscription ({@link StudentStatus}).
     *
     * <p>Par défaut ({@code includeInactive = false}), seuls les étudiants dont le
     * statut est {@link StudentStatus#ACTIVE} sont retournés : les étudiants partis
     * ({@link StudentStatus#INACTIVE}) sont exclus des listes courantes
     * (exigence 7.3). Lorsque {@code includeInactive = true}, la liste inclut
     * également les étudiants inactifs (exigence 7.4).</p>
     *
     * @param includeInactive {@code true} pour inclure les étudiants inactifs,
     *                        {@code false} pour ne retourner que les actifs
     * @return la liste des étudiants correspondant au filtre de statut
     */
    @Transactional(readOnly = true)
    public List<StudentEntity> findStudentsByStatus(boolean includeInactive) {
        if (includeInactive) {
            // Exigence 7.4 : inclure explicitement les étudiants inactifs
            return studentRepository.findAll();
        }
        // Exigence 7.3 : par défaut, exclure les étudiants INACTIVE
        return studentRepository.findByStatus(StudentStatus.ACTIVE);
    }

    /**
     * Liste les étudiants pour une année scolaire donnée.
     *
     * <p>Deux sémantiques distinctes selon l'année, cohérentes avec le modèle (l'année vit sur
     * le groupe) :</p>
     * <ul>
     *   <li><strong>Année courante</strong> (ou {@code schoolYearId} nul) : renvoie les étudiants
     *       selon leur statut global (ACTIVE par défaut, inactifs inclus si demandé). Utile juste
     *       après un passage d'année pour voir qui reste à réinscrire.</li>
     *   <li><strong>Année passée</strong> : renvoie les étudiants <em>inscrits</em> dans les
     *       groupes de cette année (historique figé, consultation seule).</li>
     * </ul>
     *
     * @param schoolYearId   année scolaire sélectionnée (nul = comportement par statut)
     * @param includeInactive inclure les inactifs (pertinent pour l'année courante)
     * @return la liste d'étudiants correspondant à l'année et au filtre de statut
     */
    @Transactional(readOnly = true)
    public List<StudentEntity> findStudentsBySchoolYear(Long schoolYearId, boolean includeInactive) {
        if (schoolYearId == null) {
            return findStudentsByStatus(includeInactive);
        }

        // Si l'année demandée est l'année courante, on liste par statut global.
        boolean isCurrent = currentSchoolYearService.findCurrent()
                .map(current -> schoolYearId.equals(current.getId()))
                .orElse(false);
        if (isCurrent) {
            return findStudentsByStatus(includeInactive);
        }

        // Année passée : historique figé = étudiants inscrits dans les groupes de cette année.
        return studentGroupRepository.findDistinctStudentsBySchoolYearId(schoolYearId);
    }

    /**
     * Réactive un étudiant précédemment marqué comme parti / archivé.
     *
     * <p>Repasse le statut de l'étudiant à {@link StudentStatus#ACTIVE} et
     * persiste la modification (exigence 7.5).</p>
     *
     * @param id l'identifiant de l'étudiant à réactiver
     * @return l'étudiant réactivé
     */
    @Transactional
    public StudentEntity reactivateStudent(Long id) {
        StudentEntity student = studentRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new CustomServiceException("Student not found with id " + id));
        student.setStatus(StudentStatus.ACTIVE);
        return studentRepository.save(student);
    }

    /**
     * Marque un étudiant comme parti / archivé (statut {@link StudentStatus#INACTIVE}) sans
     * supprimer son historique (inscriptions, paiements, présences conservés — exigence 7.1, 7.2).
     *
     * @param id l'identifiant de l'étudiant à archiver
     * @return l'étudiant mis à jour
     */
    @Transactional
    public StudentEntity deactivateStudentStatus(Long id) {
        StudentEntity student = studentRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new CustomServiceException("Student not found with id " + id));
        student.setStatus(StudentStatus.INACTIVE);
        return studentRepository.save(student);
    }

    @Transactional
    public void desactivateStudent(Long id) {
        StudentEntity student = studentRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new CustomServiceException("Student not found with id " + id));
        student.setActive(false);
        studentRepository.save(student);
    }
}