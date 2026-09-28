package com.school.management.service.group;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.service.CurrentSchoolYearService;
import com.school.management.service.ReadOnlyYearGuard;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour le rattachement de l'année scolaire lors de la création
 * d'un groupe (Exigences 3.2, 3.3).
 *
 * <p>Pour chaque scénario généré aléatoirement, un groupe est créé soit sans année explicite
 * (l'année courante doit alors être rattachée), soit avec une année explicite (celle-ci doit
 * être conservée). Le service {@link CurrentSchoolYearService} et le dépôt sont simulés avec
 * Mockito ; {@code groupRepository.save(...)} renvoie son argument.</p>
 */
class GroupYearAssignmentPropertyTest {

    // Feature: school-year, Property 9: For any Group created without an explicit year while a current year exists, its year equals the current year; for any Group created with an explicit year, its year equals the specified year.
    @Property(tries = 100)
    void property9_groupYearAssignmentOnCreation(
            @ForAll boolean withExplicitYear,
            @ForAll("yearIds") Long currentYearId,
            @ForAll("yearIds") Long explicitYearId) {

        // --- Arrange : dépendances simulées et service sous test ---
        GroupRepository groupRepository = mock(GroupRepository.class);
        CurrentSchoolYearService currentSchoolYearService = mock(CurrentSchoolYearService.class);
        ReadOnlyYearGuard readOnlyYearGuard = mock(ReadOnlyYearGuard.class);

        GroupServiceImpl groupService = new GroupServiceImpl(
                groupRepository,
                mock(com.school.management.mapper.GroupMapper.class),
                mock(com.school.management.mapper.StudentMapper.class),
                mock(org.modelmapper.ModelMapper.class),
                mock(GroupSearchService.class),
                mock(com.school.management.repository.StudentGroupRepository.class),
                mock(com.school.management.repository.AttendanceRepository.class),
                mock(com.school.management.infrastructure.storage.FileManagementService.class),
                currentSchoolYearService,
                readOnlyYearGuard,
                mock(com.school.management.repository.GroupTypeRepository.class),
                mock(com.school.management.repository.LevelRepository.class),
                mock(com.school.management.repository.SubjectRepository.class),
                mock(com.school.management.repository.PricingRepository.class),
                mock(com.school.management.repository.TeacherRepository.class),
                mock(com.school.management.repository.SchoolYearRepository.class));

        // L'année courante existe (un current year est présent).
        SchoolYearEntity currentYear = SchoolYearEntity.builder().label("2025-2026").isCurrent(true).build();
        currentYear.setId(currentYearId);
        when(currentSchoolYearService.requireCurrent()).thenReturn(currentYear);

        // Le dépôt renvoie l'entité passée à save(...).
        when(groupRepository.save(any(GroupEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Le groupe est créé avec ou sans année explicite selon le scénario généré.
        GroupEntity.GroupEntityBuilder<?, ?> builder = GroupEntity.builder().name("Groupe");
        SchoolYearEntity explicitYear = null;
        if (withExplicitYear) {
            explicitYear = SchoolYearEntity.builder().label("2024-2025").isCurrent(false).build();
            explicitYear.setId(explicitYearId);
            builder.schoolYear(explicitYear);
        }
        GroupEntity group = builder.build();

        // --- Act ---
        GroupEntity saved = groupService.createGroup(group);

        // --- Assert ---
        if (withExplicitYear) {
            // Année explicite fournie : elle est conservée telle quelle (Exigence 3.3).
            assertThat(saved.getSchoolYear())
                    .as("un groupe créé avec une année explicite conserve cette année")
                    .isEqualTo(explicitYear);
        } else {
            // Aucune année fournie : l'année courante est rattachée (Exigence 3.2).
            assertThat(saved.getSchoolYear())
                    .as("un groupe créé sans année explicite reçoit l'année courante")
                    .isEqualTo(currentYear);
        }
    }

    /** Identifiants d'années scolaires non nuls, incluant des cas limites. */
    @Provide
    Arbitrary<Long> yearIds() {
        return Arbitraries.longs().between(1L, 1_000_000L);
    }
}
