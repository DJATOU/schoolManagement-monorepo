package com.school.management.service;

import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.shared.mapper.MappingContext;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service de gestion des années scolaires.
 * Prend en charge la création (avec validation), la liste (triée par date de début
 * décroissante) et la récupération d'une année scolaire.
 * La désignation de l'année courante (setCurrent/makeCurrent) est gérée par
 * {@code CurrentSchoolYearService}.
 */
@Service
public class SchoolYearService {

    private final SchoolYearRepository schoolYearRepository;

    // Contexte de mapping partagé, utilisé par le controller pour convertir
    // les DTO en entités via SchoolYearMapper (convention MappingContext).
    private MappingContext mappingContext;

    @Autowired
    public SchoolYearService(SchoolYearRepository schoolYearRepository) {
        this.schoolYearRepository = schoolYearRepository;
    }

    /**
     * Initialise le {@link MappingContext} après injection des dépendances.
     * L'entité {@code SchoolYearEntity} n'ayant aucune relation à résoudre, seul le
     * {@code SchoolYearRepository} est fourni ; le contexte reste homogène avec les
     * autres services (convention {@code MappingContext}).
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.of(
                null, // LevelRepository
                null, // TutorRepository
                null, // GroupTypeRepository
                null, // SubjectRepository
                null, // PricingRepository
                null, // TeacherRepository
                schoolYearRepository,
                null, // RoomRepository
                null, // GroupRepository
                null, // SessionSeriesRepository
                null, // StudentRepository
                null); // SessionRepository
    }

    /**
     * Retourne le {@link MappingContext} pour utilisation par le controller.
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    /**
     * Crée une année scolaire après validation.
     * <ul>
     *   <li>Le libellé, la date de début et la date de fin sont obligatoires.</li>
     *   <li>La date de début doit être strictement antérieure à la date de fin.</li>
     *   <li>Le libellé doit être unique.</li>
     *   <li>S'il s'agit de la première année scolaire créée, elle devient l'année courante.</li>
     * </ul>
     *
     * @param schoolYear l'année scolaire à créer
     * @return l'année scolaire persistée
     */
    @Transactional
    public SchoolYearEntity create(SchoolYearEntity schoolYear) {
        Objects.requireNonNull(schoolYear, "L'année scolaire ne peut pas être nulle.");
        validateForCreation(schoolYear);

        // S'il s'agit de la première année scolaire, elle devient l'année courante (Exigence 2.3).
        if (schoolYearRepository.count() == 0) {
            schoolYear.setIsCurrent(true);
        }

        return schoolYearRepository.save(schoolYear);
    }

    /**
     * Retourne toutes les années scolaires, la plus récente en premier
     * (tri par date de début décroissante).
     */
    @Transactional(readOnly = true)
    public List<SchoolYearEntity> findAll() {
        return schoolYearRepository.findAllByOrderByStartDateDesc();
    }

    /**
     * Récupère une année scolaire par son identifiant.
     */
    @Transactional(readOnly = true)
    public Optional<SchoolYearEntity> findById(Long id) {
        return schoolYearRepository.findById(Objects.requireNonNull(id));
    }

    /**
     * Valide les champs obligatoires, l'ordre des dates et l'unicité du libellé
     * avant la création. Lève une {@link CustomServiceException} (HTTP 400) en cas d'erreur.
     */
    private void validateForCreation(SchoolYearEntity schoolYear) {
        if (!StringUtils.hasText(schoolYear.getLabel())) {
            throw new CustomServiceException(
                    "Le libellé de l'année scolaire est obligatoire.",
                    HttpStatus.BAD_REQUEST);
        }
        if (schoolYear.getStartDate() == null || schoolYear.getEndDate() == null) {
            throw new CustomServiceException(
                    "La date de début et la date de fin de l'année scolaire sont obligatoires.",
                    HttpStatus.BAD_REQUEST);
        }
        if (!schoolYear.getStartDate().before(schoolYear.getEndDate())) {
            throw new CustomServiceException(
                    "La date de début doit être antérieure à la date de fin.",
                    HttpStatus.BAD_REQUEST);
        }
        if (schoolYearRepository.findByLabel(schoolYear.getLabel()).isPresent()) {
            throw new CustomServiceException(
                    "Une année scolaire avec ce libellé existe déjà : " + schoolYear.getLabel(),
                    HttpStatus.BAD_REQUEST);
        }
    }
}
