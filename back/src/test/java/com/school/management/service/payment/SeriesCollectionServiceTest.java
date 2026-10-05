package com.school.management.service.payment;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.payment.SeriesCollectionService.SeriesCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Encaissé net d'une série : versé au registre moins remboursé, source unique du relevé et de la paie. */
class SeriesCollectionServiceTest {

    private static final long GROUP_ID = 3L;

    private PaymentRepository paymentRepository;
    private RefundRepository refundRepository;
    private SeriesCollectionService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        refundRepository = mock(RefundRepository.class);
        service = new SeriesCollectionService(paymentRepository, refundRepository);
        when(paymentRepository.sumPaidByGroupGroupedBySeries(anyLong())).thenReturn(List.of());
        when(refundRepository.sumRefundsByGroupGroupedBySeries(anyLong())).thenReturn(List.of());
    }

    private static List<Object[]> rows(Object[]... rows) {
        return new ArrayList<>(List.of(rows));
    }

    private static SessionSeriesEntity series(long id, Long groupId) {
        GroupEntity group = groupId == null ? null : GroupEntity.builder().id(groupId).build();
        return SessionSeriesEntity.builder().id(id).group(group).build();
    }

    @Test
    @DisplayName("versé moins remboursé, série par série, à deux décimales")
    void grossMinusRefundedPerSeries() {
        when(paymentRepository.sumPaidByGroupGroupedBySeries(GROUP_ID))
                .thenReturn(rows(new Object[]{10L, 74000.0}, new Object[]{11L, 2400.5}));
        when(refundRepository.sumRefundsByGroupGroupedBySeries(GROUP_ID))
                .thenReturn(rows(new Object[]{10L, new BigDecimal("2000")}));

        Map<Long, SeriesCollection> collections = service.ofGroup(GROUP_ID);

        assertThat(collections.get(10L)).isEqualTo(new SeriesCollection(
                new BigDecimal("74000.00"), new BigDecimal("2000.00"), new BigDecimal("72000.00")));
        assertThat(collections.get(11L)).isEqualTo(new SeriesCollection(
                new BigDecimal("2400.50"), new BigDecimal("0.00"), new BigDecimal("2400.50")));
    }

    @Test
    @DisplayName("une série qui n'a que des remboursements apparaît, nette négative")
    void refundOnlySeriesIsListed() {
        when(refundRepository.sumRefundsByGroupGroupedBySeries(GROUP_ID))
                .thenReturn(rows(new Object[]{12L, new BigDecimal("300.00")}));

        assertThat(service.ofGroup(GROUP_ID).get(12L).net()).isEqualByComparingTo("-300.00");
    }

    @Test
    @DisplayName("une série : celle de son groupe, ou rien d'encaissé")
    void singleSeries() {
        when(paymentRepository.sumPaidByGroupGroupedBySeries(GROUP_ID))
                .thenReturn(rows(new Object[]{10L, 1000.0}));

        assertThat(service.of(series(10L, GROUP_ID)).net()).isEqualByComparingTo("1000.00");
        assertThat(service.of(series(99L, GROUP_ID))).isEqualTo(SeriesCollection.none());
    }

    @Test
    @DisplayName("série sans groupe : rien d'encaissé, sans requête")
    void seriesWithoutGroup() {
        assertThat(service.of(series(10L, null))).isEqualTo(SeriesCollection.none());
        verifyNoInteractions(paymentRepository, refundRepository);
    }

    @Test
    @DisplayName("rien d'encaissé : zéro à deux décimales, pas un nul")
    void noneIsZero() {
        assertThat(SeriesCollection.none().net()).isEqualTo(new BigDecimal("0.00"));
        assertThat(service.ofGroup(GROUP_ID)).isEmpty();
    }
}
