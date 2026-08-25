package com.school.management.service.group;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.service.CurrentSchoolYearService;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.exception.NoCurrentSchoolYearException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour le rattachement de l'année scolaire lors de la
 * création d'un groupe et pour la consultation du garde lecture seule sur les mutations
 * (Exigences 3.2, 3.3, 9.2, 13.3).
 */
class GroupServiceSchoolYearTest {

    private GroupRepository groupRepository;
    private CurrentSchoolYearService currentSchoolYearService;
    private ReadOnlyYearGuard readOnlyYearGuard;
    private GroupServiceImpl groupService;

    private SchoolYearEntity currentYear;
    private SchoolYearEntity explicitYear;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        currentSchoolYearService = mock(CurrentSchoolYearService.class);
        readOnlyYearGuard = mock(ReadOnlyYearGuard.class);

        // Seules les dépendances utiles à ces scénarios sont réelles ; le reste est mocké.
        groupService = new GroupServiceImpl(
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

        currentYear = SchoolYearEntity.builder().label("2025-2026").isCurrent(true).build();
        currentYear.setId(1L);
        explicitYear = SchoolYearEntity.builder().label("2024-2025").isCurrent(false).build();
        explicitYear.setId(2L);

        when(groupRepository.save(any(GroupEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------------
    // createGroup
    // ------------------------------------------------------------------

    @Test
    void createGroup_assignsCurrentYearWhenNoneProvided() {
        when(currentSchoolYearService.requireCurrent()).thenReturn(currentYear);
        GroupEntity group = GroupEntity.builder().name("Groupe A").build();

        GroupEntity saved = groupService.createGroup(group);

        assertThat(saved.getSchoolYear()).isEqualTo(currentYear);
        verify(currentSchoolYearService).requireCurrent();
    }

    @Test
    void createGroup_keepsExplicitlyProvidedYear() {
        GroupEntity group = GroupEntity.builder().name("Groupe B").schoolYear(explicitYear).build();

        GroupEntity saved = groupService.createGroup(group);

        assertThat(saved.getSchoolYear()).isEqualTo(explicitYear);
        // L'année courante n'est pas consultée lorsqu'une année est fournie (Exigence 3.3).
        verify(currentSchoolYearService, never()).requireCurrent();
    }

    @Test
    void createGroup_blocksWhenNoCurrentYear() {
        when(currentSchoolYearService.requireCurrent())
                .thenThrow(new NoCurrentSchoolYearException());
        GroupEntity group = GroupEntity.builder().name("Groupe C").build();

        assertThatThrownBy(() -> groupService.createGroup(group))
                .isInstanceOf(NoCurrentSchoolYearException.class);
        verify(groupRepository, never()).save(any(GroupEntity.class));
    }

    // ------------------------------------------------------------------
    // updateGroup
    // ------------------------------------------------------------------

    @Test
    void updateGroup_allowsCurrentYearAndPreservesExistingYear() {
        GroupEntity existing = GroupEntity.builder().name("Ancien").schoolYear(currentYear).build();
        existing.setId(10L);
        when(groupRepository.findById(10L)).thenReturn(Optional.of(existing));
        GroupEntity update = GroupEntity.builder().name("Nouveau").build();

        GroupEntity saved = groupService.updateGroup(10L, update);

        verify(readOnlyYearGuard).assertGroupMutable(existing);
        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getSchoolYear()).isEqualTo(currentYear);
    }

    @Test
    void updateGroup_rejectsPastYear() {
        GroupEntity existing = GroupEntity.builder().name("Ancien").schoolYear(explicitYear).build();
        existing.setId(11L);
        when(groupRepository.findById(11L)).thenReturn(Optional.of(existing));
        doThrow(new ReadOnlySchoolYearException())
                .when(readOnlyYearGuard).assertGroupMutable(existing);

        assertThatThrownBy(() -> groupService.updateGroup(11L, GroupEntity.builder().build()))
                .isInstanceOf(ReadOnlySchoolYearException.class);
        verify(groupRepository, never()).save(any(GroupEntity.class));
    }

    // ------------------------------------------------------------------
    // delete
    // ------------------------------------------------------------------

    @Test
    void delete_rejectsPastYearGroup() {
        GroupEntity existing = GroupEntity.builder().schoolYear(explicitYear).build();
        existing.setId(12L);
        when(groupRepository.findById(12L)).thenReturn(Optional.of(existing));
        doThrow(new ReadOnlySchoolYearException())
                .when(readOnlyYearGuard).assertGroupMutable(existing);

        assertThatThrownBy(() -> groupService.delete(12L))
                .isInstanceOf(ReadOnlySchoolYearException.class);
        verify(groupRepository, never()).deleteById(anyLong());
    }

    @Test
    void delete_allowsCurrentYearGroup() {
        GroupEntity existing = GroupEntity.builder().schoolYear(currentYear).build();
        existing.setId(13L);
        when(groupRepository.findById(13L)).thenReturn(Optional.of(existing));

        assertThatCode(() -> groupService.delete(13L)).doesNotThrowAnyException();
        verify(readOnlyYearGuard).assertGroupMutable(existing);
        verify(groupRepository).deleteById(13L);
    }
}
