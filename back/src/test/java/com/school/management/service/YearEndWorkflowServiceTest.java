package com.school.management.service;

import com.school.management.dto.StudentDTO;
import com.school.management.dto.StudentDecisionDTO;
import com.school.management.dto.YearEndRequestDTO;
import com.school.management.dto.YearEndResultDTO;
import com.school.management.mapper.StudentMapper;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link YearEndWorkflowService}.
 *
 * <p>Vérifie l'orchestration du workflow de fin d'année : désactivation du drapeau de l'année
 * précédente (Exigence 5.2), préservation de l'historique — rien n'est supprimé (Exigence 5.6),
 * ajout des étudiants au niveau le plus élevé à la liste de revue (Exigences 8.1, 8.2),
 * redoublement conservant niveau + statut ACTIVE (Exigences 6.2, 6.3), départ passant le statut
 * à INACTIVE (Exigence 7.1) et promotion appliquée sans exiger d'inscription (Exigence 14.3).</p>
 *
 * <p>Ces tests sont fondés sur des exemples (et non basés sur les propriétés). Toutes les
 * dépendances (services et repositories) sont simulées avec Mockito.</p>
 */
class YearEndWorkflowServiceTest {

    private CurrentSchoolYearService currentSchoolYearService;
    private SchoolYearService schoolYearService;
    private LevelSequenceService levelSequenceService;
    private LevelRepository levelRepository;
    private StudentRepository studentRepository;
    private StudentMapper studentMapper;

    private YearEndWorkflowService service;

    // Niveaux de référence, ordonnés par séquence croissante.
    private LevelEntity cp;   // séquence 1
    private LevelEntity ce1;  // séquence 2 (niveau le plus élevé du jeu de test)
    private List<LevelEntity> ordered;

    @BeforeEach
    void setUp() {
        currentSchoolYearService = mock(CurrentSchoolYearService.class);
        schoolYearService = mock(SchoolYearService.class);
        levelSequenceService = mock(LevelSequenceService.class);
        levelRepository = mock(LevelRepository.class);
        studentRepository = mock(StudentRepository.class);
        studentMapper = mock(StudentMapper.class);

        service = new YearEndWorkflowService(currentSchoolYearService, schoolYearService,
                levelSequenceService, levelRepository, studentRepository, studentMapper);

        cp = level(100L, "CP", 1);
        ce1 = level(200L, "CE1", 2);
        ordered = List.of(cp, ce1);

        // Stubs communs à tous les scénarios de run().
        SchoolYearEntity current = year("2025-2026", true);
        when(currentSchoolYearService.requireCurrent()).thenReturn(current);
        // La création renvoie la nouvelle année (isCurrent=false, positionné ensuite par makeCurrent).
        when(schoolYearService.create(any(SchoolYearEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Chargement des niveaux ordonnés : le service lit le dépôt puis délègue le tri.
        //
        // Ces deux doublures manquaient. Le service ne recevait donc qu'une liste vide de
        // niveaux : tout étudiant passait pour être au niveau le plus élevé, aucune promotion
        // n'était calculée, et les autres scénarios de ce fichier réussissaient pour la
        // mauvaise raison (leur assertion attend précisément un niveau inchangé).
        when(levelRepository.findAllByOrderByLevelSequenceAsc()).thenReturn(ordered);
        when(levelSequenceService.sortBySequence(ordered)).thenReturn(ordered);
        // Le chargement d'un niveau cible renvoie l'entité correspondante.
        when(levelRepository.findById(cp.getId())).thenReturn(Optional.of(cp));
        when(levelRepository.findById(ce1.getId())).thenReturn(Optional.of(ce1));
        // Sauvegarde : renvoie l'entité inchangée.
        when(studentRepository.save(any(StudentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------
    // Outils de construction
    // ------------------------------------------------------------------

    private static Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }

    private static SchoolYearEntity year(String label, boolean isCurrent) {
        return SchoolYearEntity.builder()
                .label(label)
                .startDate(date(2025, 9, 1))
                .endDate(date(2026, 6, 30))
                .isCurrent(isCurrent)
                .build();
    }

    private static LevelEntity level(long id, String name, int sequence) {
        LevelEntity level = new LevelEntity();
        level.setId(id);
        level.setName(name);
        level.setLevelSequence(sequence);
        // Le workflow ne retient que les niveaux actifs : sans ce drapeau, le niveau est
        // écarté de l'ordre de passage et aucune promotion n'est calculée.
        level.setActive(true);
        return level;
    }

    private static StudentEntity student(long id, LevelEntity level, StudentStatus status) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setLevel(level);
        student.setStatus(status);
        return student;
    }

    private static YearEndRequestDTO requestWithDecisions(StudentDecisionDTO... decisions) {
        return YearEndRequestDTO.builder()
                .decisions(new ArrayList<>(List.of(decisions)))
                .build();
    }

    // ------------------------------------------------------------------
    // Exigence 5.2 : le drapeau de l'année précédente est désactivé
    // ------------------------------------------------------------------

    @Test
    void run_marksNewYearCurrentAndClearsPreviousFlag() {
        SchoolYearEntity previous = year("2025-2026", true);
        when(currentSchoolYearService.requireCurrent()).thenReturn(previous);
        when(studentRepository.findByStatus(StudentStatus.ACTIVE)).thenReturn(List.of());

        // makeCurrent (simulé) reproduit le basculement des drapeaux (5.1, 5.2).
        doAnswer(inv -> {
            SchoolYearEntity target = inv.getArgument(0);
            previous.setIsCurrent(false);
            target.setIsCurrent(true);
            return null;
        }).when(currentSchoolYearService).makeCurrent(any(SchoolYearEntity.class));

        YearEndResultDTO result = service.run(new YearEndRequestDTO());

        // makeCurrent a bien été invoqué avec la nouvelle année (dérivée "2026-2027").
        verify(currentSchoolYearService).makeCurrent(any(SchoolYearEntity.class));
        assertThat(previous.getIsCurrent()).isFalse();
        assertThat(result.getNewYear().getLabel()).isEqualTo("2026-2027");
    }

    // ------------------------------------------------------------------
    // Exigence 5.6 : préservation de l'historique (aucune suppression)
    // ------------------------------------------------------------------

    @Test
    void run_preservesHistory_neverDeletesData() {
        StudentEntity alice = student(1L, cp, StudentStatus.ACTIVE);
        when(studentRepository.findByStatus(StudentStatus.ACTIVE)).thenReturn(List.of(alice));
        when(levelSequenceService.nextLevel(cp, ordered)).thenReturn(Optional.of(ce1));

        service.run(new YearEndRequestDTO());

        // Rien n'est supprimé ni réaffecté : aucune suppression sur les repositories.
        verify(studentRepository, never()).delete(any(StudentEntity.class));
        verify(studentRepository, never()).deleteById(anyLong());
        verify(studentRepository, never()).deleteAll();
        verify(levelRepository, never()).delete(any(LevelEntity.class));
        verify(levelRepository, never()).deleteById(anyLong());
    }

    // ------------------------------------------------------------------
    // Exigences 8.1, 8.2 : étudiant au niveau le plus élevé ajouté à la liste de revue
    // ------------------------------------------------------------------

    @Test
    void run_highestLevelStudentAskedToPromote_isUnchangedAndAddedToReviewList() {
        StudentEntity bob = student(2L, ce1, StudentStatus.ACTIVE);
        when(studentRepository.findByStatus(StudentStatus.ACTIVE)).thenReturn(List.of(bob));
        // ce1 est le niveau le plus élevé : pas de niveau suivant.
        when(levelSequenceService.nextLevel(ce1, ordered)).thenReturn(Optional.empty());

        StudentDTO bobDto = new StudentDTO();
        bobDto.setId(2L);
        when(studentMapper.studentToStudentDTO(bob)).thenReturn(bobDto);

        // Décision explicite PROMOTION pour un étudiant déjà au niveau le plus élevé.
        YearEndResultDTO result = service.run(requestWithDecisions(
                StudentDecisionDTO.builder().studentId(2L).decision(PromotionDecision.PROMOTION).build()));

        // Niveau inchangé, statut ACTIVE, présent dans la liste de revue.
        assertThat(bob.getLevel()).isSameAs(ce1);
        assertThat(bob.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(result.getReviewList()).containsExactly(bobDto);
        assertThat(result.getAppliedCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Exigences 6.2, 6.3 : redoublement conserve le niveau et le statut ACTIVE
    // ------------------------------------------------------------------

    @Test
    void run_redoublement_keepsLevelAndActiveStatus() {
        StudentEntity carol = student(3L, cp, StudentStatus.ACTIVE);
        when(studentRepository.findByStatus(StudentStatus.ACTIVE)).thenReturn(List.of(carol));
        when(levelSequenceService.nextLevel(cp, ordered)).thenReturn(Optional.of(ce1));

        YearEndResultDTO result = service.run(requestWithDecisions(
                StudentDecisionDTO.builder().studentId(3L).decision(PromotionDecision.REDOUBLEMENT).build()));

        // Le niveau reste CP (inchangé) et le statut reste ACTIVE.
        assertThat(carol.getLevel()).isSameAs(cp);
        assertThat(carol.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(result.getReviewList()).isEmpty();
        assertThat(result.getAppliedCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Exigence 7.1 : départ positionne le statut à INACTIVE
    // ------------------------------------------------------------------

    @Test
    void run_departure_setsStatusInactiveAndKeepsLevel() {
        StudentEntity dan = student(4L, cp, StudentStatus.ACTIVE);
        when(studentRepository.findByStatus(StudentStatus.ACTIVE)).thenReturn(List.of(dan));
        when(levelSequenceService.nextLevel(cp, ordered)).thenReturn(Optional.of(ce1));

        service.run(requestWithDecisions(
                StudentDecisionDTO.builder().studentId(4L).decision(PromotionDecision.DEPARTURE).build()));

        // Départ : statut INACTIVE, niveau inchangé.
        assertThat(dan.getStatus()).isEqualTo(StudentStatus.INACTIVE);
        assertThat(dan.getLevel()).isSameAs(cp);
    }

    // ------------------------------------------------------------------
    // Exigence 14.3 : un étudiant sans inscription est tout de même promu
    // ------------------------------------------------------------------

    @Test
    void run_studentWithoutEnrollment_isStillPromoted() {
        // L'étudiant n'a aucun groupe (aucune inscription) mais reste actif.
        StudentEntity eve = student(5L, cp, StudentStatus.ACTIVE);
        eve.setGroups(null);
        when(studentRepository.findByStatus(StudentStatus.ACTIVE)).thenReturn(List.of(eve));
        when(levelSequenceService.nextLevel(cp, ordered)).thenReturn(Optional.of(ce1));

        // Décision par défaut (aucune décision fournie) → PROMOTION (Exigence 5.7).
        YearEndResultDTO result = service.run(new YearEndRequestDTO());

        // La promotion est appliquée directement au niveau, sans exiger d'inscription.
        assertThat(eve.getLevel()).isSameAs(ce1);
        assertThat(eve.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(result.getAppliedCount()).isEqualTo(1);
        assertThat(result.getReviewList()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Niveau dont le drapeau « actif » est nul
    // ------------------------------------------------------------------

    @Test
    void run_levelWithNullActiveFlag_isTreatedAsActive() {
        // Un niveau antérieur à l'ajout de la colonne « active » porte un drapeau nul.
        // Il doit rester dans l'ordre de passage : sinon la promotion serait impossible.
        cp.setActive(null);
        ce1.setActive(null);

        StudentEntity frank = student(6L, cp, StudentStatus.ACTIVE);
        when(studentRepository.findByStatus(StudentStatus.ACTIVE)).thenReturn(List.of(frank));
        when(levelSequenceService.nextLevel(cp, ordered)).thenReturn(Optional.of(ce1));

        service.run(new YearEndRequestDTO());

        assertThat(frank.getLevel()).isSameAs(ce1);
    }

    // ------------------------------------------------------------------
    // Niveau désactivé : écarté de l'ordre de passage
    // ------------------------------------------------------------------

    @Test
    void run_deactivatedLevel_isExcludedFromSequence() {
        // Seul CP reste actif : il devient donc le niveau le plus élevé, et l'étudiant qui s'y
        // trouve part en revue au lieu d'être promu vers un niveau désactivé.
        ce1.setActive(false);

        StudentEntity gina = student(7L, cp, StudentStatus.ACTIVE);
        when(studentRepository.findByStatus(StudentStatus.ACTIVE)).thenReturn(List.of(gina));
        when(levelSequenceService.sortBySequence(List.of(cp))).thenReturn(List.of(cp));
        when(levelSequenceService.nextLevel(cp, List.of(cp))).thenReturn(Optional.empty());

        YearEndResultDTO result = service.run(new YearEndRequestDTO());

        assertThat(gina.getLevel()).isSameAs(cp);
        assertThat(result.getReviewList()).hasSize(1);
    }
}
