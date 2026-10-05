package com.school.management.service.correction;

import com.school.management.dto.payroll.PayoutDTO;
import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionDomain;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.PayoutKind;
import com.school.management.persistance.PayoutStatus;
import com.school.management.persistance.TeacherPayRateEntity;
import com.school.management.persistance.TeacherPayoutEntity;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.TeacherPayoutRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payroll.TeacherPayRateService;
import com.school.management.service.payroll.TeacherPayoutService;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Corriger une paie d'enseignant : l'annuler, ou remplacer une paie initiale par une paie à un autre
 * taux (spec teacher-payroll, exigence 7, D3, D7).
 *
 * <h2>Par le moteur des corrections</h2>
 * Chaque correction est une {@link CorrectionCommand} exécutée par le {@link CorrectionRunner} :
 * l'Aperçu et la confirmation passent par le même code, et la trace s'écrit dans la même transaction.
 * La portée est vide, aucun montant d'élève ne change ; les effets énoncent ce que la série a versé
 * à l'enseignant et gardé pour l'école, avant et après. C'est ce qui rend l'Aperçu périmable : une
 * paie enregistrée ou corrigée entre-temps sur la série change ces deux cumuls.
 *
 * <h2>La plus récente seulement (D3)</h2>
 * Seule la paie active la plus récente d'une série se corrige. Annuler une paie initiale suivie d'une
 * régularisation laisserait une régularisation calculée sur une base qui n'existe plus. La série est
 * verrouillée avant toute lecture : une paie ou une autre correction de la même série attend.
 *
 * <h2>Ce qu'un remplacement garde</h2>
 * L'enseignant, le groupe et la série de l'originale ; le taux change, l'encaissé est relu. Payer un
 * autre enseignant n'est pas un remplacement : on annule, puis on paie. Une régularisation ne se
 * remplace pas, elle se calcule : on l'annule, et l'écart réapparaît dans « À payer ».
 *
 * <p>Pas de garde d'année close : corriger la paie de la dernière série d'une année close est voulu
 * (exigence 10.1).</p>
 */
@Service
public class PayoutCorrectionService {

    /** Motifs qui justifient de corriger une paie. */
    public static final Set<CorrectionReasonType> PAYOUT_REASONS = EnumSet.of(
            CorrectionReasonType.DATA_ENTRY_ERROR,
            CorrectionReasonType.WRONG_AMOUNT,
            CorrectionReasonType.OTHER);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SYSTEM = "system";

    private final CorrectionRunner runner;
    private final TeacherPayoutRepository payoutRepository;
    private final SessionSeriesRepository seriesRepository;
    private final TeacherPayoutService payoutService;
    private final TeacherPayRateService rateService;
    private final AuditorAware<String> auditorAware;

    public PayoutCorrectionService(CorrectionRunner runner,
                                   TeacherPayoutRepository payoutRepository,
                                   SessionSeriesRepository seriesRepository,
                                   TeacherPayoutService payoutService,
                                   TeacherPayRateService rateService,
                                   AuditorAware<String> auditorAware) {
        this.runner = runner;
        this.payoutRepository = payoutRepository;
        this.seriesRepository = seriesRepository;
        this.payoutService = payoutService;
        this.rateService = rateService;
        this.auditorAware = auditorAware;
    }

    /**
     * Annule une paie, en Aperçu ou en confirmation (exigence 7.1). Une paie initiale annulée sans
     * remplacement rend la série de nouveau « à payer » (7.5).
     *
     * @throws CustomServiceException 400 si le Motif ne convient pas ; 404 si la paie est
     *                                introuvable ; 409 si elle est déjà annulée ou n'est pas la plus
     *                                récente de sa série ; {@link StalePreviewException} si la série a
     *                                changé depuis l'Aperçu
     */
    public CorrectionOutcome<PayoutDTO> cancel(Long payoutId, CorrectionReason reason, CorrectionMode mode,
                                               String previewToken) {
        Objects.requireNonNull(payoutId, "payoutId");
        requirePayoutReason(reason);
        return runner.run(new CancelCommand(payoutId, reason), mode, previewToken);
    }

    /**
     * Remplace une paie initiale par une paie à un autre taux, en Aperçu ou en confirmation
     * (exigence 7.2) : l'originale est annulée et désigne sa remplaçante, qui reçoit un nouveau numéro.
     *
     * @param note note de la remplaçante ; {@code null} reprend celle de l'originale
     * @throws CustomServiceException 400 si le Motif ne convient pas ou si le taux manque ; 404 ; 409
     *                                comme pour l'annulation, et si la paie est une régularisation, si
     *                                le taux est le même ou désactivé, ou s'il n'y a plus rien à
     *                                partager
     */
    public CorrectionOutcome<PayoutCorrection> replace(Long payoutId, Long rateId, String note,
                                                       CorrectionReason reason, CorrectionMode mode,
                                                       String previewToken) {
        Objects.requireNonNull(payoutId, "payoutId");
        requirePayoutReason(reason);
        if (rateId == null) {
            throw new CustomServiceException("Choisissez le taux de la paie de remplacement.", HttpStatus.BAD_REQUEST);
        }
        return runner.run(new ReplaceCommand(payoutId, rateId, note, reason), mode, previewToken);
    }

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    private final class CancelCommand implements CorrectionCommand<PayoutDTO> {

        private final Long payoutId;
        private final CorrectionReason reason;

        private CancelCommand(Long payoutId, CorrectionReason reason) {
            this.payoutId = payoutId;
            this.reason = reason;
        }

        @Override
        public String fingerprint() {
            return EncashmentCorrectionService.canonical("PAYOUT_CANCEL", payoutId, reason.type(), reason.text());
        }

        @Override
        public CorrectionScope scope() {
            return CorrectionScope.empty();
        }

        @Override
        public CorrectionExecution<PayoutDTO> execute() {
            Correctable target = correctable(payoutId);
            TeacherPayoutEntity payout = target.payout();
            Totals before = Totals.of(target.active());

            cancelInPlace(payout, reason);
            payoutRepository.saveAndFlush(payout);
            Totals after = before.without(payout);

            String done = describe(payout) + " " + agreed(payout, "annulé");
            List<CorrectionEffect> effects = List.of(
                    new CorrectionEffect(CorrectionEffectType.PAYOUT_CANCELLED, done
                            + (payout.getKind() == PayoutKind.INITIAL ? " : la série redevient à payer" : "")),
                    sharesEffect(payout, before, after));
            AuditDraft trace = trace(CorrectionAction.PAYOUT_CANCELLED, payout,
                    cancellationValues(payout, PayoutStatus.ACTIVE, before),
                    cancellationValues(payout, PayoutStatus.CANCELLED, after),
                    done + where(payout) + " : " + sharesChange(before, after), reason);
            return new CorrectionExecution<>(TeacherPayoutService.toDto(payout), effects, Set.of(), List.of(trace));
        }
    }

    // ------------------------------------------------------------------
    // Remplacement
    // ------------------------------------------------------------------

    private final class ReplaceCommand implements CorrectionCommand<PayoutCorrection> {

        private final Long payoutId;
        private final Long rateId;
        private final String note;
        private final CorrectionReason reason;

        private ReplaceCommand(Long payoutId, Long rateId, String note, CorrectionReason reason) {
            this.payoutId = payoutId;
            this.rateId = rateId;
            this.note = note;
            this.reason = reason;
        }

        @Override
        public String fingerprint() {
            return EncashmentCorrectionService.canonical("PAYOUT_REPLACE", payoutId, rateId, note, reason.type(),
                    reason.text());
        }

        @Override
        public CorrectionScope scope() {
            return CorrectionScope.empty();
        }

        @Override
        public CorrectionExecution<PayoutCorrection> execute() {
            Correctable target = correctable(payoutId);
            TeacherPayoutEntity original = target.payout();
            if (original.getKind() != PayoutKind.INITIAL) {
                throw new CustomServiceException("La paie " + original.getPayoutNumber() + " est une régularisation : "
                        + "elle ne se remplace pas. Annulez-la ; l'écart restant réapparaîtra dans « À payer ».",
                        HttpStatus.CONFLICT);
            }
            TeacherPayRateEntity rate = rateService.requireActive(rateId);
            if (original.getRate() != null && rate.getId().equals(original.getRate().getId())) {
                throw new CustomServiceException("La paie " + original.getPayoutNumber() + " est déjà au taux « "
                        + original.getRateLabel() + " » : choisissez un autre taux. Pour l'argent arrivé ou rendu "
                        + "depuis, enregistrez une régularisation.", HttpStatus.CONFLICT);
            }
            Totals before = Totals.of(target.active());
            Map<String, Object> oldValue = replacementValues(original, PayoutStatus.ACTIVE, null);

            // L'originale d'abord, écrite : l'index n'admet qu'une paie initiale active par série.
            cancelInPlace(original, reason);
            payoutRepository.saveAndFlush(original);
            TeacherPayoutEntity replacement = payoutService.recordReplacement(original, rate,
                    note == null ? original.getNote() : note);
            original.setReplacedBy(replacement);
            payoutRepository.saveAndFlush(original);
            Totals after = Totals.of(List.of(replacement));

            List<CorrectionEffect> effects = List.of(
                    new CorrectionEffect(CorrectionEffectType.PAYOUT_CANCELLED, describe(original) + " "
                            + agreed(original, "annulé") + ", remplacée par " + replacement.getPayoutNumber()),
                    new CorrectionEffect(CorrectionEffectType.PAYOUT_CREATED, "Paie " + replacement.getPayoutNumber()
                            + " : " + AmountEffectWriter.money(replacement.getBaseDelta()) + " DA × "
                            + percent(replacement.getTeacherPercent()) + " = "
                            + AmountEffectWriter.money(replacement.getTeacherAmount()) + " DA à "
                            + TeacherPayoutService.fullName(replacement.getTeacher()) + ", école "
                            + AmountEffectWriter.money(replacement.getSchoolAmount()) + " DA (taux « "
                            + replacement.getRateLabel() + " »)"),
                    sharesEffect(original, before, after));
            AuditDraft trace = trace(CorrectionAction.PAYOUT_REPLACED, original, oldValue,
                    replacementValues(replacement, PayoutStatus.CANCELLED, replacement.getPayoutNumber()),
                    "Paie " + original.getPayoutNumber() + " remplacée par " + replacement.getPayoutNumber()
                            + where(original) + " : taux « " + original.getRateLabel() + " » "
                            + percent(original.getTeacherPercent()) + " → « " + replacement.getRateLabel() + " » "
                            + percent(replacement.getTeacherPercent()) + ", " + sharesChange(before, after), reason);
            return new CorrectionExecution<>(new PayoutCorrection(TeacherPayoutService.toDto(original),
                    TeacherPayoutService.toDto(replacement)), effects, Set.of(), List.of(trace));
        }
    }

    // ------------------------------------------------------------------
    // Règles
    // ------------------------------------------------------------------

    /** Paie à corriger et paies actives de sa série, de la plus ancienne à la plus récente. */
    private record Correctable(TeacherPayoutEntity payout, List<TeacherPayoutEntity> active) {
    }

    /**
     * La paie, active et la plus récente de sa série, lue après le verrou de la série : telle que l'a
     * laissée la dernière transaction validée (D3).
     */
    private Correctable correctable(Long payoutId) {
        Long seriesId = payoutRepository.findSeriesIdById(payoutId).orElseThrow(() -> notFound(payoutId));
        // La paie existe et désigne sa série par une clé étrangère : ni l'une ni l'autre ne peut manquer ici.
        seriesRepository.findByIdForUpdate(seriesId).orElseThrow();
        TeacherPayoutEntity payout = payoutRepository.findById(payoutId).orElseThrow();
        if (payout.getStatus() != PayoutStatus.ACTIVE) {
            TeacherPayoutEntity replacedBy = payout.getReplacedBy();
            throw new CustomServiceException("La paie " + payout.getPayoutNumber() + " est déjà annulée, le "
                    + day(payout.getCancelledAt()) + " par " + payout.getCancelledBy()
                    + (replacedBy == null ? "" : ", remplacée par " + replacedBy.getPayoutNumber()) + ".",
                    HttpStatus.CONFLICT);
        }
        List<TeacherPayoutEntity> active = payoutRepository.findActiveForSeries(seriesId);
        TeacherPayoutEntity latest = active.get(active.size() - 1);
        if (!latest.getId().equals(payout.getId())) {
            throw new CustomServiceException("La paie " + payout.getPayoutNumber()
                    + " n'est pas la plus récente de la série « " + payout.getSeries().getName()
                    + " » : corrigez d'abord " + latest.getPayoutNumber() + ".", HttpStatus.CONFLICT);
        }
        return new Correctable(payout, active);
    }

    private void cancelInPlace(TeacherPayoutEntity payout, CorrectionReason reason) {
        payout.setStatus(PayoutStatus.CANCELLED);
        payout.setCancelledAt(new Date());
        // La trace de la correction exige un administrateur authentifié : c'est lui qui annule.
        payout.setCancelledBy(auditorAware.getCurrentAuditor().orElse(SYSTEM));
        payout.setCancelReasonType(reason.type());
        payout.setCancelReasonText(reason.text());
    }

    private static void requirePayoutReason(CorrectionReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (!PAYOUT_REASONS.contains(reason.type())) {
            throw new CustomServiceException("Motif « " + reason.type()
                    + " » sans rapport avec la correction d'une paie.", HttpStatus.BAD_REQUEST);
        }
    }

    private static CustomServiceException notFound(Long payoutId) {
        return new CustomServiceException("Paie introuvable : " + payoutId, HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Cumuls de la série
    // ------------------------------------------------------------------

    /** Ce que la série a versé à l'enseignant et gardé pour l'école, sur ses paies actives. */
    record Totals(BigDecimal teacher, BigDecimal school) {

        static Totals of(List<TeacherPayoutEntity> payouts) {
            BigDecimal teacher = BigDecimal.ZERO.setScale(2);
            BigDecimal school = BigDecimal.ZERO.setScale(2);
            for (TeacherPayoutEntity payout : payouts) {
                teacher = teacher.add(payout.getTeacherAmount());
                school = school.add(payout.getSchoolAmount());
            }
            return new Totals(teacher, school);
        }

        Totals without(TeacherPayoutEntity payout) {
            return new Totals(teacher.subtract(payout.getTeacherAmount()), school.subtract(payout.getSchoolAmount()));
        }
    }

    // ------------------------------------------------------------------
    // Rédaction
    // ------------------------------------------------------------------

    /** « Paie PAIE-2030-0001 de 43 200,00 DA à … », « Complément … à … », « Retenue … sur … ». */
    static String describe(TeacherPayoutEntity payout) {
        String teacher = TeacherPayoutService.fullName(payout.getTeacher());
        BigDecimal amount = payout.getTeacherAmount();
        String number = payout.getPayoutNumber();
        if (payout.getKind() == PayoutKind.INITIAL) {
            return "Paie " + number + " de " + AmountEffectWriter.money(amount) + " DA à " + teacher;
        }
        if (amount.signum() < 0) {
            return "Retenue " + number + " de " + AmountEffectWriter.money(amount.negate()) + " DA sur " + teacher;
        }
        return "Complément " + number + " de " + AmountEffectWriter.money(amount) + " DA à " + teacher;
    }

    /** Accord du participe : une paie, une retenue annulées ; un complément annulé. */
    static String agreed(TeacherPayoutEntity payout, String participle) {
        boolean masculine = payout.getKind() == PayoutKind.REGULARIZATION && payout.getTeacherAmount().signum() > 0;
        return masculine ? participle : participle + "e";
    }

    /** « (Octobre, Maths 4 AM A) ». */
    private static String where(TeacherPayoutEntity payout) {
        return " (" + payout.getSeries().getName() + ", " + payout.getGroup().getName() + ")";
    }

    private static String sharesChange(Totals before, Totals after) {
        return "enseignant " + AmountEffectWriter.money(before.teacher()) + " → "
                + AmountEffectWriter.money(after.teacher()) + " DA, école " + AmountEffectWriter.money(before.school())
                + " → " + AmountEffectWriter.money(after.school()) + " DA";
    }

    private static CorrectionEffect sharesEffect(TeacherPayoutEntity payout, Totals before, Totals after) {
        return new CorrectionEffect(CorrectionEffectType.PAYOUT_SHARES_CHANGED, "« " + payout.getSeries().getName()
                + " » (" + payout.getGroup().getName() + ") : versé à l'enseignant "
                + AmountEffectWriter.money(before.teacher()) + " → " + AmountEffectWriter.money(after.teacher())
                + " DA, part de l'école " + AmountEffectWriter.money(before.school()) + " → "
                + AmountEffectWriter.money(after.school()) + " DA");
    }

    /** « 60 % », « 62,5 % ». */
    static String percent(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',') + " %";
    }

    private static String day(Date date) {
        return DAY.format(date.toInstant().atZone(ZoneId.systemDefault()));
    }

    private static AuditDraft trace(CorrectionAction action, TeacherPayoutEntity payout, Map<String, ?> oldValue,
                                    Map<String, ?> newValue, String summary, CorrectionReason reason) {
        return AuditDraft.builder()
                .domain(CorrectionDomain.TEACHER_PAYOUT)
                .action(action)
                .entityId(payout.getId())
                .groupId(payout.getGroup().getId())
                .seriesId(payout.getSeries().getId())
                .oldValue(oldValue)
                .newValue(newValue)
                .summary(summary)
                .reason(reason)
                .build();
    }

    /** Une paie annulée et les cumuls de sa série : l'effet sur les deux parts (exigence 7.4). */
    private static Map<String, Object> cancellationValues(TeacherPayoutEntity payout, PayoutStatus status,
                                                          Totals totals) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", status.name());
        values.put("payoutNumber", payout.getPayoutNumber());
        values.put("kind", payout.getKind().name());
        values.put("teacherAmount", payout.getTeacherAmount());
        values.put("schoolAmount", payout.getSchoolAmount());
        values.put("seriesTeacherPaid", totals.teacher());
        values.put("seriesSchoolKept", totals.school());
        return values;
    }

    /** Taux et parts d'une paie ; après remplacement, l'originale annulée et sa remplaçante. */
    private static Map<String, Object> replacementValues(TeacherPayoutEntity payout, PayoutStatus status,
                                                         String replacedBy) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", status.name());
        values.put("replacedBy", replacedBy);
        values.put("rateLabel", payout.getRateLabel());
        values.put("teacherPercent", payout.getTeacherPercent());
        values.put("collectedNet", payout.getCollectedNet());
        values.put("teacherAmount", payout.getTeacherAmount());
        values.put("schoolAmount", payout.getSchoolAmount());
        return values;
    }
}
