package com.school.management.service.correction;

import com.school.management.dto.payment.EncashmentDTO;
import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionDomain;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.EncashmentAllocationRepository;
import com.school.management.repository.EncashmentRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import com.school.management.service.payment.EncashmentQueryService;
import com.school.management.service.payment.EncashmentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Corrections d'un Encaissement (spec admin-corrections, exigences 2 et 3).
 *
 * <p>Chaque correction est une {@link CorrectionCommand} exécutée par le {@link CorrectionRunner} :
 * l'Aperçu et la confirmation passent par le même code. Ce service porte les <b>règles</b> d'une
 * correction demandée par l'administratrice ; la <b>mécanique</b> — neutraliser les Imputations,
 * la ventilation, les reports, recalculer les cumuls — reste dans {@link EncashmentService}.</p>
 *
 * <h2>Annulation (exigence 2)</h2>
 * Refusée si l'Encaissement est introuvable (404), déjà annulé (409, 2.5), porte sur une année
 * close (409, 2.7), ou si elle ferait passer le versé d'une Série sous ce qui y a déjà été
 * remboursé (409 nommant les remboursements, 2.4). L'Encaissement annulé reste au registre, marqué
 * avec la date, l'auteur et le Motif (2.3).
 */
@Service
public class EncashmentCorrectionService {

    /** Motifs qui justifient d'annuler un versement (D10). */
    public static final Set<CorrectionReasonType> CANCEL_REASONS = EnumSet.of(
            CorrectionReasonType.DATA_ENTRY_ERROR,
            CorrectionReasonType.WRONG_STUDENT,
            CorrectionReasonType.WRONG_AMOUNT,
            CorrectionReasonType.OTHER);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CorrectionRunner runner;
    private final EncashmentService encashmentService;
    private final EncashmentQueryService encashmentQueryService;
    private final EncashmentRepository encashmentRepository;
    private final EncashmentAllocationRepository allocationRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ReadOnlyYearGuard readOnlyYearGuard;

    public EncashmentCorrectionService(CorrectionRunner runner,
                                       EncashmentService encashmentService,
                                       EncashmentQueryService encashmentQueryService,
                                       EncashmentRepository encashmentRepository,
                                       EncashmentAllocationRepository allocationRepository,
                                       PaymentRepository paymentRepository,
                                       RefundRepository refundRepository,
                                       ReadOnlyYearGuard readOnlyYearGuard) {
        this.runner = runner;
        this.encashmentService = encashmentService;
        this.encashmentQueryService = encashmentQueryService;
        this.encashmentRepository = encashmentRepository;
        this.allocationRepository = allocationRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.readOnlyYearGuard = readOnlyYearGuard;
    }

    /**
     * Annule un Encaissement, en Aperçu ou en confirmation.
     *
     * @param encashmentId Encaissement à annuler
     * @param reason       Motif, parmi {@link #CANCEL_REASONS}
     * @param mode         Aperçu ou confirmation
     * @param previewToken jeton de l'Aperçu lu, en confirmation
     * @return l'Aperçu et, en confirmation, l'Encaissement annulé
     * @throws CustomServiceException 400 si le Motif ne convient pas ; 404, 409 selon les règles
     *                                ci-dessus ; {@link StalePreviewException} si les données ont
     *                                changé depuis l'Aperçu
     */
    public CorrectionOutcome<EncashmentDTO> cancel(Long encashmentId, CorrectionReason reason,
                                                   CorrectionMode mode, String previewToken) {
        Objects.requireNonNull(encashmentId, "encashmentId");
        Objects.requireNonNull(reason, "reason");
        if (!CANCEL_REASONS.contains(reason.type())) {
            throw new CustomServiceException("Motif « " + reason.type()
                    + " » sans rapport avec l'annulation d'un versement.", HttpStatus.BAD_REQUEST);
        }
        return runner.run(new CancelCommand(encashmentId, reason), mode, previewToken);
    }

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    /** Annulation d'un Encaissement. */
    private final class CancelCommand implements CorrectionCommand<EncashmentDTO> {

        private final Long encashmentId;
        private final CorrectionReason reason;

        private CancelCommand(Long encashmentId, CorrectionReason reason) {
            this.encashmentId = encashmentId;
            this.reason = reason;
        }

        @Override
        public String fingerprint() {
            return "ENCASHMENT_CANCEL|" + encashmentId + "|" + reason.type() + "|"
                    + (reason.text() == null ? "" : reason.text());
        }

        /**
         * Le groupe de l'Encaissement, entier : il ne crédite que des Séries de ce groupe — la Série
         * visée, qui en fait partie ({@code EncashmentService.open}), et les Séries suivantes où va
         * le report.
         */
        @Override
        public CorrectionScope scope() {
            EncashmentEntity encashment = find(encashmentRepository.findById(encashmentId).orElse(null));
            return CorrectionScope.empty().group(encashment.getStudent().getId(), encashment.getGroup().getId());
        }

        @Override
        public CorrectionExecution<EncashmentDTO> execute() {
            EncashmentEntity encashment = find(encashmentRepository.findByIdForUpdate(encashmentId).orElse(null));
            if (!encashment.isActive()) {
                throw new CustomServiceException("Le reçu " + encashment.getReceiptNumber()
                        + " est déjà annulé, le " + day(encashment.getCancelledAt())
                        + " par " + encashment.getCancelledBy() + ".", HttpStatus.CONFLICT);
            }
            assertYearOpen(encashment);

            Long studentId = encashment.getStudent().getId();
            List<EncashmentAllocationEntity> allocations = activeAllocations();
            Set<SessionSeriesEntity> credited = new LinkedHashSet<>();
            List<CorrectionEffect> effects = new ArrayList<>();
            effects.add(new CorrectionEffect(CorrectionEffectType.ENCASHMENT_CANCELLED,
                    "Reçu " + encashment.getReceiptNumber() + " de "
                            + AmountEffectWriter.money(encashment.getAmountReceived()) + " DA annulé"));
            for (EncashmentAllocationEntity allocation : allocations) {
                credited.add(allocation.getSeries());
                effects.add(new CorrectionEffect(CorrectionEffectType.ALLOCATION_NEUTRALIZED,
                        (Boolean.TRUE.equals(allocation.getCarriedOver()) ? "Report de " : "Imputation de ")
                                + AmountEffectWriter.money(allocation.getAmount()) + " DA sur « "
                                + allocation.getSeries().getName() + " » "
                                + (Boolean.TRUE.equals(allocation.getCarriedOver()) ? "neutralisé" : "neutralisée")));
            }

            encashmentService.neutralize(encashmentId, reason);
            for (SessionSeriesEntity series : credited) {
                assertAboveRefunds(studentId, series);
            }

            AuditDraft trace = AuditDraft.builder()
                    .domain(CorrectionDomain.ENCASHMENT)
                    .action(CorrectionAction.ENCASHMENT_CANCELLED)
                    .entityId(encashmentId)
                    .studentId(studentId)
                    .groupId(encashment.getGroup().getId())
                    .seriesId(encashment.getTargetSeries().getId())
                    .oldValue(Map.of("status", "ACTIVE"))
                    .newValue(Map.of("status", "CANCELLED"))
                    .summary("Reçu " + encashment.getReceiptNumber() + " de "
                            + AmountEffectWriter.money(encashment.getAmountReceived()) + " DA annulé ("
                            + encashment.getTargetSeries().getName() + ", " + encashment.getGroup().getName() + ")")
                    .reason(reason)
                    .build();

            Set<SeriesKey> touched = credited.stream()
                    .map(series -> new SeriesKey(studentId, series.getId()))
                    .collect(Collectors.toSet());
            return new CorrectionExecution<>(encashmentQueryService.get(encashmentId), effects, touched,
                    List.of(trace));
        }

        private List<EncashmentAllocationEntity> activeAllocations() {
            return allocationRepository.findByEncashmentIdAndActiveTrueOrderByIdAsc(encashmentId);
        }

        private EncashmentEntity find(EncashmentEntity encashment) {
            if (encashment == null) {
                throw new CustomServiceException("Encaissement introuvable : " + encashmentId, HttpStatus.NOT_FOUND);
            }
            return encashment;
        }
    }

    // ------------------------------------------------------------------
    // Règles communes
    // ------------------------------------------------------------------

    /** Un Encaissement d'une année close ne se corrige plus (exigence 2.7). */
    private void assertYearOpen(EncashmentEntity encashment) {
        try {
            readOnlyYearGuard.assertGroupMutable(encashment.getGroup());
        } catch (ReadOnlySchoolYearException closed) {
            throw new ReadOnlySchoolYearException("Le reçu " + encashment.getReceiptNumber()
                    + " porte sur une année scolaire close : il ne peut plus être corrigé.");
        }
    }

    /**
     * Le versé d'une Série ne passe jamais sous ce qui y a été remboursé (exigence 2.4) : vérifié
     * après la neutralisation, sur le cumul recalculé, et nommant chaque remboursement en cause.
     */
    private void assertAboveRefunds(Long studentId, SessionSeriesEntity series) {
        BigDecimal paid = money(paymentRepository.sumAmountPaidForStudentAndSeries(studentId, series.getId()));
        BigDecimal refunded = money(refundRepository.sumRefundsForStudentAndSeries(studentId, series.getId()));
        if (paid.compareTo(refunded) >= 0) {
            return;
        }
        List<RefundEntity> refunds = refundRepository.findActiveForStudentAndSeries(studentId, series.getId());
        String named = refunds.stream()
                .map(refund -> refund.getRefundNumber() + " du " + day(refund.getRefundDate()) + " ("
                        + AmountEffectWriter.money(refund.getAmount()) + " DA)")
                .collect(Collectors.joining(", "));
        throw new RefundFloorException("Correction refusée : le versé de « " + series.getName()
                + " » passerait à " + AmountEffectWriter.money(paid) + " DA, sous les "
                + AmountEffectWriter.money(refunded) + " DA déjà remboursés — "
                + (refunds.size() > 1 ? "remboursements " : "remboursement ") + named + ".",
                refunds.stream()
                        .map(refund -> new RefundFloorException.BlockingRefund(refund.getRefundNumber(),
                                refund.getRefundDate(), refund.getAmount(), series.getName()))
                        .toList());
    }

    /** Les sommes du dépôt sont des {@code COALESCE(…, 0)} : jamais nulles. */
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Date d'annulation ou de remboursement, toujours fixée par le serveur à l'écriture. */
    private static String day(Date date) {
        return DAY.format(date.toInstant().atZone(ZoneId.systemDefault()));
    }
}
