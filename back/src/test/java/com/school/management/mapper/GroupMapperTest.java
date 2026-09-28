package com.school.management.mapper;

import com.school.management.dto.GroupDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.shared.exception.ResourceNotFoundException;
import com.school.management.shared.mapper.MappingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du {@link GroupMapper} centrés sur l'année scolaire (Exigence 3.1).
 *
 * <p>Couvre :</p>
 * <ul>
 *   <li>l'aplatissement entité → DTO du {@code schoolYearId} et du {@code schoolYearLabel} ;</li>
 *   <li>la résolution DTO → entité du {@code schoolYearId} via {@link MappingContext}
 *       (qualifier {@code idToSchoolYear}), y compris le rejet d'un identifiant inconnu ;</li>
 *   <li>l'aller-retour (round-trip) préservant l'identifiant de l'année scolaire.</li>
 * </ul>
 */
class GroupMapperTest {

    private final GroupMapper mapper = new GroupMapperImpl();

    private SchoolYearRepository schoolYearRepository;
    private MappingContext context;

    @BeforeEach
    void setUp() {
        schoolYearRepository = mock(SchoolYearRepository.class);
        context = MappingContext.of(
                null, null, null, null, null, null,
                schoolYearRepository,
                null, null, null, null, null);
    }

    private static SchoolYearEntity schoolYear(long id, String label) {
        SchoolYearEntity y = new SchoolYearEntity();
        y.setId(id);
        y.setLabel(label);
        return y;
    }

    // ------------------------------------------------------------------
    // Entité → DTO : aplatissement de l'année scolaire
    // ------------------------------------------------------------------

    @Test
    void toDto_withSchoolYear_carriesIdAndLabel() {
        GroupEntity entity = new GroupEntity();
        entity.setId(5L);
        entity.setName("Groupe Maths");
        entity.setSchoolYear(schoolYear(42L, "2024-2025"));

        GroupDTO dto = mapper.groupToGroupDTO(entity);

        assertThat(dto.getSchoolYearId()).isEqualTo(42L);
        assertThat(dto.getSchoolYearLabel()).isEqualTo("2024-2025");
    }

    @Test
    void toDto_withoutSchoolYear_leavesSchoolYearNull() {
        GroupEntity entity = new GroupEntity();
        entity.setId(6L);
        entity.setName("Groupe sans année");
        entity.setSchoolYear(null);

        GroupDTO dto = mapper.groupToGroupDTO(entity);

        assertThat(dto.getSchoolYearId()).isNull();
        assertThat(dto.getSchoolYearLabel()).isNull();
    }

    // ------------------------------------------------------------------
    // DTO → entité : résolution du schoolYearId via MappingContext
    // ------------------------------------------------------------------

    @Test
    void toEntity_resolvesSchoolYearFromId() {
        SchoolYearEntity year = schoolYear(42L, "2024-2025");
        when(schoolYearRepository.findById(42L)).thenReturn(Optional.of(year));

        GroupDTO dto = GroupDTO.builder()
                .name("Groupe Maths")
                .schoolYearId(42L)
                .build();

        GroupEntity entity = mapper.groupDTOToGroup(dto, context);

        assertThat(entity.getSchoolYear()).isSameAs(year);
        assertThat(entity.getSchoolYear().getId()).isEqualTo(42L);
    }

    @Test
    void toEntity_nullSchoolYearId_resolvesToNull() {
        GroupDTO dto = GroupDTO.builder()
                .name("Groupe sans année")
                .schoolYearId(null)
                .build();

        GroupEntity entity = mapper.groupDTOToGroup(dto, context);

        assertThat(entity.getSchoolYear()).isNull();
    }

    @Test
    void toEntity_unknownSchoolYear_throwsResourceNotFound() {
        when(schoolYearRepository.findById(99L)).thenReturn(Optional.empty());

        GroupDTO dto = GroupDTO.builder()
                .name("Groupe année inconnue")
                .schoolYearId(99L)
                .build();

        assertThatThrownBy(() -> mapper.groupDTOToGroup(dto, context))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Aller-retour : l'identifiant de l'année scolaire est préservé
    // ------------------------------------------------------------------

    @Test
    void roundTrip_preservesSchoolYearId() {
        SchoolYearEntity year = schoolYear(42L, "2024-2025");
        when(schoolYearRepository.findById(42L)).thenReturn(Optional.of(year));

        GroupEntity original = new GroupEntity();
        original.setId(5L);
        original.setName("Groupe Maths");
        original.setSchoolYear(year);

        // Entité → DTO → entité
        GroupDTO dto = mapper.groupToGroupDTO(original);
        GroupEntity roundTripped = mapper.groupDTOToGroup(dto, context);

        assertThat(dto.getSchoolYearId()).isEqualTo(42L);
        assertThat(roundTripped.getSchoolYear()).isSameAs(year);
        assertThat(roundTripped.getSchoolYear().getId()).isEqualTo(42L);
    }
}
