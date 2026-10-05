package com.school.management.service.correction;

import com.school.management.dto.payment.EncashmentDTO;
import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionDomain;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.EncashmentKind;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.EncashmentAllocationRepository;
import com.school.management.repository.EncashmentRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import com.school.management.service.payment.EncashmentQueryService;
import com.school.management.service.payment.EncashmentService;
import com.school.management.service.payment.PaymentAllocationResult;
import com.school.management.service.payment.PaymentProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Corrections d'un Encaissement (spec admin-corrections, exigences 2 et 3).
 *
 * <p>Chaque correction est une {@link CorrectionCommand} exécutée par le {@link CorrectionRunner} :
 * l'Aperçu et la confirmation passent par le même code. Ce service porte les <b>règles</b> d'une
 * correction demandée par l'administratrice ; la <b>mécanique</b> — neutraliser les Imputations,
 * la ventilation, les reports, recalculer les cumuls — reste dans {@link EncashmentService}, et
 * l'encaissement d'un remplacement passe par le chemin ordinaire, {@link PaymentProcessingService}.</p>
 *
 * <h2>Annulation (exigence 2)</h2>
 * Refusée si l'Encaissement est introuvable (404), déjà annulé (409, 2.5), porte sur une année
 * close (409, 2.7), ou si elle ferait passer le versé d'une Série sous ce qui y a déjà été
 * remboursé (409 nommant les remboursements, 2.4). L'Encaissement annulé reste au registre, marqué
 * avec la date, l'auteur et le Motif (2.3).
 *
 * <h2>Correction (exigence 3, D4)</h2>
 * L'administratrice décrit l'Encaissement tel qu'il aurait dû être saisi. Si le montant, l'étudiant,
 * le groupe ou la Série changent, l'original est annulé et le remplacement encaissé par le chemin
 * ordinaire — plafond, report, refus en totalité, année close —, dans la même transaction : un
 * remplacement refusé n'annule rien (3.5). Les deux Encaissements sont reliés dans les deux sens
 * (3.3). Si seuls le mode de paiement ou la note changent, ils sont corrigés en place, sans nouveau
 * reçu (3.6). Sans aucun changement, la correction est refusée (11.3).
 */
@Service
public class EncashmentCorrectionService {

    /** Motifs qui justifient d'annuler ou de corriger un versement (D10). */
    public static final Set<CorrectionReasonType> ENCASHMENT_REASONS = EnumSet.of(
            CorrectionReasonType.DATA_ENTRY_ERROR,
            CorrectionReasonType.WRONG_STUDENT,
            CorrectionReasonType.WRONG_AMOUNT,
            CorrectionReasonType.OTHER);

    /** Libellés des modes de paiement enregistrés par l'écran d'encaissement, pour la Trace. */
    private static final Map<String, String> METHOD_LABELS = Map.of(
            "cash", "espèces",
            "cheque", "chèque",
            "carte_bancaire", "carte bancaire",
            "autre", "autre");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CorrectionRunner runner;
    private final EncashmentService encashmentService;
    private final EncashmentQueryService encashmentQueryService;
    private final PaymentProcessingService processing;
    private final EncashmentRepository encashmentRepository;
    private final EncashmentAllocationRepository allocationRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ReadOnlyYearGuard readOnlyYearGuard;

    public EncashmentCorrectionService(CorrectionRunner runner,
                                       EncashmentService encashmentService,
                                       EncashmentQueryService encashmentQueryService,
                                       PaymentProcessingService processing,
                                       EncashmentRepository encashmentRepository,
                                       EncashmentAllocationRepository allocationRepository,
                                       PaymentRepository paymentRepository,
                                       RefundRepository refundRepository,
                                       ReadOnlyYearGuard readOnlyYearGuard) {
        this.runner = runner;
        this.encashmentService = encashmentService;
        this.encashmentQueryService = encashmentQueryService;
        this.processing = processing;
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
     * @param reason       Motif, parmi {@link #ENCASHMENT_REASONS}
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
        requireEncashmentReason(reason);
        return runner.run(new CancelCommand(encashmentId, reason), mode, previewToken);
    }

    /**
     * Corrige un Encaissement, en Aperçu ou en confirmation.
     *
     * @param encashmentId Encaissement à corriger
     * @param changes      l'Encaissement tel qu'il aurait dû être saisi
     * @param reason       Motif, parmi {@link #ENCASHMENT_REASONS}
     * @param mode         Aperçu ou confirmation
     * @param previewToken jeton de l'Aperçu lu, en confirmation
     * @return l'Aperçu et, en confirmation, l'original et son éventuel remplacement
     * @throws CustomServiceException 400 si le Motif ne convient pas, si rien ne change, ou si le
     *                                remplacement est refusé par les règles d'encaissement ; 404,
     *                                409 comme pour l'annulation
     */
    public CorrectionOutcome<EncashmentCorrection> correct(Long encashmentId, EncashmentChanges changes,
                                                           CorrectionReason reason, CorrectionMode mode,
                                                           String previewToken) {
        Objects.requireNonNull(encashmentId, "encashmentId");
        Objects.requireNonNull(changes, "changes");
        requireEncashmentReason(reason);
        return runner.run(new CorrectCommand(encashmentId, changes, reason), mode, previewToken);
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
            return canonical("ENCASHMENT_CANCEL", encashmentId, reason.type(), reason.text());
        }

        /**
         * Le groupe de l'Encaissement, entier : il ne crédite que des Séries de ce groupe — la Série
         * visée, qui en fait partie ({@code EncashmentService.open}), et les Séries suivantes où va
         * le report.
         */
        @Override
        public CorrectionScope scope() {
            EncashmentEntity encashment = existing(encashmentId);
            return CorrectionScope.empty().group(encashment.getStudent().getId(), encashment.getGroup().getId());
        }

        @Override
        public CorrectionExecution<EncashmentDTO> execute() {
            EncashmentEntity encashment = lockActive(encashmentId);
            Long studentId = encashment.getStudent().getId();
            List<EncashmentAllocationEntity> allocations = activeAllocations(encashmentId);
            List<CorrectionEffect> effects = cancellationEffects(encashment, allocations);

            encashmentService.neutralize(encashmentId, reason);
            Set<SessionSeriesEntity> credited = creditedSeries(allocations);
            credited.forEach(series -> assertAboveRefunds(studentId, series));

            AuditDraft trace = AuditDraft.builder()
                    .domain(CorrectionDomain.ENCASHMENT)
                    .action(CorrectionAction.ENCASHMENT_CANCELLED)
                    .entityId(encashmentId)
                    .studentId(studentId)
                    .groupId(encashment.getGroup().getId())
                    .seriesId(encashment.getTargetSeries().getId())
                    .oldValue(Map.of("status", "ACTIVE"))
                    .newValue(Map.of("status", "CANCELLED"))
                    .summary(describe(encashment) + " annulé (" + encashment.getTargetSeries().getName() + ", "
                            + encashment.getGroup().getName() + ")")
                    .reason(reason)
                    .build();

            return new CorrectionExecution<>(encashmentQueryService.get(encashmentId), effects,
                    keys(studentId, credited), List.of(trace));
        }
    }

    // ------------------------------------------------------------------
    // Correction
    // ------------------------------------------------------------------

    /** Correction d'un Encaissement : Remplacement, ou mode et note corrigés en place. */
    private final class CorrectCommand implements CorrectionCommand<EncashmentCorrection> {

        private final Long encashmentId;
        private final EncashmentChanges changes;
        private final CorrectionReason reason;

        private CorrectCommand(Long encashmentId, EncashmentChanges changes, CorrectionReason reason) {
            this.encashmentId = encashmentId;
            this.changes = changes;
            this.reason = reason;
        }

        @Override
        public String fingerprint() {
            return canonical("ENCASHMENT_CORRECT", encashmentId, changes.amount().toPlainString(),
                    changes.studentId(), changes.groupId(), changes.targetSeriesId(),
                    changes.paymentMethod(), changes.notes(), reason.type(), reason.text());
        }

        /** Le groupe de l'original, pour son étudiant, et celui du remplacement, pour le sien. */
        @Override
        public CorrectionScope scope() {
            EncashmentEntity original = existing(encashmentId);
            return CorrectionScope.empty()
                    .group(original.getStudent().getId(), original.getGroup().getId())
                    .group(changes.studentId(), changes.groupId());
        }

        @Override
        public CorrectionExecution<EncashmentCorrection> execute() {
            EncashmentEntity original = lockActive(encashmentId);
            String method = EncashmentService.normalizedPaymentMethod(changes.paymentMethod());
            String notes = EncashmentService.normalizedNotes(changes.notes());

            boolean moneyChanged = original.getAmountReceived().compareTo(changes.amount()) != 0
                    || !original.getStudent().getId().equals(changes.studentId())
                    || !original.getGroup().getId().equals(changes.groupId())
                    || !original.getTargetSeries().getId().equals(changes.targetSeriesId());
            boolean detailsChanged = !Objects.equals(method, original.getPaymentMethod())
                    || !Objects.equals(notes, original.getNotes());

            if (moneyChanged) {
                return replace(original, method, notes);
            }
            if (detailsChanged) {
                return editDetails(original, method, notes);
            }
            throw new CustomServiceException(
                    "Correction sans changement effectif : rien à enregistrer.", HttpStatus.BAD_REQUEST);
        }

        /** Remplacement : annuler l'original, encaisser le remplacement par le chemin ordinaire, relier. */
        private CorrectionExecution<EncashmentCorrection> replace(EncashmentEntity original, String method,
                                                                  String notes) {
            if (original.getKind() == EncashmentKind.CATCH_UP) {
                throw new CustomServiceException("Le reçu " + original.getReceiptNumber()
                        + " paie une séance de rattrapage : il ne se remplace pas. Annulez-le, puis encaissez "
                        + "de nouveau le rattrapage.", HttpStatus.BAD_REQUEST);
            }
            Long originalStudentId = original.getStudent().getId();
            List<EncashmentAllocationEntity> originalAllocations = activeAllocations(encashmentId);
            List<CorrectionEffect> effects = cancellationEffects(original, originalAllocations);
            Map<String, Object> before = values(original);

            encashmentService.neutralize(encashmentId, reason);
            PaymentAllocationResult result = processing.processPayment(changes.studentId(), changes.groupId(),
                    changes.targetSeriesId(), changes.amount().doubleValue(), null,
                    new PaymentProcessingService.PaymentMeans(method, notes));
            EncashmentEntity replacement = result.encashment();
            original.setReplacedBy(replacement);
            replacement.setReplaces(original);
            encashmentRepository.save(original);
            encashmentRepository.save(replacement);

            List<EncashmentAllocationEntity> replacementAllocations = activeAllocations(replacement.getId());
            effects.add(new CorrectionEffect(CorrectionEffectType.ENCASHMENT_CREATED,
                    "Versement de remplacement de " + AmountEffectWriter.money(replacement.getAmountReceived())
                            + " DA sur « " + replacement.getTargetSeries().getName() + " » ("
                            + replacement.getGroup().getName() + "), au nom de " + fullName(replacement.getStudent())));
            for (EncashmentAllocationEntity allocation : replacementAllocations) {
                effects.add(new CorrectionEffect(CorrectionEffectType.ALLOCATION_CREATED,
                        (carried(allocation) ? "Report de " : "Imputation de ")
                                + AmountEffectWriter.money(allocation.getAmount()) + " DA sur « "
                                + allocation.getSeries().getName() + " »"));
            }

            // Le plancher se juge sur l'état final : un remplacement sur la même Série peut rendre
            // ce que l'annulation retirait.
            Set<SessionSeriesEntity> credited = creditedSeries(originalAllocations);
            credited.forEach(series -> assertAboveRefunds(originalStudentId, series));

            Set<SeriesKey> touched = new HashSet<>(keys(originalStudentId, credited));
            touched.addAll(keys(changes.studentId(), creditedSeries(replacementAllocations)));

            List<AuditDraft> traces = new ArrayList<>();
            boolean otherStudent = !originalStudentId.equals(changes.studentId());
            traces.add(AuditDraft.builder()
                    .domain(CorrectionDomain.ENCASHMENT)
                    .action(CorrectionAction.ENCASHMENT_REPLACED)
                    .entityId(encashmentId)
                    .studentId(originalStudentId)
                    .groupId(original.getGroup().getId())
                    .seriesId(original.getTargetSeries().getId())
                    .oldValue(before)
                    .newValue(values(replacement))
                    .summary(describe(original) + " annulé, remplacé par " + replacement.getReceiptNumber()
                            + " de " + AmountEffectWriter.money(replacement.getAmountReceived()) + " DA"
                            + differences(original, replacement))
                    .reason(reason)
                    .build());
            // L'argent change d'élève : le Journal du nouvel élève doit aussi le montrer.
            if (otherStudent) {
                traces.add(AuditDraft.builder()
                        .domain(CorrectionDomain.ENCASHMENT)
                        .action(CorrectionAction.ENCASHMENT_REPLACED)
                        .entityId(replacement.getId())
                        .studentId(changes.studentId())
                        .groupId(changes.groupId())
                        .seriesId(changes.targetSeriesId())
                        .newValue(values(replacement))
                        .summary(describe(replacement) + ", en remplacement du reçu " + original.getReceiptNumber()
                                + " saisi au nom de " + fullName(original.getStudent()))
                        .reason(reason)
                        .build());
            }

            return new CorrectionExecution<>(
                    new EncashmentCorrection(encashmentQueryService.get(encashmentId),
                            encashmentQueryService.get(replacement.getId())),
                    effects, touched, traces);
        }

        /** Mode de paiement et note corrigés en place : aucun montant, aucun nouveau reçu (3.6). */
        private CorrectionExecution<EncashmentCorrection> editDetails(EncashmentEntity original, String method,
                                                                      String notes) {
            Map<String, Object> before = details(original.getPaymentMethod(), original.getNotes());
            List<String> changed = new ArrayList<>();
            if (!Objects.equals(method, original.getPaymentMethod())) {
                changed.add("mode de paiement " + methodLabel(original.getPaymentMethod()) + " → " + methodLabel(method));
            }
            if (!Objects.equals(notes, original.getNotes())) {
                changed.add("note " + noteLabel(original.getNotes()) + " → " + noteLabel(notes));
            }
            String what = String.join(", ", changed);

            encashmentService.editDetails(original, method, notes);

            AuditDraft trace = AuditDraft.builder()
                    .domain(CorrectionDomain.ENCASHMENT)
                    .action(CorrectionAction.ENCASHMENT_DETAILS_EDITED)
                    .entityId(encashmentId)
                    .studentId(original.getStudent().getId())
                    .groupId(original.getGroup().getId())
                    .seriesId(original.getTargetSeries().getId())
                    .oldValue(before)
                    .newValue(details(method, notes))
                    .summary("Reçu " + original.getReceiptNumber() + " : " + what)
                    .reason(reason)
                    .build();
            return new CorrectionExecution<>(new EncashmentCorrection(encashmentQueryService.get(encashmentId), null),
                    List.of(new CorrectionEffect(CorrectionEffectType.ENCASHMENT_DETAILS_EDITED,
                            "Reçu " + original.getReceiptNumber() + " : " + what)),
                    Set.of(), List.of(trace));
        }
    }

    // ------------------------------------------------------------------
    // Règles communes
    // ------------------------------------------------------------------

    private static void requireEncashmentReason(CorrectionReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (!ENCASHMENT_REASONS.contains(reason.type())) {
            throw new CustomServiceException("Motif « " + reason.type()
                    + " » sans rapport avec la correction d'un versement.", HttpStatus.BAD_REQUEST);
        }
    }

    private EncashmentEntity existing(Long encashmentId) {
        return found(encashmentId, encashmentRepository.findById(encashmentId).orElse(null));
    }

    /**
     * L'Encaissement, verrouillé, actif, d'une année ouverte : seul un tel Encaissement se corrige
     * (exigences 2.5, 2.7).
     */
    private EncashmentEntity lockActive(Long encashmentId) {
        EncashmentEntity encashment = found(encashmentId,
                encashmentRepository.findByIdForUpdate(encashmentId).orElse(null));
        if (!encashment.isActive()) {
            throw new CustomServiceException("Le reçu " + encashment.getReceiptNumber()
                    + " est déjà annulé, le " + day(encashment.getCancelledAt())
                    + " par " + encashment.getCancelledBy()
                    + (encashment.getReplacedBy() == null ? ""
                            : ", remplacé par " + encashment.getReplacedBy().getReceiptNumber()) + ".",
                    HttpStatus.CONFLICT);
        }
        try {
            readOnlyYearGuard.assertGroupMutable(encashment.getGroup());
        } catch (ReadOnlySchoolYearException closed) {
            throw new ReadOnlySchoolYearException("Le reçu " + encashment.getReceiptNumber()
                    + " porte sur une année scolaire close : il ne peut plus être corrigé.");
        }
        return encashment;
    }

    private static EncashmentEntity found(Long encashmentId, EncashmentEntity encashment) {
        if (encashment == null) {
            throw new CustomServiceException("Encaissement introuvable : " + encashmentId, HttpStatus.NOT_FOUND);
        }
        return encashment;
    }

    private List<EncashmentAllocationEntity> activeAllocations(Long encashmentId) {
        return allocationRepository.findByEncashmentIdAndActiveTrueOrderByIdAsc(encashmentId);
    }

    /** Effets d'une annulation : l'Encaissement, puis chacune de ses parts. */
    private static List<CorrectionEffect> cancellationEffects(EncashmentEntity encashment,
                                                              List<EncashmentAllocationEntity> allocations) {
        List<CorrectionEffect> effects = new ArrayList<>();
        effects.add(new CorrectionEffect(CorrectionEffectType.ENCASHMENT_CANCELLED, describe(encashment) + " annulé"));
        for (EncashmentAllocationEntity allocation : allocations) {
            effects.add(new CorrectionEffect(CorrectionEffectType.ALLOCATION_NEUTRALIZED,
                    (carried(allocation) ? "Report de " : "Imputation de ")
                            + AmountEffectWriter.money(allocation.getAmount()) + " DA sur « "
                            + allocation.getSeries().getName() + " » "
                            + (carried(allocation) ? "neutralisé" : "neutralisée")));
        }
        return effects;
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

    // ------------------------------------------------------------------
    // Rédaction
    // ------------------------------------------------------------------

    /** « Reçu RECU-2030-0001 de 3 000,00 DA ». */
    private static String describe(EncashmentEntity encashment) {
        return "Reçu " + encashment.getReceiptNumber() + " de "
                + AmountEffectWriter.money(encashment.getAmountReceived()) + " DA";
    }

    /** Ce qui change entre l'original et son remplacement, hors montant déjà énoncé. */
    private static String differences(EncashmentEntity original, EncashmentEntity replacement) {
        List<String> changed = new ArrayList<>();
        if (!original.getStudent().getId().equals(replacement.getStudent().getId())) {
            changed.add("élève " + fullName(original.getStudent()) + " → " + fullName(replacement.getStudent()));
        }
        if (!original.getGroup().getId().equals(replacement.getGroup().getId())) {
            changed.add("groupe « " + original.getGroup().getName() + " » → « " + replacement.getGroup().getName() + " »");
        }
        if (!original.getTargetSeries().getId().equals(replacement.getTargetSeries().getId())) {
            changed.add("série « " + original.getTargetSeries().getName() + " » → « "
                    + replacement.getTargetSeries().getName() + " »");
        }
        return changed.isEmpty() ? "" : " (" + String.join(", ", changed) + ")";
    }

    /** Valeurs structurées d'un Encaissement, pour la Trace. */
    private static Map<String, Object> values(EncashmentEntity encashment) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("receiptNumber", encashment.getReceiptNumber());
        values.put("amount", encashment.getAmountReceived());
        values.put("studentId", encashment.getStudent().getId());
        values.put("groupId", encashment.getGroup().getId());
        values.put("seriesId", encashment.getTargetSeries().getId());
        values.putAll(details(encashment.getPaymentMethod(), encashment.getNotes()));
        return values;
    }

    private static Map<String, Object> details(String method, String notes) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paymentMethod", method);
        details.put("notes", notes);
        return details;
    }

    private static String methodLabel(String method) {
        return method == null ? "aucun" : METHOD_LABELS.getOrDefault(method, method);
    }

    private static String noteLabel(String note) {
        return note == null ? "aucune" : "« " + note + " »";
    }

    private static String fullName(StudentEntity student) {
        return Stream.of(student.getFirstName(), student.getLastName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    private static boolean carried(EncashmentAllocationEntity allocation) {
        return Boolean.TRUE.equals(allocation.getCarriedOver());
    }

    /** Séries créditées, dans l'ordre des parts, sans doublon. */
    private static Set<SessionSeriesEntity> creditedSeries(List<EncashmentAllocationEntity> allocations) {
        Set<SessionSeriesEntity> series = new LinkedHashSet<>();
        allocations.forEach(allocation -> series.add(allocation.getSeries()));
        return series;
    }

    private static Set<SeriesKey> keys(Long studentId, Set<SessionSeriesEntity> series) {
        return series.stream().map(s -> new SeriesKey(studentId, s.getId())).collect(Collectors.toSet());
    }

    /**
     * Description canonique d'une commande : chaque paramètre préfixé de sa longueur, l'absence
     * notée à part. Une note contenant un séparateur ne peut pas se faire passer pour deux champs.
     */
    static String canonical(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            if (part == null) {
                canonical.append("~|");
            } else {
                String text = part.toString();
                canonical.append(text.length()).append(':').append(text).append('|');
            }
        }
        return canonical.toString();
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
