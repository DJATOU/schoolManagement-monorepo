package com.school.management.service.payment;

import com.school.management.dto.revenue.RevenueReportDTO;
import com.school.management.dto.revenue.RevenueRowDTO;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Rapport de recettes transversal : combien l'école a encaissé, ventilé par groupe, série,
 * séance ou mois d'encaissement, sur un périmètre filtrable.
 *
 * <h2>Pourquoi une agrégation dédiée</h2>
 * Le panneau de la fiche groupe calcule aussi l'<em>attendu</em>, ce qui impose une
 * résolution par étudiant et par série. À l'échelle de l'école, ce coût explose. Ce
 * rapport se limite donc à l'<em>encaissé</em>, entièrement agrégé par la base. Pour
 * confronter encaissé et attendu, on ouvre la fiche du groupe concerné.
 */
@Service
public class RevenueReportService {

    /** Axes d'agrégation acceptés. */
    public enum GroupBy {
        GROUP, SERIES, SESSION, MONTH
    }

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final PaymentDetailRepository paymentDetailRepository;
    private final RefundRepository refundRepository;

    public RevenueReportService(PaymentDetailRepository paymentDetailRepository,
            RefundRepository refundRepository) {
        this.paymentDetailRepository = paymentDetailRepository;
        this.refundRepository = refundRepository;
    }

    /**
     * Construit le rapport.
     *
     * @param groupBy      axe d'agrégation
     * @param groupId      filtre groupe (optionnel)
     * @param levelId      filtre niveau (optionnel)
     * @param seriesId     filtre série (optionnel)
     * @param schoolYearId filtre année scolaire (optionnel)
     * @param dateFrom     borne basse de la date d'encaissement (optionnelle)
     * @param dateTo       borne haute de la date d'encaissement (optionnelle, inclusive)
     * @param locale       langue des libellés de mois
     * @return le rapport agrégé
     */
    @Transactional(readOnly = true)
    public RevenueReportDTO getReport(GroupBy groupBy, Long groupId, Long levelId, Long seriesId,
            Long schoolYearId, Date dateFrom, Date dateTo, Locale locale) {
        if (groupBy == null) {
            throw new CustomServiceException("Axe d'agrégation manquant.", HttpStatus.BAD_REQUEST);
        }

        Date upperBound = endOfDay(dateTo);

        BigDecimal totalCollected = scale(toBigDecimal(paymentDetailRepository.sumRevenue(
                groupId, levelId, seriesId, schoolYearId, dateFrom, upperBound)));
        BigDecimal totalRefunded = scale(nullToZero(refundRepository.sumRefundsForReport(
                groupId, levelId, seriesId, schoolYearId, dateFrom, upperBound)));

        List<RevenueRowDTO> rows = switch (groupBy) {
            case GROUP -> mapEntityRows(paymentDetailRepository.revenueByGroup(
                    groupId, levelId, seriesId, schoolYearId, dateFrom, upperBound), totalCollected, false);
            case SERIES -> mapEntityRows(paymentDetailRepository.revenueBySeries(
                    groupId, levelId, seriesId, schoolYearId, dateFrom, upperBound), totalCollected, true);
            case SESSION -> mapEntityRows(paymentDetailRepository.revenueBySession(
                    groupId, levelId, seriesId, schoolYearId, dateFrom, upperBound), totalCollected, true);
            case MONTH -> mapMonthRows(paymentDetailRepository.revenueByMonth(
                    groupId, levelId, seriesId, schoolYearId, dateFrom, upperBound), totalCollected, locale);
        };

        return new RevenueReportDTO(
                groupBy.name(),
                totalCollected,
                totalRefunded,
                scale(totalCollected.subtract(totalRefunded)),
                rows);
    }

    // ------------------------------------------------------------------
    // Mapping des lignes
    // ------------------------------------------------------------------

    /**
     * @param withSubLabel vrai lorsque la requête renvoie un libellé secondaire en 3ᵉ colonne
     *                     (le groupe d'une série ou d'une séance)
     */
    private List<RevenueRowDTO> mapEntityRows(List<Object[]> raw, BigDecimal total, boolean withSubLabel) {
        List<RevenueRowDTO> rows = new ArrayList<>();
        for (Object[] row : raw) {
            BigDecimal collected = scale(toBigDecimal(row[withSubLabel ? 3 : 2]));
            rows.add(new RevenueRowDTO(
                    (Long) row[0],
                    (String) row[1],
                    withSubLabel ? (String) row[2] : null,
                    collected,
                    share(collected, total)));
        }
        return rows;
    }

    private List<RevenueRowDTO> mapMonthRows(List<Object[]> raw, BigDecimal total, Locale locale) {
        List<RevenueRowDTO> rows = new ArrayList<>();
        for (Object[] row : raw) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            BigDecimal collected = scale(toBigDecimal(row[2]));
            rows.add(new RevenueRowDTO(
                    null,
                    monthLabel(year, month, locale),
                    null,
                    collected,
                    share(collected, total)));
        }
        return rows;
    }

    private String monthLabel(int year, int month, Locale locale) {
        Locale resolved = locale == null ? Locale.FRENCH : locale;
        return Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, resolved) + " " + year;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Part d'une ligne dans le total, en pourcentage (0 si le total est nul). */
    private BigDecimal share(BigDecimal value, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1, MONEY_ROUNDING);
        }
        return value.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, MONEY_ROUNDING);
    }

    /** Borne haute inclusive : sans cela, « jusqu'au 20/08 » exclurait ce jour-là. */
    private Date endOfDay(Date dateTo) {
        if (dateTo == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateTo);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
