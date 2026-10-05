package com.school.management.service.payroll;

import com.school.management.dto.payroll.PayRequest;
import com.school.management.dto.payroll.PayableSeriesDTO;
import com.school.management.dto.payroll.PayableSeriesDTO.PayableState;
import com.school.management.dto.payroll.PayoutDTO;
import com.school.management.dto.payroll.PayoutListDTO;
import com.school.management.dto.payroll.PayoutPreviewDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PayoutKind;
import com.school.management.persistance.PayoutStatus;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.persistance.TeacherPayRateEntity;
import com.school.management.persistance.TeacherPayoutEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.TeacherPayoutRepository;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.CurrentSchoolYearService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.SeriesCollectionService;
import com.school.management.service.payment.SeriesCollectionService.SeriesCollection;
import com.school.management.service.payroll.PayoutCalculator.Shares;
import com.school.management.service.payroll.SeriesCompletionService.SeriesCompletion;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Payer un enseignant : séries à payer, paie initiale, régularisation, consultation (spec
 * teacher-payroll, exigences 2 à 4, 6 et 9).
 *
 * <h2>Aperçu, puis confirmation</h2>
 * Chaque paie se lit avant d'être écrite. L'aperçu calcule les deux parts et les scelle d'un jeton ;
 * la confirmation recalcule tout, sous verrou de la série, et refuse si le jeton ne correspond plus :
 * un encaissement, un remboursement ou une autre paie survenus entre-temps ne passent pas inaperçus
 * (exigence 4.3, D6). Un aperçu n'écrit rien et ne consomme aucun numéro.
 *
 * <h2>Ce que ce service ne fait pas</h2>
 * Il ne corrige pas une paie : annuler et remplacer passent par {@code PayoutCorrectionService}, avec
 * motif et trace. Il ne consulte pas l'année scolaire : payer la dernière série d'une année close est
 * voulu (exigence 10, D10).
 */
@Service
public class TeacherPayoutService {

    private static final String SYSTEM = "system";
    private static final String TOKEN_VERSION = "payout-preview-v1";

    /** Index unique partiel de V9 : une seule Paie_Initiale active par série. */
    static final String INITIAL_ACTIVE_INDEX = "uk_teacher_payout_initial_active";

    private final SessionSeriesRepository seriesRepository;
    private final GroupRepository groupRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherPayoutRepository payoutRepository;
    private final TeacherPayRateService rateService;
    private final SeriesCollectionService collectionService;
    private final SeriesCompletionService completionService;
    private final PayoutNumberService numberService;
    private final CurrentSchoolYearService currentSchoolYearService;
    private final AuditorAware<String> auditorAware;

    public TeacherPayoutService(SessionSeriesRepository seriesRepository,
                                GroupRepository groupRepository,
                                TeacherRepository teacherRepository,
                                TeacherPayoutRepository payoutRepository,
                                TeacherPayRateService rateService,
                                SeriesCollectionService collectionService,
                                SeriesCompletionService completionService,
                                PayoutNumberService numberService,
                                CurrentSchoolYearService currentSchoolYearService,
                                AuditorAware<String> auditorAware) {
        this.seriesRepository = seriesRepository;
        this.groupRepository = groupRepository;
        this.teacherRepository = teacherRepository;
        this.payoutRepository = payoutRepository;
        this.rateService = rateService;
        this.collectionService = collectionService;
        this.completionService = completionService;
        this.numberService = numberService;
        this.currentSchoolYearService = currentSchoolYearService;
        this.auditorAware = auditorAware;
    }

    // ==================================================================
    // Séries à payer (exigence 2)
    // ==================================================================

    /**
     * Séries à traiter : terminées et non payées, en cours, ou payées dont l'encaissé a changé. Une
     * série payée et à jour n'y figure pas : elle est dans « Paies versées ».
     *
     * <p>Sans filtre de groupe : toutes les séries des groupes actifs de l'année courante, et, des
     * autres années, celles qui appellent un paiement (à payer, à régulariser). Payer la dernière
     * série d'une année close est voulu (exigence 10.1) : la limiter à l'année courante la ferait
     * disparaître de l'écran dès la bascule d'année. Les séries en cours, sans enseignant ou sans
     * encaissé d'une année passée ne sont en revanche que du bruit.</p>
     *
     * @param teacherId restreint aux séries de cet enseignant ; facultatif
     * @param groupId   restreint à ce groupe, toutes lignes comprises ; facultatif
     */
    @Transactional(readOnly = true)
    public List<PayableSeriesDTO> payable(Long teacherId, Long groupId) {
        Long currentYearId = groupId != null ? null : currentSchoolYearService.findCurrent()
                .map(SchoolYearEntity::getId).orElse(null);
        List<PayableSeriesDTO> rows = new ArrayList<>();
        for (GroupEntity group : groupsInScope(groupId)) {
            boolean pastYear = currentYearId != null && !currentYearId.equals(schoolYearIdOf(group));
            List<SessionSeriesEntity> seriesList = seriesRepository.findByGroupId(group.getId());
            if (seriesList.isEmpty()) {
                continue;
            }
            List<Long> seriesIds = seriesList.stream().map(SessionSeriesEntity::getId).toList();
            Map<Long, SeriesCollection> collections = collectionService.ofGroup(group.getId());
            Map<Long, SeriesCompletion> completions = completionService.of(seriesIds);
            Map<Long, List<TeacherPayoutEntity>> payouts = payoutRepository.findActiveForSeriesIn(seriesIds)
                    .stream().collect(Collectors.groupingBy(payout -> payout.getSeries().getId()));

            for (SessionSeriesEntity series : seriesList) {
                PayableSeriesDTO row = row(group, series,
                        collections.getOrDefault(series.getId(), SeriesCollection.none()),
                        completions.get(series.getId()),
                        payouts.getOrDefault(series.getId(), List.of()));
                if (row != null && (teacherId == null || teacherId.equals(row.teacherId()))
                        && (!pastYear || calls(row.state()))) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Ligne qui appelle un paiement : la seule qu'une année passée fait remonter. */
    private static boolean calls(PayableState state) {
        return state == PayableState.PAYABLE || state == PayableState.TO_REGULARIZE;
    }

    private static Long schoolYearIdOf(GroupEntity group) {
        return group.getSchoolYear() == null ? null : group.getSchoolYear().getId();
    }

    private List<GroupEntity> groupsInScope(Long groupId) {
        if (groupId != null) {
            return List.of(groupRepository.findById(groupId).orElseThrow(() -> new CustomServiceException(
                    "Groupe introuvable : " + groupId, HttpStatus.NOT_FOUND)));
        }
        return groupRepository.findAllActive().stream()
                .sorted(Comparator.comparing(GroupEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(GroupEntity::getId))
                .toList();
    }

    /** Ligne d'une série, ou rien si la série n'appelle aucune action. */
    private PayableSeriesDTO row(GroupEntity group, SessionSeriesEntity series, SeriesCollection collection,
                                 SeriesCompletion completion, List<TeacherPayoutEntity> payouts) {
        TeacherPayoutEntity initial = initialOf(payouts);
        if (initial != null) {
            BigDecimal teacherPaid = teacherPaid(payouts);
            BigDecimal gap = PayoutCalculator.gap(collection.net(), initial.getTeacherPercent(), teacherPaid);
            if (gap.signum() == 0) {
                return null;
            }
            return new PayableSeriesDTO(series.getId(), series.getName(), group.getId(), group.getName(),
                    initial.getTeacher().getId(), fullName(initial.getTeacher()), PayableState.TO_REGULARIZE,
                    completion.activeSessions(), completion.validatedSessions(),
                    collection.gross(), collection.refunded(), collection.net(),
                    initial.getPayoutNumber(), initial.getTeacherPercent(), teacherPaid, gap);
        }
        if (completion.activeSessions() == 0) {
            return null;
        }
        TeacherEntity teacher = group.getTeacher();
        PayableState state;
        if (!completion.finished()) {
            state = PayableState.NOT_FINISHED;
        } else if (teacher == null) {
            state = PayableState.NO_TEACHER;
        } else if (collection.net().signum() <= 0) {
            state = PayableState.NOTHING_COLLECTED;
        } else {
            state = PayableState.PAYABLE;
        }
        return new PayableSeriesDTO(series.getId(), series.getName(), group.getId(), group.getName(),
                teacher == null ? null : teacher.getId(), teacher == null ? null : fullName(teacher), state,
                completion.activeSessions(), completion.validatedSessions(),
                collection.gross(), collection.refunded(), collection.net(), null, null, null, null);
    }

    // ==================================================================
    // Paie initiale (exigences 3 et 4)
    // ==================================================================

    /**
     * Aperçu de la paie d'une série terminée, au taux choisi.
     *
     * @throws CustomServiceException 404 série ou taux introuvable ; 400 taux non choisi ; 409 série
     *                                déjà payée, non terminée, sans enseignant, taux désactivé, rien
     *                                d'encaissé
     */
    @Transactional(readOnly = true)
    public PayoutPreviewDTO previewPay(Long seriesId, PayRequest request) {
        return initialPreview(foundSeries(seriesId), request);
    }

    /**
     * Enregistre la paie d'une série, si l'aperçu lu est encore exact.
     *
     * @throws StalePayoutPreviewException 409 avec le nouvel aperçu si un montant a changé
     */
    @Transactional
    public PayoutDTO confirmPay(Long seriesId, PayRequest request) {
        String token = requireToken(request);
        SessionSeriesEntity series = lockedSeries(seriesId);
        PayoutPreviewDTO preview = initialPreview(series, request);
        requireSameToken(preview, token);

        TeacherPayRateEntity rate = rateService.requireActive(request.rateId());
        return toDto(save(series, preview, rate, null, request.note()));
    }

    private PayoutPreviewDTO initialPreview(SessionSeriesEntity series, PayRequest request) {
        Objects.requireNonNull(request, "request");
        List<TeacherPayoutEntity> payouts = payoutRepository.findActiveForSeries(series.getId());
        TeacherPayoutEntity initial = initialOf(payouts);
        if (initial != null) {
            throw new CustomServiceException("La série « " + series.getName() + " » est déjà payée ("
                    + initial.getPayoutNumber() + "). Pour l'argent arrivé ou rendu depuis, enregistrez une "
                    + "régularisation.", HttpStatus.CONFLICT);
        }
        GroupEntity group = series.getGroup();
        if (group == null) {
            throw new CustomServiceException("La série « " + series.getName()
                    + " » n'est rattachée à aucun groupe : elle n'a pas d'enseignant à payer.", HttpStatus.CONFLICT);
        }
        TeacherEntity teacher = group.getTeacher();
        if (teacher == null) {
            throw new CustomServiceException("Le groupe « " + group.getName()
                    + " » n'a pas d'enseignant : rattachez-en un avant de payer la série.", HttpStatus.CONFLICT);
        }
        requireFinished(series);
        if (request.rateId() == null) {
            throw new CustomServiceException("Choisissez le taux de rémunération à appliquer.", HttpStatus.BAD_REQUEST);
        }
        TeacherPayRateEntity rate = rateService.requireActive(request.rateId());

        SeriesCollection collection = collectionService.of(series);
        Shares shares;
        try {
            shares = PayoutCalculator.initial(collection.net(), rate.getTeacherPercent());
        } catch (IllegalArgumentException e) {
            throw new CustomServiceException(e.getMessage(), HttpStatus.CONFLICT);
        }
        return preview(series, group, teacher, PayoutKind.INITIAL, rate.getId(), rate.getLabel(),
                rate.getTeacherPercent(), collection, zero(), shares, zero(), null);
    }

    private void requireFinished(SessionSeriesEntity series) {
        SeriesCompletion completion = completionService.of(series.getId());
        if (completion.activeSessions() == 0) {
            throw new CustomServiceException("La série « " + series.getName()
                    + " » ne compte aucune séance : il n'y a rien à payer.", HttpStatus.CONFLICT);
        }
        if (!completion.finished()) {
            throw new CustomServiceException("La série « " + series.getName() + " » n'est pas terminée : "
                    + completion.remaining() + " séance(s) sur " + completion.activeSessions()
                    + " restent à valider.", HttpStatus.CONFLICT);
        }
    }

    // ==================================================================
    // Régularisation (exigence 6)
    // ==================================================================

    /**
     * Aperçu de la régularisation d'une série payée : complément si de l'argent est arrivé depuis,
     * retenue s'il a été rendu. Au pourcentage figé de la paie initiale.
     *
     * @throws CustomServiceException 404 série introuvable ; 409 série jamais payée, rien à régulariser
     */
    @Transactional(readOnly = true)
    public PayoutPreviewDTO previewRegularize(Long seriesId) {
        return regularizationPreview(foundSeries(seriesId));
    }

    /** Enregistre la régularisation d'une série, si l'aperçu lu est encore exact. */
    @Transactional
    public PayoutDTO confirmRegularize(Long seriesId, PayRequest request) {
        String token = requireToken(request);
        SessionSeriesEntity series = lockedSeries(seriesId);
        PayoutPreviewDTO preview = regularizationPreview(series);
        requireSameToken(preview, token);

        TeacherPayoutEntity initial = initialOf(payoutRepository.findActiveForSeries(series.getId()));
        return toDto(save(series, preview, initial.getRate(), initial, request.note()));
    }

    private PayoutPreviewDTO regularizationPreview(SessionSeriesEntity series) {
        List<TeacherPayoutEntity> payouts = payoutRepository.findActiveForSeries(series.getId());
        TeacherPayoutEntity initial = initialOf(payouts);
        if (initial == null) {
            throw new CustomServiceException("La série « " + series.getName()
                    + " » n'a pas encore été payée : enregistrez d'abord sa paie.", HttpStatus.CONFLICT);
        }
        TeacherPayoutEntity latest = payouts.get(payouts.size() - 1);
        BigDecimal teacherPaid = teacherPaid(payouts);
        SeriesCollection collection = collectionService.of(series);
        Shares shares;
        try {
            shares = PayoutCalculator.regularization(collection.net(), latest.getCollectedNet(),
                    initial.getTeacherPercent(), teacherPaid);
        } catch (IllegalArgumentException e) {
            throw new CustomServiceException(e.getMessage(), HttpStatus.CONFLICT);
        }
        TeacherPayRateEntity rate = initial.getRate();
        return preview(series, initial.getGroup(), initial.getTeacher(), PayoutKind.REGULARIZATION,
                rate == null ? null : rate.getId(), initial.getRateLabel(), initial.getTeacherPercent(),
                collection, latest.getCollectedNet(), shares, teacherPaid, latest.getId());
    }

    // ==================================================================
    // Consultation (exigence 9)
    // ==================================================================

    /**
     * Paies selon les filtres, la plus récente d'abord, avec les totaux des paies actives.
     *
     * @param from premier jour inclus, facultatif
     * @param to   dernier jour inclus, facultatif
     */
    @Transactional(readOnly = true)
    public PayoutListDTO search(Long teacherId, Long groupId, PayoutStatus status, Date from, Date to) {
        List<PayoutDTO> payouts = payoutRepository.search(teacherId, groupId, status, from, endOfDay(to))
                .stream().map(TeacherPayoutService::toDto).toList();
        BigDecimal teacherTotal = zero();
        BigDecimal schoolTotal = zero();
        for (PayoutDTO payout : payouts) {
            if (payout.status() == PayoutStatus.ACTIVE) {
                teacherTotal = teacherTotal.add(payout.teacherAmount());
                schoolTotal = schoolTotal.add(payout.schoolAmount());
            }
        }
        return new PayoutListDTO(payouts, teacherTotal, schoolTotal);
    }

    /**
     * Paies d'un enseignant, pour sa fiche.
     *
     * @throws CustomServiceException 404 si l'enseignant est introuvable
     */
    @Transactional(readOnly = true)
    public PayoutListDTO forTeacher(Long teacherId) {
        if (!teacherRepository.existsById(Objects.requireNonNull(teacherId, "teacherId"))) {
            throw new CustomServiceException("Enseignant introuvable : " + teacherId, HttpStatus.NOT_FOUND);
        }
        return search(teacherId, null, null, null, null);
    }

    /** Une paie. */
    @Transactional(readOnly = true)
    public PayoutDTO get(Long payoutId) {
        return toDto(payoutRepository.findById(Objects.requireNonNull(payoutId, "payoutId"))
                .orElseThrow(() -> new CustomServiceException("Paie introuvable : " + payoutId, HttpStatus.NOT_FOUND)));
    }

    // ==================================================================
    // Écriture
    // ==================================================================

    private TeacherPayoutEntity save(SessionSeriesEntity series, PayoutPreviewDTO preview, TeacherPayRateEntity rate,
                                     TeacherPayoutEntity initial, String note) {
        Date now = new Date();
        TeacherPayoutEntity payout = TeacherPayoutEntity.builder()
                .payoutNumber(numberService.next(now))
                .kind(preview.kind())
                .initialPayout(initial)
                .teacher(initial != null ? initial.getTeacher() : series.getGroup().getTeacher())
                .group(initial != null ? initial.getGroup() : series.getGroup())
                .series(series)
                .rate(rate)
                .rateLabel(preview.rateLabel())
                .teacherPercent(preview.teacherPercent())
                .collectedGross(preview.collectedGross())
                .refunded(preview.refunded())
                .collectedNet(preview.collectedNet())
                .baseDelta(preview.baseDelta())
                .teacherAmount(preview.teacherAmount())
                .schoolAmount(preview.schoolAmount())
                .note(cleanNote(note))
                .paidAt(now)
                .paidBy(currentAuditor())
                .status(PayoutStatus.ACTIVE)
                .build();
        try {
            return payoutRepository.saveAndFlush(payout);
        } catch (DataIntegrityViolationException e) {
            // Filet de sécurité derrière le verrou : l'index unique des paies initiales actives. Toute
            // autre violation est un défaut du calcul, à remonter tel quel plutôt qu'à maquiller.
            if (!violates(e, INITIAL_ACTIVE_INDEX)) {
                throw e;
            }
            throw new CustomServiceException("La série « " + series.getName()
                    + " » vient d'être payée par ailleurs : rechargez la liste.", HttpStatus.CONFLICT);
        }
    }

    /** La violation porte-t-elle sur cette contrainte ? Lu dans le message du pilote, seul à la nommer. */
    static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================
    // Aperçu et jeton
    // ==================================================================

    private PayoutPreviewDTO preview(SessionSeriesEntity series, GroupEntity group, TeacherEntity teacher,
                                     PayoutKind kind, Long rateId, String rateLabel, BigDecimal percent,
                                     SeriesCollection collection, BigDecimal netCovered, Shares shares,
                                     BigDecimal teacherPaid, Long latestPayoutId) {
        PayoutPreviewDTO unsigned = new PayoutPreviewDTO(series.getId(), series.getName(), group.getId(),
                group.getName(), teacher.getId(), fullName(teacher), kind, rateId, rateLabel, percent,
                collection.gross(), collection.refunded(), collection.net(), netCovered, shares.baseDelta(),
                shares.teacherAmount(), shares.schoolAmount(), teacherPaid, null);
        return withToken(unsigned, latestPayoutId);
    }

    /**
     * Empreinte de tout ce qui détermine les montants : série, enseignant, taux, encaissé, parts, et
     * dernière paie active. Deux aperçus égaux donnent le même jeton ; le moindre changement, un autre.
     */
    private static PayoutPreviewDTO withToken(PayoutPreviewDTO preview, Long latestPayoutId) {
        String canonical = String.join("|", TOKEN_VERSION, String.valueOf(preview.kind()),
                String.valueOf(preview.seriesId()), String.valueOf(preview.teacherId()),
                String.valueOf(preview.rateId()), plain(preview.teacherPercent()),
                plain(preview.collectedGross()), plain(preview.refunded()), plain(preview.collectedNet()),
                plain(preview.netCovered()), plain(preview.baseDelta()), plain(preview.teacherAmount()),
                plain(preview.schoolAmount()), plain(preview.teacherPaid()), String.valueOf(latestPayoutId));
        return new PayoutPreviewDTO(preview.seriesId(), preview.seriesName(), preview.groupId(),
                preview.groupName(), preview.teacherId(), preview.teacherName(), preview.kind(), preview.rateId(),
                preview.rateLabel(), preview.teacherPercent(), preview.collectedGross(), preview.refunded(),
                preview.collectedNet(), preview.netCovered(), preview.baseDelta(), preview.teacherAmount(),
                preview.schoolAmount(), preview.teacherPaid(), sha256(canonical));
    }

    private static String requireToken(PayRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.previewToken() == null || request.previewToken().isBlank()) {
            throw new CustomServiceException(
                    "Confirmation sans aperçu : lisez le calcul de la paie avant de la confirmer.",
                    HttpStatus.BAD_REQUEST);
        }
        return request.previewToken().strip();
    }

    private static void requireSameToken(PayoutPreviewDTO preview, String token) {
        if (!MessageDigest.isEqual(preview.previewToken().getBytes(StandardCharsets.US_ASCII),
                token.getBytes(StandardCharsets.US_ASCII))) {
            throw new StalePayoutPreviewException(preview);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    // ==================================================================
    // Outils
    // ==================================================================

    private SessionSeriesEntity foundSeries(Long seriesId) {
        return seriesRepository.findById(Objects.requireNonNull(seriesId, "seriesId"))
                .orElseThrow(() -> new CustomServiceException("Série introuvable : " + seriesId, HttpStatus.NOT_FOUND));
    }

    private SessionSeriesEntity lockedSeries(Long seriesId) {
        return seriesRepository.findByIdForUpdate(Objects.requireNonNull(seriesId, "seriesId"))
                .orElseThrow(() -> new CustomServiceException("Série introuvable : " + seriesId, HttpStatus.NOT_FOUND));
    }

    /** Paie initiale active d'une série, s'il y en a une. */
    static TeacherPayoutEntity initialOf(List<TeacherPayoutEntity> payouts) {
        return payouts.stream().filter(payout -> payout.getKind() == PayoutKind.INITIAL).findFirst().orElse(null);
    }

    static BigDecimal teacherPaid(List<TeacherPayoutEntity> payouts) {
        return payouts.stream().map(TeacherPayoutEntity::getTeacherAmount).reduce(zero(), BigDecimal::add);
    }

    static PayoutDTO toDto(TeacherPayoutEntity payout) {
        TeacherPayoutEntity initial = payout.getInitialPayout();
        TeacherPayoutEntity replaces = payout.getReplaces();
        TeacherPayoutEntity replacedBy = payout.getReplacedBy();
        return new PayoutDTO(payout.getId(), payout.getPayoutNumber(), payout.getKind(),
                initial == null ? null : initial.getPayoutNumber(),
                payout.getTeacher().getId(), fullName(payout.getTeacher()),
                payout.getGroup().getId(), payout.getGroup().getName(),
                payout.getSeries().getId(), payout.getSeries().getName(),
                payout.getRateLabel(), payout.getTeacherPercent(),
                payout.getCollectedGross(), payout.getRefunded(), payout.getCollectedNet(), payout.getBaseDelta(),
                payout.getTeacherAmount(), payout.getSchoolAmount(), payout.getNote(),
                payout.getPaidAt(), payout.getPaidBy(), payout.getStatus(),
                payout.getCancelledAt(), payout.getCancelledBy(), payout.getCancelReasonType(),
                payout.getCancelReasonText(),
                replaces == null ? null : replaces.getPayoutNumber(),
                replacedBy == null ? null : replacedBy.getPayoutNumber());
    }

    static String fullName(TeacherEntity teacher) {
        String first = teacher.getFirstName() == null ? "" : teacher.getFirstName();
        String last = teacher.getLastName() == null ? "" : teacher.getLastName();
        return (first + " " + last).strip();
    }

    private static String cleanNote(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }

    private String currentAuditor() {
        return auditorAware.getCurrentAuditor().filter(name -> !name.isBlank()).orElse(SYSTEM);
    }

    private static Date endOfDay(Date to) {
        if (to == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(to);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    /** Montant sans zéros de queue : 60 et 60,00 donnent la même empreinte. */
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
