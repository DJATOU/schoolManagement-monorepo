package com.school.management.service;

import com.school.management.dto.SchoolYearDTO;
import com.school.management.dto.StudentDecisionDTO;
import com.school.management.dto.StudentDecisionPreviewDTO;
import com.school.management.dto.YearEndPreviewDTO;
import com.school.management.dto.YearEndRequestDTO;
import com.school.management.dto.YearEndResultDTO;
import com.school.management.mapper.StudentMapper;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.shared.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Assistant de fin d'année ({@code Year_End_Workflow}).
 *
 * <p>Clôture l'année scolaire courante et ouvre l'année suivante en une seule transaction :
 * il crée la nouvelle année, la marque comme courante, puis applique à chaque étudiant actif
 * sa décision (PROMOTION par défaut), en s'appuyant sur {@link LevelSequenceService} pour le
 * niveau suivant et sur {@link PromotionCalculator} pour l'issue (niveau cible / statut).</p>
 *
 * <p>Toutes les données de l'année précédente (inscriptions, groupes, séries, séances,
 * paiements, présences) sont <strong>préservées telles quelles</strong> : rien n'est supprimé
 * ni réaffecté (Exigence 5.6). La décision est appliquée directement au niveau de l'étudiant,
 * sans exiger d'inscription dans l'année courante (Exigence 14.3).</p>
 */
@Service
public class YearEndWorkflowService {

    private final CurrentSchoolYearService currentSchoolYearService;
    private final SchoolYearService schoolYearService;
    private final LevelSequenceService levelSequenceService;
    private final LevelRepository levelRepository;
    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;

    // Calculateur de promotion pur (sans état, sans I/O).
    private final PromotionCalculator promotionCalculator = new PromotionCalculator();

    @Autowired
    public YearEndWorkflowService(CurrentSchoolYearService currentSchoolYearService,
                                  SchoolYearService schoolYearService,
                                  LevelSequenceService levelSequenceService,
                                  LevelRepository levelRepository,
                                  StudentRepository studentRepository,
                                  StudentMapper studentMapper) {
        this.currentSchoolYearService = currentSchoolYearService;
        this.schoolYearService = schoolYearService;
        this.levelSequenceService = levelSequenceService;
        this.levelRepository = levelRepository;
        this.studentRepository = studentRepository;
        this.studentMapper = studentMapper;
    }

    /**
     * Exécute le workflow de fin d'année (clôture / ouverture) en une seule transaction.
     *
     * <p>Étapes : récupération de l'année courante, dérivation/validation du libellé suivant,
     * création de la nouvelle année, désignation comme courante, puis application des décisions
     * par étudiant actif (PROMOTION par défaut). Les étudiants au niveau le plus élevé pour
     * lesquels une promotion est demandée sont laissés inchangés et collectés dans la liste de
     * revue (Exigences 8.1, 8.2).</p>
     *
     * @param request la requête (libellé/dates optionnels, décisions par étudiant optionnelles).
     * @return le résultat : nouvelle année courante, liste de revue et nombre d'étudiants traités.
     */
    @Transactional
    public YearEndResultDTO run(YearEndRequestDTO request) {
        YearEndRequestDTO safeRequest = (request != null) ? request : new YearEndRequestDTO();

        // 1) Récupérer l'année courante (garde 13.x).
        SchoolYearEntity current = currentSchoolYearService.requireCurrent();

        // 2) Déterminer le libellé de l'année suivante (fourni ou dérivé — Exigence 5.1).
        String nextLabel = resolveNextLabel(safeRequest.getNewLabel(), current.getLabel());

        // 3) Créer la nouvelle année scolaire. La validation d'unicité du libellé (Exigence 1.4)
        //    et l'ordre des dates sont assurés par SchoolYearService.create.
        SchoolYearEntity nextYear = SchoolYearEntity.builder()
                .label(nextLabel)
                .startDate(resolveStartDate(safeRequest.getStartDate(), current))
                .endDate(resolveEndDate(safeRequest.getEndDate(), current))
                .isCurrent(false)
                .build();
        SchoolYearEntity createdYear = schoolYearService.create(nextYear);

        // 4) Marquer la nouvelle année comme courante : l'ancienne passe à isCurrent=false
        //    (Exigences 5.1, 5.2).
        currentSchoolYearService.makeCurrent(createdYear);

        // 5) Ordre des niveaux (référence pour le calcul du niveau suivant).
        List<LevelEntity> ordered = loadOrderedLevels();

        // 6) Indexer les décisions par identifiant d'étudiant.
        Map<Long, PromotionDecision> decisionsByStudent = indexDecisions(safeRequest.getDecisions());

        // 7) Appliquer la décision à chaque étudiant actif (sans exiger d'inscription — 14.3).
        List<StudentEntity> students = studentRepository.findByStatus(StudentStatus.ACTIVE);
        List<StudentEntity> reviewList = new ArrayList<>();
        int appliedCount = 0;

        for (StudentEntity student : students) {
            if (student == null) {
                continue;
            }
            PromotionOutcome outcome = applyDecision(student, ordered, decisionsByStudent);
            if (outcome.needsReview()) {
                reviewList.add(student);
            }
            appliedCount++;
        }

        // 8) Rien n'est supprimé ni réaffecté : les données de l'année précédente sont
        //    préservées (Exigence 5.6).

        // 9) Construire le résultat.
        List<com.school.management.dto.StudentDTO> reviewDtos = new ArrayList<>();
        for (StudentEntity student : reviewList) {
            reviewDtos.add(studentMapper.studentToStudentDTO(student));
        }

        return YearEndResultDTO.builder()
                .newYear(toDto(createdYear))
                .reviewList(reviewDtos)
                .appliedCount(appliedCount)
                .build();
    }

    /**
     * Prépare un aperçu du workflow sans rien modifier : libellé de l'année suivante proposé et
     * décision par défaut (PROMOTION) pour chaque étudiant actif, les étudiants au niveau le plus
     * élevé étant signalés pour revue.
     *
     * @return l'aperçu (libellé proposé + décisions par défaut par étudiant actif).
     */
    @Transactional(readOnly = true)
    public YearEndPreviewDTO preview() {
        SchoolYearEntity current = currentSchoolYearService.requireCurrent();
        String proposedNextLabel = SchoolYearLabels.deriveNextLabel(current.getLabel());

        List<LevelEntity> ordered = loadOrderedLevels();
        List<StudentEntity> students = studentRepository.findByStatus(StudentStatus.ACTIVE);

        List<StudentDecisionPreviewDTO> decisions = new ArrayList<>();
        for (StudentEntity student : students) {
            if (student == null) {
                continue;
            }
            // La décision proposée par défaut est PROMOTION (Exigence 5.7). Un étudiant au niveau
            // le plus élevé est signalé pour revue (Exigences 8.1, 8.2).
            boolean needsReview = student.getLevel() != null
                    && levelSequenceService.isHighest(student.getLevel(), ordered);
            decisions.add(StudentDecisionPreviewDTO.builder()
                    .student(studentMapper.studentToStudentDTO(student))
                    .decision(PromotionDecision.PROMOTION)
                    .needsReview(needsReview)
                    .build());
        }

        return YearEndPreviewDTO.builder()
                .proposedNextLabel(proposedNextLabel)
                .decisions(decisions)
                .build();
    }

    /**
     * Charge les niveaux <strong>actifs</strong> ordonnés par rang. Les niveaux désactivés sont
     * ignorés (ils ne doivent pas bloquer le passage). Si un niveau actif n'a pas de rang
     * ({@code levelSequence} nul), renvoie un message métier clair (HTTP 400) nommant les niveaux
     * concernés, plutôt qu'une erreur serveur : l'administrateur doit d'abord définir l'ordre de
     * passage dans « Ressources Académiques &gt; Niveaux ».
     */
    private List<LevelEntity> loadOrderedLevels() {
        // Un drapeau nul vaut « actif » : BaseEntity.isActive() déballe un Boolean et lèverait
        // une NullPointerException sur un niveau antérieur à l'ajout de la colonne, ce qui
        // ferait échouer tout l'assistant de fin d'année.
        List<LevelEntity> activeLevels = levelRepository.findAllByOrderByLevelSequenceAsc().stream()
                .filter(Objects::nonNull)
                .filter(level -> !Boolean.FALSE.equals(level.getActive()))
                .toList();

        List<String> unranked = activeLevels.stream()
                .filter(level -> level.getLevelSequence() == null)
                .map(level -> level.getName() != null ? level.getName() : ("#" + level.getId()))
                .toList();

        if (!unranked.isEmpty()) {
            throw new CustomServiceException(
                    "Les niveaux suivants n'ont pas d'ordre de passage (rang) défini : "
                            + String.join(", ", unranked)
                            + ". Veuillez renseigner leur rang dans « Ressources Académiques > "
                            + "Niveaux » (Éditer) avant de lancer l'assistant de fin d'année.",
                    HttpStatus.BAD_REQUEST);
        }

        // Réutilise la logique de tri pure (valide l'absence de rang nul, déjà garantie ci-dessus).
        return levelSequenceService.sortBySequence(activeLevels);
    }

    /**
     * Applique la décision de fin d'année à un étudiant : calcule le niveau suivant, délègue au
     * {@link PromotionCalculator}, met à jour le niveau et le statut de l'étudiant, puis le
     * persiste. Retourne l'issue calculée (pour la collecte de la liste de revue).
     */
    private PromotionOutcome applyDecision(StudentEntity student,
                                           List<LevelEntity> ordered,
                                           Map<Long, PromotionDecision> decisionsByStudent) {
        LevelEntity currentLevel = student.getLevel();
        if (currentLevel == null || currentLevel.getId() == null) {
            throw new CustomServiceException(
                    "L'étudiant (id=" + student.getId() + ") n'a pas de niveau courant : "
                            + "impossible d'appliquer la promotion.",
                    HttpStatus.BAD_REQUEST);
        }

        // Décision : celle fournie, sinon PROMOTION par défaut (Exigence 5.7).
        PromotionDecision decision = decisionsByStudent.getOrDefault(student.getId(),
                PromotionDecision.PROMOTION);

        Optional<Long> nextLevelId = levelSequenceService.nextLevel(currentLevel, ordered)
                .map(LevelEntity::getId);

        PromotionOutcome outcome = promotionCalculator.decide(currentLevel.getId(), nextLevelId,
                decision);

        // Appliquer le niveau cible (courant ou suivant, jamais fabriqué — Exigence 8.3).
        LevelEntity targetLevel = loadLevel(outcome.targetLevelId());
        student.setLevel(targetLevel);
        student.setStatus(outcome.status());
        studentRepository.save(student);

        return outcome;
    }

    /**
     * Indexe les décisions par identifiant d'étudiant, en ignorant les entrées nulles.
     */
    private Map<Long, PromotionDecision> indexDecisions(List<StudentDecisionDTO> decisions) {
        Map<Long, PromotionDecision> byStudent = new HashMap<>();
        if (decisions == null) {
            return byStudent;
        }
        for (StudentDecisionDTO decision : decisions) {
            if (decision == null || decision.getStudentId() == null) {
                continue;
            }
            PromotionDecision value = (decision.getDecision() != null)
                    ? decision.getDecision()
                    : PromotionDecision.PROMOTION;
            byStudent.put(decision.getStudentId(), value);
        }
        return byStudent;
    }

    /**
     * Retourne le libellé fourni s'il est renseigné, sinon le dérive du libellé courant.
     */
    private String resolveNextLabel(String requestedLabel, String currentLabel) {
        if (requestedLabel != null && !requestedLabel.isBlank()) {
            return requestedLabel;
        }
        return SchoolYearLabels.deriveNextLabel(currentLabel);
    }

    /**
     * Détermine la date de début de la nouvelle année : celle fournie, sinon dérivée de l'année
     * courante en décalant sa date de début d'un an.
     */
    private Date resolveStartDate(Date requested, SchoolYearEntity current) {
        if (requested != null) {
            return requested;
        }
        return plusOneYear(current.getStartDate());
    }

    /**
     * Détermine la date de fin de la nouvelle année : celle fournie, sinon dérivée de l'année
     * courante en décalant sa date de fin d'un an.
     */
    private Date resolveEndDate(Date requested, SchoolYearEntity current) {
        if (requested != null) {
            return requested;
        }
        return plusOneYear(current.getEndDate());
    }

    /**
     * Décale une date d'un an, ou retourne {@code null} si la date est absente (la validation de
     * {@link SchoolYearService#create} rejettera alors une date manquante).
     */
    private Date plusOneYear(Date date) {
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.YEAR, 1);
        return calendar.getTime();
    }

    /**
     * Charge un niveau par son identifiant.
     *
     * @throws ResourceNotFoundException si le niveau n'existe pas.
     */
    private LevelEntity loadLevel(Long levelId) {
        return levelRepository.findById(Objects.requireNonNull(levelId,
                        "L'identifiant du niveau cible est obligatoire."))
                .orElseThrow(() -> new ResourceNotFoundException("Level", levelId));
    }

    /**
     * Convertit une {@link SchoolYearEntity} en {@link SchoolYearDTO} (mapping minimal, affinable
     * en tâche 16.1).
     */
    private SchoolYearDTO toDto(SchoolYearEntity entity) {
        return SchoolYearDTO.builder()
                .id(entity.getId())
                .label(entity.getLabel())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .isCurrent(entity.getIsCurrent())
                .build();
    }
}
