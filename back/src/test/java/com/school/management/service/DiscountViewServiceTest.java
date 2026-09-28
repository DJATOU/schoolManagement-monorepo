package com.school.management.service;

import com.school.management.dto.DiscountResponseDTO;
import com.school.management.mapper.DiscountMapper;
import com.school.management.mapper.DiscountMapperImpl;
import com.school.management.persistance.DiscountEntity;
import com.school.management.persistance.DiscountScope;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link DiscountViewService} : résolution des libellés d'affichage
 * (nom de l'étudiant, libellé de la cible selon la portée) et cas dégradés (cible
 * introuvable, étudiant absent, identifiants nuls).
 */
class DiscountViewServiceTest {

    private final DiscountService discountService = mock(DiscountService.class);
    private final DiscountMapper discountMapper = new DiscountMapperImpl();
    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final SessionSeriesRepository seriesRepository = mock(SessionSeriesRepository.class);
    private final SessionRepository sessionRepository = mock(SessionRepository.class);

    private final DiscountViewService service = new DiscountViewService(
            discountService, discountMapper, groupRepository, seriesRepository, sessionRepository);

    private StudentEntity student(String first, String last) {
        StudentEntity s = new StudentEntity();
        s.setId(7L);
        s.setFirstName(first);
        s.setLastName(last);
        return s;
    }

    private DiscountEntity discount(DiscountScope scope, Long groupId, Long seriesId, Long sessionId,
                                    StudentEntity student) {
        return DiscountEntity.builder()
                .id(1L)
                .student(student)
                .scope(scope)
                .groupId(groupId)
                .seriesId(seriesId)
                .sessionId(sessionId)
                .rate(new BigDecimal("0.50"))
                .build();
    }

    // ------------------------------------------------------------------
    // Portée GROUP
    // ------------------------------------------------------------------

    @Test
    void groupScope_resolvesGroupName() {
        GroupEntity group = new GroupEntity();
        group.setId(31L);
        group.setName("Math 1ère B");
        when(groupRepository.findById(31L)).thenReturn(Optional.of(group));

        DiscountResponseDTO dto = service.toDisplayDto(
                discount(DiscountScope.GROUP, 31L, null, null, student("Bilal", "Amrani")));

        assertThat(dto.studentId()).isEqualTo(7L);
        assertThat(dto.studentName()).isEqualTo("Bilal Amrani");
        assertThat(dto.targetName()).isEqualTo("Math 1ère B");
        assertThat(dto.rate()).isEqualByComparingTo("0.50");
    }

    @Test
    void groupScope_unknownGroup_targetNameNull() {
        when(groupRepository.findById(31L)).thenReturn(Optional.empty());

        DiscountResponseDTO dto = service.toDisplayDto(
                discount(DiscountScope.GROUP, 31L, null, null, student("Bilal", "Amrani")));

        assertThat(dto.targetName()).isNull();
    }

    @Test
    void groupScope_nullGroupId_targetNameNull() {
        DiscountEntity d = discount(DiscountScope.GROUP, null, null, null, student("A", "B"));

        assertThat(service.toDisplayDto(d).targetName()).isNull();
    }

    // ------------------------------------------------------------------
    // Portée SERIES
    // ------------------------------------------------------------------

    @Test
    void seriesScope_resolvesSeriesName() {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(41L);
        series.setName("Septembre 2025");
        when(seriesRepository.findById(41L)).thenReturn(Optional.of(series));

        DiscountResponseDTO dto = service.toDisplayDto(
                discount(DiscountScope.SERIES, null, 41L, null, student("Nourine", "Haddad")));

        assertThat(dto.targetName()).isEqualTo("Septembre 2025");
    }

    @Test
    void seriesScope_unknownSeries_targetNameNull() {
        when(seriesRepository.findById(41L)).thenReturn(Optional.empty());

        assertThat(service.toDisplayDto(
                discount(DiscountScope.SERIES, null, 41L, null, student("A", "B"))).targetName())
                .isNull();
    }

    @Test
    void seriesScope_nullSeriesId_targetNameNull() {
        assertThat(service.toDisplayDto(
                discount(DiscountScope.SERIES, null, null, null, student("A", "B"))).targetName())
                .isNull();
    }

    // ------------------------------------------------------------------
    // Portée SESSION
    // ------------------------------------------------------------------

    @Test
    void sessionScope_resolvesSessionTitle() {
        SessionEntity session = new SessionEntity();
        session.setId(51L);
        session.setTitle("Math séance 1");
        when(sessionRepository.findById(51L)).thenReturn(Optional.of(session));

        assertThat(service.toDisplayDto(
                discount(DiscountScope.SESSION, null, null, 51L, student("A", "B"))).targetName())
                .isEqualTo("Math séance 1");
    }

    @Test
    void sessionScope_unknownSession_targetNameNull() {
        when(sessionRepository.findById(51L)).thenReturn(Optional.empty());

        assertThat(service.toDisplayDto(
                discount(DiscountScope.SESSION, null, null, 51L, student("A", "B"))).targetName())
                .isNull();
    }

    @Test
    void sessionScope_nullSessionId_targetNameNull() {
        assertThat(service.toDisplayDto(
                discount(DiscountScope.SESSION, null, null, null, student("A", "B"))).targetName())
                .isNull();
    }

    // ------------------------------------------------------------------
    // Cas dégradés : portée nulle, étudiant absent ou sans nom
    // ------------------------------------------------------------------

    @Test
    void nullScope_targetNameNull() {
        assertThat(service.toDisplayDto(
                discount(null, null, null, null, student("A", "B"))).targetName())
                .isNull();
    }

    @Test
    void nullStudent_studentNameAndIdNull() {
        DiscountResponseDTO dto = service.toDisplayDto(
                discount(DiscountScope.GROUP, null, null, null, null));

        assertThat(dto.studentId()).isNull();
        assertThat(dto.studentName()).isNull();
    }

    @Test
    void studentWithoutNames_studentNameNull() {
        DiscountResponseDTO dto = service.toDisplayDto(
                discount(DiscountScope.GROUP, null, null, null, student(null, null)));

        assertThat(dto.studentName()).isNull();
    }

    @Test
    void studentWithOnlyFirstName_trimsName() {
        DiscountResponseDTO dto = service.toDisplayDto(
                discount(DiscountScope.GROUP, null, null, null, student("Bilal", null)));

        assertThat(dto.studentName()).isEqualTo("Bilal");
    }

    @Test
    void studentWithOnlyLastName_trimsName() {
        DiscountResponseDTO dto = service.toDisplayDto(
                discount(DiscountScope.GROUP, null, null, null, student(null, "Amrani")));

        assertThat(dto.studentName()).isEqualTo("Amrani");
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    @Test
    void findAllForDisplay_mapsEveryDiscount() {
        GroupEntity group = new GroupEntity();
        group.setId(31L);
        group.setName("Math 1ère B");
        when(groupRepository.findById(31L)).thenReturn(Optional.of(group));
        when(discountService.findAll()).thenReturn(List.of(
                discount(DiscountScope.GROUP, 31L, null, null, student("Bilal", "Amrani")),
                discount(DiscountScope.GROUP, 31L, null, null, student("Nourine", "Haddad"))));

        List<DiscountResponseDTO> all = service.findAllForDisplay();

        assertThat(all).hasSize(2);
        assertThat(all).allSatisfy(d -> assertThat(d.targetName()).isEqualTo("Math 1ère B"));
        assertThat(all).extracting(DiscountResponseDTO::studentName)
                .containsExactly("Bilal Amrani", "Nourine Haddad");
    }
}
