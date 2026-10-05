package com.school.management.service.payment;

import com.school.management.dto.revenue.GroupRevenueDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de la balance d'un groupe : reste à recouvrer et trop-perçu.
 *
 * <p>Régression : le reste à recouvrer était déduit du solde global de la série
 * ({@code attendu − encaissé}, borné à zéro). Un étudiant qui verse plus que son dû
 * compensait donc le retard d'un autre, et la série apparaissait soldée. Les deux montants
 * sont désormais agrégés étudiant par étudiant.</p>
 */
class GroupRevenueBalanceTest {

    private static final long GROUP_ID = 1L;
    private static final long SERIES_ID = 10L;
    private static final long OVERPAYER_ID = 100L;
    private static final long LATE_STUDENT_ID = 200L;

    private PaymentCostResolver paymentCostResolver;
    private StudentGroupRepository studentGroupRepository;
    private PaymentRepository paymentRepository;
    private RefundRepository refundRepository;
    private SeriesCollectionService seriesCollectionService;
    private SessionSeriesEntity series;
    private GroupRevenueService service;

    @BeforeEach
    void setUp() {
        GroupRepository groupRepository = mock(GroupRepository.class);
        SessionSeriesRepository sessionSeriesRepository = mock(SessionSeriesRepository.class);
        studentGroupRepository = mock(StudentGroupRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        PaymentDetailRepository paymentDetailRepository = mock(PaymentDetailRepository.class);
        refundRepository = mock(RefundRepository.class);
        paymentCostResolver = mock(PaymentCostResolver.class);
        seriesCollectionService = new SeriesCollectionService(paymentRepository, refundRepository);

        service = new GroupRevenueService(groupRepository, sessionSeriesRepository,
                studentGroupRepository, paymentRepository, paymentDetailRepository, refundRepository,
                paymentCostResolver, seriesCollectionService);

        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("Math 1ère A");
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        series.setName("Septembre 2025");
        series.setGroup(group);
        when(sessionSeriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(series));

        when(studentGroupRepository.findByGroupId(GROUP_ID))
                .thenReturn(List.of(enrollment(OVERPAYER_ID), enrollment(LATE_STUDENT_ID)));

        when(paymentDetailRepository.sumCollectedByGroupGroupedBySeries(GROUP_ID)).thenReturn(List.of());
        when(paymentDetailRepository.sumCollectedByGroupGroupedBySession(GROUP_ID)).thenReturn(List.of());
        when(paymentDetailRepository.sumCollectedByGroupGroupedByMonth(GROUP_ID)).thenReturn(List.of());
        when(paymentDetailRepository.sumCollectedForGroup(GROUP_ID)).thenReturn(0.0);
        when(paymentRepository.sumPaidByGroupGroupedBySeries(GROUP_ID)).thenReturn(List.of());
        when(paymentRepository.sumPaidForGroup(GROUP_ID)).thenReturn(BigDecimal.ZERO);
        when(refundRepository.sumRefundsByGroupGroupedBySeries(GROUP_ID)).thenReturn(List.of());
        when(refundRepository.sumRefundsForGroup(GROUP_ID)).thenReturn(BigDecimal.ZERO);
    }

    private StudentGroupEntity enrollment(Long studentId) {
        StudentEntity student = new StudentEntity();
        student.setId(studentId);
        StudentGroupEntity enrollment = new StudentGroupEntity();
        enrollment.setStudent(student);
        enrollment.setActive(true);
        return enrollment;
    }

    private void givenStatus(Long studentId, String cost, String paid) {
        when(paymentCostResolver.resolve(studentId, SERIES_ID)).thenReturn(
                new PaymentCostResolver.PaymentStatusResult(
                        new BigDecimal(cost), new BigDecimal(cost), new BigDecimal(paid), false, false));
    }

    @Test
    void getGroupRevenue_overpayerDoesNotCompensateLateStudent() {
        // Un étudiant verse 3000 pour un dû de 2100 (+900), l'autre ne verse rien sur 2800.
        givenStatus(OVERPAYER_ID, "2100.00", "3000.00");
        givenStatus(LATE_STUDENT_ID, "2800.00", "0.00");

        GroupRevenueDTO dto = service.getGroupRevenue(GROUP_ID);

        assertThat(dto.expected()).isEqualByComparingTo("4900.00");
        // 2800 restent réellement à recouvrer : le trop-perçu de l'un ne les efface pas.
        assertThat(dto.remaining()).isEqualByComparingTo("2800.00");
        assertThat(dto.overpaid()).isEqualByComparingTo("900.00");
        assertThat(dto.series()).singleElement().satisfies(series -> {
            assertThat(series.remaining()).isEqualByComparingTo("2800.00");
            assertThat(series.overpaid()).isEqualByComparingTo("900.00");
        });
    }

    @Test
    void getGroupRevenue_departedAndReturnedStudentsCountOnce() {
        // C.5 : l'étudiant parti doit encore sa fenêtre, et son trop-perçu reste au relevé ;
        // l'étudiant revenu a deux inscriptions, mais une seule situation par série.
        StudentGroupEntity departed = enrollment(OVERPAYER_ID);
        departed.setActive(false);
        when(studentGroupRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(
                departed, enrollment(LATE_STUDENT_ID), enrollment(LATE_STUDENT_ID)));
        givenStatus(OVERPAYER_ID, "2100.00", "3000.00");
        givenStatus(LATE_STUDENT_ID, "2800.00", "0.00");

        GroupRevenueDTO dto = service.getGroupRevenue(GROUP_ID);

        assertThat(dto.expected()).isEqualByComparingTo("4900.00");
        assertThat(dto.remaining()).isEqualByComparingTo("2800.00");
        assertThat(dto.overpaid()).isEqualByComparingTo("900.00");
    }

    @Test
    void getGroupRevenue_everyoneSettled_noRemainingNoOverpaid() {
        givenStatus(OVERPAYER_ID, "2100.00", "2100.00");
        givenStatus(LATE_STUDENT_ID, "2800.00", "2800.00");

        GroupRevenueDTO dto = service.getGroupRevenue(GROUP_ID);

        assertThat(dto.remaining()).isEqualByComparingTo("0.00");
        assertThat(dto.overpaid()).isEqualByComparingTo("0.00");
    }

    @Test
    void getGroupRevenue_unresolvableStudent_isSkippedWithoutFailing() {
        givenStatus(OVERPAYER_ID, "2100.00", "2100.00");
        when(paymentCostResolver.resolve(LATE_STUDENT_ID, SERIES_ID))
                .thenThrow(new IllegalStateException("tarif absent"));

        GroupRevenueDTO dto = service.getGroupRevenue(GROUP_ID);

        assertThat(dto.expected()).isEqualByComparingTo("2100.00");
        assertThat(dto.remaining()).isEqualByComparingTo("0.00");
    }

    @Test
    void getGroupRevenue_seriesCollectedIsTheSharedSource_P3() {
        // Spec teacher-payroll, P3 : la paie de l'enseignant et le relevé du groupe lisent le même
        // encaissé net. 74 000 versés, 2 000 rendus : 72 000 des deux côtés.
        givenStatus(OVERPAYER_ID, "0.00", "0.00");
        givenStatus(LATE_STUDENT_ID, "0.00", "0.00");
        when(paymentRepository.sumPaidByGroupGroupedBySeries(GROUP_ID))
                .thenReturn(new java.util.ArrayList<>(List.<Object[]>of(new Object[]{SERIES_ID, 74000.0})));
        when(refundRepository.sumRefundsByGroupGroupedBySeries(GROUP_ID))
                .thenReturn(new java.util.ArrayList<>(List.<Object[]>of(new Object[]{SERIES_ID, new BigDecimal("2000")})));

        GroupRevenueDTO dto = service.getGroupRevenue(GROUP_ID);

        assertThat(dto.series()).singleElement().satisfies(line -> {
            assertThat(line.collected()).isEqualByComparingTo("72000.00");
            assertThat(line.refunded()).isEqualByComparingTo("2000.00");
            assertThat(line.collected()).isEqualByComparingTo(seriesCollectionService.of(series).net());
        });
    }

    @Test
    void getGroupRevenue_unknownGroup_throws() {
        GroupRepository emptyRepository = mock(GroupRepository.class);
        when(emptyRepository.findById(anyLong())).thenReturn(Optional.empty());
        GroupRevenueService isolated = new GroupRevenueService(emptyRepository,
                mock(SessionSeriesRepository.class), mock(StudentGroupRepository.class),
                mock(PaymentRepository.class), mock(PaymentDetailRepository.class), mock(RefundRepository.class),
                mock(PaymentCostResolver.class), mock(SeriesCollectionService.class));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> isolated.getGroupRevenue(99L)))
                .isNotNull();
    }
}
