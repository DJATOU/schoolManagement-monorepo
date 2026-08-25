package com.school.management.service.student;

import com.school.management.dto.LevelDto;
import com.school.management.dto.ParcoursDTO;
import com.school.management.dto.ParcoursYearDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.mapper.GroupMapperImpl;
import com.school.management.mapper.LeveLMapper;
import com.school.management.mapper.LeveLMapperImpl;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.StudentGroupRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test de propriété (jqwik) pour {@link StudentParcoursService}.
 *
 * <p>Vérifie la dérivation du niveau historique et du parcours à partir des inscriptions
 * de l'étudiant réparties sur plusieurs années scolaires : le parcours contient exactement
 * les années distinctes ayant au moins une inscription (les autres sont omises, sans niveau
 * historique), triées par date de début décroissante ; pour chaque année, l'ensemble des
 * niveaux rapportés est égal aux niveaux distincts des groupes inscrits.</p>
 *
 * <p>Le {@link StudentGroupRepository} est simulé (Mockito) ; les mappers réels
 * ({@link GroupMapperImpl}, {@link LeveLMapperImpl}) sont utilisés comme dans le test
 * unitaire existant.</p>
 */
class StudentParcoursDerivationPropertyTest {

    /** Identifiant fixe de l'étudiant utilisé dans toutes les itérations. */
    private static final Long STUDENT_ID = 42L;

    // Feature: school-year, Property 6: For any set of Student enrollments across School Years, the parcours contains exactly the distinct years with at least one enrollment (others omitted, no historical level), ordered by start date descending; each year's reported Level set equals the distinct Levels of the enrolled Groups.
    @Property(tries = 100)
    void property6_historicalLevelAndParcoursDerivation(@ForAll("enrollmentGraphs") EnrollmentGraph graph) {
        // --- Construction des entités à partir du graphe généré. -----------------------------
        // Une année scolaire par offset généré : identifiant = index + 1, dates distinctes.
        List<SchoolYearEntity> years = new ArrayList<>();
        for (int i = 0; i < graph.startOffsets.size(); i++) {
            SchoolYearEntity year = new SchoolYearEntity();
            year.setId((long) (i + 1));
            year.setLabel("Y" + (i + 1));
            // Offsets distincts -> dates de début distinctes -> ordre non ambigu.
            year.setStartDate(new Date(graph.startOffsets.get(i) * 86_400_000L));
            years.add(year);
        }

        // Chaque inscription devient un groupe unique (année + niveau), simulant un graphe
        // d'inscriptions aléatoire pouvant couvrir plusieurs niveaux par année.
        List<StudentGroupEntity> enrollments = new ArrayList<>();
        long nextGroupId = 1L;
        for (int[] spec : graph.enrollments) {
            int yearIndex = spec[0];
            long levelId = spec[1];

            LevelEntity level = new LevelEntity();
            level.setId(levelId);
            level.setName("L" + levelId);

            GroupEntity group = new GroupEntity();
            group.setId(nextGroupId++);
            group.setName("G" + group.getId());
            group.setSchoolYear(years.get(yearIndex));
            group.setLevel(level);

            StudentGroupEntity sg = new StudentGroupEntity();
            sg.setGroup(group);
            enrollments.add(sg);
        }

        StudentGroupRepository studentGroupRepository = mock(StudentGroupRepository.class);
        when(studentGroupRepository.findByStudentIdAndActiveTrue(STUDENT_ID)).thenReturn(enrollments);

        GroupMapper groupMapper = new GroupMapperImpl();
        LeveLMapper levelMapper = new LeveLMapperImpl();
        StudentParcoursService service = new StudentParcoursService(studentGroupRepository, groupMapper, levelMapper);

        // --- Résultat attendu, calculé indépendamment du service. ----------------------------
        // Niveaux distincts (par identifiant) par identifiant d'année scolaire inscrite.
        Map<Long, Set<Long>> expectedLevelsByYear = new TreeMap<>();
        for (int[] spec : graph.enrollments) {
            long yearId = spec[0] + 1L;
            long levelId = spec[1];
            expectedLevelsByYear.computeIfAbsent(yearId, k -> new LinkedHashSet<>()).add(levelId);
        }

        // Ordre attendu : années inscrites triées par date de début décroissante.
        List<Long> expectedOrderedYearIds = expectedLevelsByYear.keySet().stream()
                .sorted(Comparator.comparing(
                        (Long yearId) -> years.get(yearId.intValue() - 1).getStartDate()).reversed())
                .collect(Collectors.toList());

        // --- Exécution. ----------------------------------------------------------------------
        ParcoursDTO parcours = service.getParcours(STUDENT_ID);

        // --- Assertions. ---------------------------------------------------------------------
        assertThat(parcours.getStudentId()).isEqualTo(STUDENT_ID);

        List<Long> actualYearIds = parcours.getYears().stream()
                .map(ParcoursYearDTO::getSchoolYearId)
                .collect(Collectors.toList());

        // Le parcours contient exactement les années distinctes inscrites, triées par date de
        // début décroissante (les années sans inscription sont omises).
        assertThat(actualYearIds)
                .as("années du parcours (exactement les années inscrites, triées par date desc)")
                .containsExactlyElementsOf(expectedOrderedYearIds);

        // Pour chaque année, l'ensemble des niveaux rapportés (par identifiant) est égal aux
        // niveaux distincts des groupes inscrits cette année-là.
        for (ParcoursYearDTO entry : parcours.getYears()) {
            Set<Long> actualLevelIds = entry.getLevels().stream()
                    .map(LevelDto::getId)
                    .collect(Collectors.toSet());
            assertThat(actualLevelIds)
                    .as("niveaux distincts pour l'année %s", entry.getSchoolYearId())
                    .isEqualTo(expectedLevelsByYear.get(entry.getSchoolYearId()));
        }
    }

    // ------------------------------------------------------------------
    // Générateurs
    // ------------------------------------------------------------------

    /**
     * Graphe d'inscriptions aléatoire : un ensemble d'années scolaires (offsets de dates de
     * début distincts) et une liste d'inscriptions (année, niveau). Couvre les cas d'un étudiant
     * sans inscription (liste vide) et d'années sans groupe inscrit (indices d'année jamais
     * choisis).
     */
    @Provide
    Arbitrary<EnrollmentGraph> enrollmentGraphs() {
        // Offsets de dates de début uniques : 0 à 6 années disponibles dans le pool.
        Arbitrary<List<Integer>> startOffsets = Arbitraries.integers().between(0, 100_000)
                .list().uniqueElements().ofMinSize(0).ofMaxSize(6);

        return startOffsets.flatMap(offsets -> {
            int yearCount = offsets.size();
            if (yearCount == 0) {
                // Aucune année dans le pool -> aucune inscription possible (parcours vide).
                return Arbitraries.just(new EnrollmentGraph(offsets, List.of()));
            }
            Arbitrary<int[]> oneEnrollment = Combinators.combine(
                    Arbitraries.integers().between(0, yearCount - 1),
                    Arbitraries.integers().between(1, 4)
            ).as((yearIndex, levelId) -> new int[]{yearIndex, levelId});

            return oneEnrollment.list().ofMinSize(0).ofMaxSize(15)
                    .map(enrollments -> new EnrollmentGraph(offsets, enrollments));
        });
    }

    /** Graphe d'inscriptions généré : offsets de dates de début et inscriptions (année, niveau). */
    private static final class EnrollmentGraph {
        private final List<Integer> startOffsets;
        private final List<int[]> enrollments;

        private EnrollmentGraph(List<Integer> startOffsets, List<int[]> enrollments) {
            this.startOffsets = startOffsets;
            this.enrollments = enrollments;
        }

        @Override
        public String toString() {
            List<String> pairs = enrollments.stream()
                    .map(e -> "(y" + (e[0] + 1) + ",L" + e[1] + ")")
                    .collect(Collectors.toList());
            return "EnrollmentGraph{years=" + startOffsets.size() + ", enrollments=" + pairs + "}";
        }
    }
}
