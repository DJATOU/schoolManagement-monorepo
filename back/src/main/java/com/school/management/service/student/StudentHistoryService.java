package com.school.management.service.student;

import com.school.management.dto.group.GroupHistoryDTO;
import com.school.management.dto.serie.SeriesHistoryDTO;
import com.school.management.dto.session.BillingInclusionReason;
import com.school.management.dto.session.SessionHistoryDTO;
import com.school.management.dto.student.StudentFullHistoryDTO;
import com.school.management.persistance.*;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.service.payment.BillableSessionsResolver;
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import com.school.management.service.payment.PaymentQuoteService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StudentHistoryService {

    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final com.school.management.repository.PaymentDetailRepository paymentDetailRepository;
    private final RefundRepository refundRepository;

    /**
     * Source unique de la règle du prorata (exigence 1.5).
     *
     * <p>L'historique portait sa propre copie de cette règle ({@code resolveBillableSessions})
     * pendant que le devis plafonnait sur {@code series.total_sessions} : les deux se
     * contredisaient, et l'écart devenait un trop-perçu intégral. La règle vit désormais dans
     * un seul composant, que l'historique, le résolveur de coût, le devis et la ventilation
     * consomment tous.</p>
     */
    private final BillableSessionsResolver billableSessionsResolver;

    /**
     * Source unique du Coût_Série_Prorata, du prix net et de l'exemption (exigences 11.1, 11.2).
     *
     * <p>L'historique recalculait le prix net et le coût de série lui-même, à partir du tarif
     * catalogue et du taux de réduction. Trois composants comparaient ainsi un montant versé à
     * un coût qu'ils calculaient chacun de leur côté — {@code PaymentProcessingService},
     * {@code PaymentStatusService} et celui-ci — c'est-à-dire exactement la divergence que
     * cette fonctionnalité corrige. Les trois lisent désormais
     * {@code PaymentQuoteService.quote(...).monthTotalCost()}, qui relaie sans le modifier le
     * coût produit par le {@code PaymentCostResolver}, lui-même alimenté par le résolveur de
     * séances facturables.</p>
     */
    private final PaymentQuoteService paymentQuoteService;

    public StudentHistoryService(StudentRepository studentRepository,
            AttendanceRepository attendanceRepository,
            com.school.management.repository.PaymentDetailRepository paymentDetailRepository,
            RefundRepository refundRepository,
            BillableSessionsResolver billableSessionsResolver,
            PaymentQuoteService paymentQuoteService) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.refundRepository = refundRepository;
        this.billableSessionsResolver = billableSessionsResolver;
        this.paymentQuoteService = paymentQuoteService;
    }

    public StudentFullHistoryDTO getStudentFullHistory(Long studentId) {
        // Récupérer l'étudiant
        StudentEntity student = studentRepository.findById(Objects.requireNonNull(studentId))
                .orElseThrow(() -> new EntityNotFoundException("Étudiant non trouvé"));

        return mapStudentEntityToDTO(student);
    }

    // ===================== mapStudentEntityToDTO ======================
    private StudentFullHistoryDTO mapStudentEntityToDTO(StudentEntity student) {
        StudentFullHistoryDTO dto = new StudentFullHistoryDTO();
        dto.setStudentId(student.getId());
        dto.setStudentName(student.getFirstName() + " " + student.getLastName());

        // 1) Groupes fixes : ceux où l'étudiant est officiellement inscrit
        List<GroupEntity> fixedGroups = new ArrayList<>(student.getGroups());

        // 2) Groupes "rattrapage" (où attendance.isCatchUp = true pour l'étudiant)
        List<GroupEntity> catchUpGroups = attendanceRepository
                .findByStudentIdAndIsCatchUp(student.getId(), true)
                .stream()
                .map(AttendanceEntity::getGroup)
                .distinct()
                .toList();

        // 3) Union des deux
        Set<GroupEntity> unionSet = new HashSet<>(fixedGroups);
        unionSet.addAll(catchUpGroups);

        // 4) Construire la liste de GroupHistoryDTO, triée par nom de groupe.
        //    L'union passe par un HashSet : sans tri explicite, l'ordre des groupes dans
        //    l'historique dépend du hachage et change d'une génération à l'autre.
        List<GroupHistoryDTO> groupDTOs = unionSet.stream()
                .sorted(GROUP_BY_NAME)
                .map(group -> mapGroupEntityToDTO(group, student))
                .toList();

        dto.setGroups(groupDTOs);
        return dto;
    }

    /*
     * Codes de statut, et non libellés affichables.
     *
     * L'historique était auparavant renvoyé en français en dur (« Présent », « Non payé »,
     * « Complet »), ce qui rendait le PDF et la fenêtre d'historique intraduisibles et
     * obligeait le frontend à comparer des chaînes accentuées pour choisir ses couleurs.
     * Le backend expose désormais des codes stables ; la traduction appartient à
     * l'interface (clés studentHistory.* en FR et EN).
     */
    private static final String PAYMENT_PAID = "PAID";
    private static final String PAYMENT_PARTIAL = "PARTIAL";
    private static final String PAYMENT_UNPAID = "UNPAID";

    private static final String ATTENDANCE_PRESENT = "PRESENT";
    private static final String ATTENDANCE_ABSENT = "ABSENT";
    private static final String ATTENDANCE_UNKNOWN = "UNKNOWN";

    private static final String SERIES_FULL = "FULL";
    private static final String SERIES_PARTIAL = "PARTIAL";

    /** Groupes classés par nom, insensible à la casse et aux valeurs nulles. */
    private static final Comparator<GroupEntity> GROUP_BY_NAME =
            Comparator.comparing(GroupEntity::getName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    /**
     * Séries classées de la plus ancienne à la plus récente.
     *
     * <p>La date de début est la clé naturelle ; l'identifiant sert de départage et de
     * repli pour les séries héritées sans date, afin que l'ordre reste toujours croissant
     * et surtout stable d'une génération à l'autre.</p>
     */
    private static final Comparator<SessionSeriesEntity> SERIES_CHRONOLOGICAL =
            Comparator.comparing(SessionSeriesEntity::getSerieTimeStart,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(SessionSeriesEntity::getId,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    /** Séances classées par date de début, valeurs nulles en dernier. */
    private static final Comparator<SessionEntity> SESSION_CHRONOLOGICAL =
            Comparator.comparing(SessionEntity::getSessionTimeStart,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(SessionEntity::getId,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    // ===================== mapGroupEntityToDTO ======================
    private GroupHistoryDTO mapGroupEntityToDTO(GroupEntity group, StudentEntity student) {
        GroupHistoryDTO dto = new GroupHistoryDTO();
        dto.setGroupId(group.getId());
        dto.setGroupName(group.getName());

        // L'inscription officielle n'est plus déduite de student.getGroups().contains(group) :
        // c'est une collection @ManyToMany en mémoire, dont l'appartenance dépend de
        // equals/hashCode et du chargement de la session Hibernate. Elle est portée par le
        // champ « enrolled » du résolveur partagé, lu en base série par série.

        // Convertir les séries en SeriesHistoryDTO, de la plus ancienne à la plus récente.
        // group.getSeries() est un Set : l'ordre d'itération n'est pas garanti.
        List<SeriesHistoryDTO> seriesDTOs = group.getSeries().stream()
                .sorted(SERIES_CHRONOLOGICAL)
                .map(series -> mapSeriesEntityToDTO(series, student))
                .filter(Objects::nonNull) // on enlève les séries qui n'ont aucune session pertinente
                .toList();

        dto.setSeries(seriesDTOs);
        return dto;
    }

    // ===================== mapSeriesEntityToDTO ======================
    private SeriesHistoryDTO mapSeriesEntityToDTO(SessionSeriesEntity series,
            StudentEntity student) {
        SeriesHistoryDTO dto = new SeriesHistoryDTO();
        dto.setSeriesId(series.getId());
        dto.setSeriesName(series.getName());

        // IMPORTANT: Charger tous les payment details ACTIFS (non CANCELLED) pour cette
        // série et cet étudiant en une seule requête
        // Cela évite le problème de lazy loading et améliore les performances
        List<PaymentDetailEntity> paymentDetailsForSeries = paymentDetailRepository
                .findByPayment_StudentIdAndSession_SessionSeriesId(student.getId(), series.getId());

        // FILTRER les paiements CANCELLED
        List<PaymentDetailEntity> activePaymentDetails = paymentDetailsForSeries.stream()
                .filter(pd -> pd.getActive() != null && pd.getActive())
                .filter(pd -> !"CANCELLED".equals(pd.getPayment().getStatus()))
                .toList();

        // Créer une map sessionId -> PaymentDetail pour un accès rapide
        Map<Long, PaymentDetailEntity> paymentDetailMap = activePaymentDetails.stream()
                .collect(Collectors.toMap(
                        pd -> pd.getSession().getId(),
                        pd -> pd,
                        (existing, replacement) -> existing // En cas de doublon, garder le premier
                ));

        // Séances facturables, séances écartées, inscription et date d'inscription : tout
        // provient du résolveur partagé. L'historique ne reproduit plus la règle du prorata.
        BillableSessions billable = billableSessionsResolver.resolve(student.getId(), series.getId());
        boolean enrolled = billable.enrolled();

        // Séances affichées. Un inscrit voit TOUTE la série, y compris les séances écartées :
        // elles restent visibles (non présentes, non facturées) plutôt que de disparaître.
        // Un rattrapage ne voit que les séances où il a une présence active, c'est-à-dire
        // exactement ses séances facturables (exigence 1.4).
        // Tri null-safe : une séance sans date de début faisait échouer le comparateur
        // précédent (NullPointerException) et donc toute la génération de l'historique.
        List<SessionEntity> filteredSessions = enrolled
                ? Stream.concat(billable.billable().stream(), billable.excluded().stream())
                        .sorted(SESSION_CHRONOLOGICAL)
                        .toList()
                : billable.billable();

        // Si le résultat est VIDE => on ne retourne pas cette série (on renvoie null)
        if (filteredSessions.isEmpty()) {
            return null; // => la série n'apparaîtra pas dans le PDF
        }

        // Devis de la série : prix net, Coût_Série_Prorata et exemption viennent tous de la
        // source partagée. L'historique calculait auparavant lui-même le prix net à partir du
        // tarif catalogue et du taux de réduction, donc un coût qui pouvait s'écarter de celui
        // du devis d'un centime d'arrondi — assez pour qu'une série soldée d'un côté paraisse
        // impayée de l'autre.
        PaymentQuoteDTO quote = paymentQuoteService.quote(student.getId(), series.getId());

        // Prix net de la séance : tarif catalogue minoré de la réduction applicable.
        // Sans cette minoration, l'historique facturait le plein tarif alors que la fiche
        // étudiante, elle, applique la réduction : un étudiant « à jour » côté fiche
        // apparaissait « non payé » séance par séance dans son historique.
        BigDecimal netSessionPrice = quote.netPricePerSession();

        // Les deux prix unitaires sont relayés tels quels vers l'interface, qui énonce le coût
        // en clair (« 2 séances × 6 000 DA = 12 000 DA ») et barre le tarif catalogue quand une
        // réduction s'applique. Aucun calcul n'est ajouté ici : le devis les a déjà résolus.
        dto.setUnitPriceNet(netSessionPrice);
        dto.setUnitPriceGross(quote.grossPricePerSession());

        // Séances réellement facturables à cet étudiant, dans l'ordre chronologique.
        List<SessionEntity> billableSessions = billable.billable();

        // Coût au prorata de la série : séances facturables × prix net, jamais
        // total_sessions × prix (exigence 11.1).
        BigDecimal totalCostForStudent = quote.monthTotalCost();

        // Total versé : on réutilise les versements déjà chargés plus haut. Le calcul
        // parcourait auparavant le graphe d'entités (session.getPaymentDetails()), donc une
        // source différente de celle qui alimente les lignes de séances.
        double totalPaidForSeries = sumAmounts(activePaymentDetails);

        // Statut de la série, évalué contre le Coût_Série_Prorata (exigences 11.1, 11.2) :
        // un étudiant arrivé à la dernière séance d'une série de quatre et l'ayant réglée est
        // soldé. Comparé au coût nominal de la série, il resterait indéfiniment « partiel ».
        dto.setPaymentStatus(BigDecimal.valueOf(totalPaidForSeries).compareTo(totalCostForStudent) >= 0
                ? SERIES_FULL : SERIES_PARTIAL);
        dto.setTotalAmountPaid(totalPaidForSeries);
        dto.setTotalCost(totalCostForStudent.doubleValue());

        // NOUVEAU (tâche 16.1) : exemption (réduction 100 %) au niveau de la série.
        // Le devis porte déjà le verdict (taux résolu comparé à 1.00) : le relire ici évitait
        // une seconde résolution du taux, susceptible de diverger de celle du devis.
        boolean exempted = quote.exempted();
        dto.setIsExempted(exempted);

        // NOUVEAU (tâche 16.1) : total remboursé sur la série (BigDecimal, échelle 2).
        dto.setTotalRefunded(resolveTotalRefunded(student.getId(), series.getId()));

        // Décompte des séances facturables (exigence 11.6). Il ne se déduit pas du nombre de
        // lignes affichées, qui comprend aussi les séances écartées : sans lui, l'interface ne
        // peut pas justifier un coût inférieur au coût nominal de la série.
        dto.setBillableSessions(billable.billableCount());

        // Motif d'inclusion de chaque séance, décidé ici où les deux listes du résolveur sont
        // disponibles. L'interface l'approximait auparavant à partir de l'assiduité et du
        // montant affecté, et classait donc à tort une séance future encore sans feuille de
        // présence (exigences 11.3, 11.5).
        Map<Long, BillingInclusionReason> inclusionReasons = resolveInclusionReasons(billable);

        // Affectation des versements aux séances, du plus ancien au plus récent.
        Map<Long, SessionCoverage> coverages = allocatePayments(
                billableSessions, netSessionPrice, activePaymentDetails);

        // Réconciliation versé / affecté / trop-perçu : la somme des montants affectés aux
        // séances doit être justifiable ligne par ligne. L'écart avec le montant versé est
        // exposé comme trop-perçu au lieu d'être noyé dans le total.
        BigDecimal allocated = coverages.values().stream()
                .map(SessionCoverage::allocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal overpaid = BigDecimal.valueOf(totalPaidForSeries).subtract(allocated);
        dto.setTotalAllocated(allocated.doubleValue());
        dto.setTotalOverpaid(overpaid.signum() > 0
                ? overpaid.setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0);

        // Mentions de rattrapage : la même vue que celle consommée par le résolveur de séances
        // facturables, afin qu'un écran et un montant ne puissent pas qualifier un rattrapage
        // différemment. Résolue une fois par série, pas une fois par séance.
        CatchUpMentions catchUpMentions = resolveCatchUpMentions(student.getId(), billable);

        // 4) Construire la liste finale de SessionHistoryDTO
        List<SessionHistoryDTO> sessionDTOs = filteredSessions.stream()
                .map(session -> mapSessionEntityToDTO(session, student, paymentDetailMap, exempted,
                        coverages, netSessionPrice, inclusionReasons.get(session.getId()),
                        catchUpMentions))
                .toList();

        dto.setSessions(sessionDTOs);
        return dto;
    }

    /**
     * Motif d'inclusion de chaque séance de la série, facturable ou écartée.
     *
     * <p>Les deux listes du résolveur portent déjà le verdict « facturable / écartée ». Ce qui
     * leur manque, c'est la <em>raison</em> : une séance facturable l'est soit parce qu'elle est
     * postérieure à l'inscription, soit parce que l'étudiant y a assisté alors qu'elle la
     * précédait. Seul le second cas doit être étiqueté rattrapage (exigence 11.5) ; étiqueter
     * les deux reviendrait à signaler comme surprenante une facturation qui n'a rien de
     * surprenant.</p>
     *
     * <p>Un étudiant sans inscription n'a pas de date d'inscription : toutes ses séances
     * facturables sont des séances suivies (exigence 1.4), donc du rattrapage pur — le motif
     * {@code ATTENDED_BEFORE_ENROLMENT} les décrit exactement.</p>
     */
    private Map<Long, BillingInclusionReason> resolveInclusionReasons(BillableSessions billable) {
        Map<Long, BillingInclusionReason> reasons = new HashMap<>();
        Date enrollmentDate = billable.enrollmentDate();
        for (SessionEntity session : billable.billable()) {
            reasons.put(session.getId(), isOnOrAfterEnrolment(session, enrollmentDate)
                    ? BillingInclusionReason.AFTER_ENROLMENT
                    : BillingInclusionReason.ATTENDED_BEFORE_ENROLMENT);
        }
        for (SessionEntity session : billable.excluded()) {
            reasons.put(session.getId(), BillingInclusionReason.EXCLUDED);
        }
        return reasons;
    }

    /**
     * Séance postérieure ou égale à la date d'inscription. Même test que le résolveur partagé :
     * il ne s'agit pas de rejouer la règle du prorata — le verdict facturable/écarté vient du
     * résolveur — mais de départager les deux motifs d'inclusion.
     */
    private boolean isOnOrAfterEnrolment(SessionEntity session, Date enrollmentDate) {
        if (enrollmentDate == null) {
            return false;
        }
        Date sessionDate = session.getSessionTimeStart();
        return sessionDate != null && !sessionDate.before(enrollmentDate);
    }

    /**
     * Mentions de rattrapage d'une série, indexées par séance.
     *
     * @param caughtUpElsewhere séances manquées puis rattrapées ailleurs, avec le lieu et la date
     *                          du rattrapage (exigence 1.4)
     * @param billedInOrigin    séances de cette série écartées parce que déjà facturées dans la
     *                          série d'origine d'un rattrapage compensatoire (exigence 2.9)
     * @param unknownMissed     séances de rattrapage dont la séance manquée n'est pas déterminable
     *                          (exigence 1.10)
     */
    private record CatchUpMentions(Map<Long, CaughtUpElsewhere> caughtUpElsewhere,
                                   Map<Long, OriginBilling> billedInOrigin,
                                   Set<Long> unknownMissed) {

        static CatchUpMentions empty() {
            return new CatchUpMentions(Map.of(), Map.of(), Set.of());
        }
    }

    /** Où et quand une séance manquée a été rattrapée. */
    private record CaughtUpElsewhere(Date caughtUpOn, String groupName) { }

    /** Série d'origine qui facture une séance écartée côté accueil. */
    private record OriginBilling(String seriesName, String groupName, Date sessionDate) { }

    /**
     * Résout les mentions de rattrapage d'une série pour un étudiant.
     *
     * <p>Deux sens sont nécessaires, et c'est ce qui rend cette résolution moins évidente qu'il n'y
     * paraît. Vue depuis la série <strong>d'origine</strong>, une séance manquée puis rattrapée doit
     * porter « Rattrapée » avec la date et le groupe d'accueil. Vue depuis la série
     * <strong>d'accueil</strong>, la séance de rattrapage doit dire qu'elle est déjà facturée
     * ailleurs, sans quoi une séance suivie mais non facturée ressemble à une erreur de calcul.</p>
     *
     * <p>Les rattrapages sont lus une fois par série, non une fois par séance : la vue du
     * qualificateur est constante en nombre de requêtes, mais la parcourir par séance
     * multiplierait inutilement le travail.</p>
     */
    private CatchUpMentions resolveCatchUpMentions(Long studentId, BillableSessions billable) {
        List<AttendanceEntity> catchUps =
                attendanceRepository.findByStudentIdAndIsCatchUpTrueAndActiveTrue(studentId);
        if (catchUps.isEmpty()) {
            return CatchUpMentions.empty();
        }

        Map<Long, CaughtUpElsewhere> caughtUp = new HashMap<>();
        Map<Long, OriginBilling> billedInOrigin = new HashMap<>();
        Set<Long> unknownMissed = new HashSet<>();

        for (AttendanceEntity catchUp : catchUps) {
            SessionEntity hostSession = catchUp.getSession();
            SessionEntity missedSession = catchUp.getMissedSession();

            if (missedSession == null || missedSession.getId() == null) {
                // Exigence 1.10 : le signaler plutôt que d'afficher un vide, qui ressemblerait à une
                // donnée perdue alors que c'est le lien qui manque.
                if (hostSession != null && hostSession.getId() != null) {
                    unknownMissed.add(hostSession.getId());
                }
                continue;
            }

            // Sens origine : la séance manquée porte la mention « Rattrapée ».
            caughtUp.put(missedSession.getId(), new CaughtUpElsewhere(
                    hostSession != null ? hostSession.getSessionTimeStart() : null,
                    groupNameOf(catchUp.getGroup())));

            // Sens accueil : la séance de rattrapage écartée nomme la série qui la facture. La
            // condition s'appuie sur le verdict du résolveur, seule autorité sur l'exclusion.
            if (hostSession != null && hostSession.getId() != null
                    && billable.isCompensatedAway(hostSession.getId())) {
                billedInOrigin.put(hostSession.getId(), new OriginBilling(
                        missedSession.getSessionSeries() != null
                                ? missedSession.getSessionSeries().getName() : null,
                        groupNameOf(missedSession.getGroup()),
                        missedSession.getSessionTimeStart()));
            }
        }

        return new CatchUpMentions(Map.copyOf(caughtUp), Map.copyOf(billedInOrigin),
                Set.copyOf(unknownMissed));
    }

    private String groupNameOf(GroupEntity group) {
        return group != null ? group.getName() : null;
    }

    /** Reporte sur la séance les mentions de rattrapage qui la concernent. */
    private void applyCatchUpMentions(SessionHistoryDTO dto, SessionEntity session,
            CatchUpMentions mentions) {
        Long sessionId = session.getId();
        if (sessionId == null) {
            return;
        }

        CaughtUpElsewhere caughtUp = mentions.caughtUpElsewhere().get(sessionId);
        if (caughtUp != null) {
            dto.setCaughtUpElsewhere(true);
            dto.setCaughtUpOnDate(caughtUp.caughtUpOn());
            dto.setCaughtUpInGroupName(caughtUp.groupName());
        }

        OriginBilling origin = mentions.billedInOrigin().get(sessionId);
        if (origin != null) {
            dto.setBilledInOriginSeries(true);
            dto.setOriginSeriesName(origin.seriesName());
            dto.setOriginGroupName(origin.groupName());
            dto.setOriginSessionDate(origin.sessionDate());
        }

        if (mentions.unknownMissed().contains(sessionId)) {
            dto.setMissedSessionUnknown(true);
        }
    }

    /**
     * Reporte l'auteur et la date de la dernière modification de la justification.
     *
     * <p>Lue depuis les colonnes d'audit de la présence plutôt que depuis le journal dédié : ce
     * dernier exigerait une requête par séance affichée, pour une information d'appoint. Le journal
     * complet reste consultable depuis le dialogue de modification, où il est le sujet.</p>
     */
    private void applyJustificationAudit(SessionHistoryDTO dto, AttendanceEntity attendance) {
        if (attendance.getDateUpdate() == null) {
            return;
        }
        dto.setJustificationUpdatedBy(attendance.getUpdatedBy());
        dto.setJustificationUpdatedAt(
                Date.from(attendance.getDateUpdate().atZone(java.time.ZoneId.systemDefault()).toInstant()));
    }

    /**
     * Récupère le total remboursé pour l'étudiant sur la série. Retourne 0 (échelle 2)
     * lorsqu'aucun remboursement n'existe.
     */
    private BigDecimal resolveTotalRefunded(Long studentId, Long seriesId) {
        BigDecimal total = refundRepository.sumRefundsForStudentAndSeries(studentId, seriesId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Couverture d'une séance : montant du versement affecté à cette séance et date du
     * versement qui l'a soldée.
     */
    private record SessionCoverage(BigDecimal allocated, Date paymentDate, boolean nothingToPay) {
    }

    /**
     * Affecte les versements de l'étudiant aux séances facturables, du versement le plus
     * ancien au plus récent et de la séance la plus ancienne à la plus récente.
     *
     * <p>Le statut d'une séance se lisait auparavant sur le seul montant que l'administrateur
     * avait saisi <em>sur cette séance</em>. Or la façon de régler (une seule saisie pour
     * plusieurs séances, versements échelonnés) relève du « comment on paie », pas du
     * « combien est dû » : une séance restait donc « non payée » alors que l'étudiant avait
     * versé plus que le total de la série. L'affectation en cascade rétablit la cohérence
     * avec le témoin « à jour » de la fiche étudiante.</p>
     */
    private Map<Long, SessionCoverage> allocatePayments(List<SessionEntity> billableSessions,
            BigDecimal netSessionPrice,
            List<PaymentDetailEntity> paymentDetails) {
        Map<Long, SessionCoverage> coverages = new HashMap<>();

        // Séance gratuite ou exemptée à 100 % : il n'y a rien à régler.
        if (netSessionPrice.signum() <= 0) {
            billableSessions.forEach(session -> coverages.put(session.getId(),
                    new SessionCoverage(BigDecimal.ZERO.setScale(2), null, true)));
            return coverages;
        }

        Map<Long, BigDecimal> allocated = new LinkedHashMap<>();
        Map<Long, Date> lastPaymentDate = new HashMap<>();
        billableSessions.forEach(session -> allocated.put(session.getId(), BigDecimal.ZERO.setScale(2)));

        List<PaymentDetailEntity> chronological = paymentDetails.stream()
                .sorted(PAYMENT_DETAIL_CHRONOLOGICAL)
                .toList();

        int cursor = 0;
        for (PaymentDetailEntity detail : chronological) {
            BigDecimal remaining = detail.getAmountPaid() != null
                    ? BigDecimal.valueOf(detail.getAmountPaid()).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);

            while (remaining.signum() > 0 && cursor < billableSessions.size()) {
                Long sessionId = billableSessions.get(cursor).getId();
                BigDecimal missing = netSessionPrice.subtract(allocated.get(sessionId));
                if (missing.signum() <= 0) {
                    cursor++;
                    continue;
                }
                BigDecimal taken = remaining.min(missing);
                allocated.put(sessionId, allocated.get(sessionId).add(taken));
                lastPaymentDate.put(sessionId, detail.getPaymentDate());
                remaining = remaining.subtract(taken);
                if (allocated.get(sessionId).compareTo(netSessionPrice) >= 0) {
                    cursor++;
                }
            }
        }

        allocated.forEach((sessionId, amount) -> coverages.put(sessionId,
                new SessionCoverage(amount, lastPaymentDate.get(sessionId), false)));
        return coverages;
    }

    /** Versements classés du plus ancien au plus récent, valeurs nulles en dernier. */
    private static final Comparator<PaymentDetailEntity> PAYMENT_DETAIL_CHRONOLOGICAL =
            Comparator.comparing(PaymentDetailEntity::getPaymentDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(PaymentDetailEntity::getId,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    // ===================== mapSessionEntityToDTO ======================
    private SessionHistoryDTO mapSessionEntityToDTO(SessionEntity session, StudentEntity student,
            Map<Long, PaymentDetailEntity> paymentDetailMap, boolean exempted,
            Map<Long, SessionCoverage> coverages, BigDecimal netSessionPrice,
            BillingInclusionReason inclusionReason, CatchUpMentions catchUpMentions) {
        SessionHistoryDTO dto = new SessionHistoryDTO();

        // NOUVEAU (tâche 16.1) : propager l'exemption de la série sur chaque séance
        // (pilote la légende « Présent et exempté »).
        dto.setIsExempted(exempted);

        // Mentions de rattrapage, renseignées avant le raccourci « séance dévalidée » : une séance
        // désactivée qui a été rattrapée ailleurs mérite de le dire, comme les autres.
        applyCatchUpMentions(dto, session, catchUpMentions);

        // Verdict de facturation et son motif, renseignés avant tout autre traitement : une
        // séance dévalidée sort par le raccourci ci-dessous, et elle a droit à cette
        // information comme les autres (exigences 11.3, 11.5).
        dto.setInclusionReason(inclusionReason);
        dto.setBillable(inclusionReason != BillingInclusionReason.EXCLUDED);

        // Si la session n'est plus active (= dévalidée)
        if (Boolean.FALSE.equals(session.getActive())) {
            dto.setSessionId(session.getId());
            dto.setSessionName(session.getTitle());
            dto.setAttendanceStatus(ATTENDANCE_UNKNOWN);
            dto.setIsJustified(false);
            dto.setPaymentStatus(PAYMENT_UNPAID);
            dto.setAmountPaid(0.0);
            // Une séance dévalidée n'est plus réclamée : elle ne porte aucun montant attendu.
            dto.setAmountDue(zeroMoney());
            dto.setAmountRemaining(zeroMoney());
            return dto;
        }

        // Sinon, la session est valide, on applique la logique habituelle
        dto.setSessionId(session.getId());
        dto.setSessionName(session.getTitle());
        dto.setSessionDate(session.getSessionTimeStart());

        // Récupérer l'attendance
        AttendanceEntity attendance = session.getAttendances().stream()
                .filter(a -> a.getStudent().getId().equals(student.getId()))
                .filter(AttendanceEntity::isActive)
                .findFirst()
                .orElse(null);

        if (attendance != null) {
            if (!attendance.isActive()) {
                // attendance inactive
                dto.setAttendanceStatus(ATTENDANCE_UNKNOWN);
                dto.setIsJustified(false);
            } else {
                // Présent ou Absent
                dto.setAttendanceStatus(Boolean.TRUE.equals(attendance.getIsPresent())
                        ? ATTENDANCE_PRESENT : ATTENDANCE_ABSENT);
                dto.setIsJustified(attendance.getIsJustified());

                // catchUpSession => si attendance.isCatchUp = true
                dto.setCatchUpSession(Boolean.TRUE.equals(attendance.getIsCatchUp()));

                // Dernière modification de la justification (exigence 5.9) : c'est ce qui permet de
                // répondre à un parent qui contexte, sans ouvrir un autre écran.
                applyJustificationAudit(dto, attendance);
            }
        } else {
            dto.setAttendanceStatus(ATTENDANCE_UNKNOWN);
            // Pas d'attendance => catchUpSession = false
            dto.setCatchUpSession(false);
        }

        // CORRECTION: Utiliser la map pré-chargée au lieu du lazy loading
        // Cela résout le problème où les paiements n'apparaissaient pas pour les
        // sessions de rattrapage
        SessionCoverage coverage = coverages.get(session.getId());

        if (coverage != null) {
            // Séance facturable : le montant affiché est la part des versements qui la
            // couvre, et la date celle du versement qui l'a soldée.
            dto.setPaymentStatus(resolveCoverageStatus(coverage, netSessionPrice));
            dto.setAmountPaid(coverage.allocated().doubleValue());
            dto.setPaymentDate(coverage.paymentDate());
            // Montant attendu et reste à régler : sur une série exemptée à 100 %, le prix net
            // vaut zéro, donc les deux valent zéro — la ligne annoncera « exempté » et non une
            // dette. Le reste est borné à zéro : un versement couvrant plusieurs séances peut
            // en solder une au-delà de son prix.
            dto.setAmountDue(money(netSessionPrice));
            dto.setAmountRemaining(money(netSessionPrice.subtract(coverage.allocated()).max(BigDecimal.ZERO)));
            return dto;
        }

        // Séance écartée de la facturation : elle reste affichée, sans montant attendu. Lui en
        // prêter un la ferait lire comme une dette (exigences 11.3, 11.4).
        dto.setAmountDue(zeroMoney());
        dto.setAmountRemaining(zeroMoney());

        // Séance non facturable à cet étudiant (antérieure à son inscription et non suivie).
        // On n'affiche un montant que si un versement lui a malgré tout été rattaché.
        PaymentDetailEntity paymentDetail = paymentDetailMap.get(session.getId());
        if (paymentDetail != null) {
            double amountPaid = paymentDetail.getAmountPaid() != null ? paymentDetail.getAmountPaid() : 0.0;
            dto.setPaymentStatus(amountPaid > 0 ? PAYMENT_PAID : PAYMENT_UNPAID);
            dto.setAmountPaid(amountPaid);
            dto.setPaymentDate(paymentDetail.getPaymentDate());
        } else {
            dto.setPaymentStatus(PAYMENT_UNPAID);
            dto.setAmountPaid(0.0);
            dto.setPaymentDate(null);
        }

        return dto;
    }

    /**
     * Statut de paiement <strong>de la séance</strong>, déduit de la part des versements
     * affectée à cette séance face à son prix net.
     *
     * <p>La colonne affichait à l'origine {@code paymentDetail.getPayment().getStatus()},
     * donc le statut global du paiement parent : corriger le montant d'une seule séance
     * faisait basculer toutes les autres. Elle a ensuite comparé le montant saisi sur la
     * séance à son tarif catalogue, sans tenir compte de la réduction ni des versements
     * couvrant plusieurs séances. Le verdict porte désormais sur la couverture réelle.</p>
     *
     * <p>Comparaison en {@link BigDecimal} : en {@code double}, 3500.0 face à 3499.9999
     * produit des verdicts arbitraires.</p>
     */
    private String resolveCoverageStatus(SessionCoverage coverage, BigDecimal netSessionPrice) {
        // Séance gratuite ou étudiant exempté : il n'y a rien à régler.
        if (coverage.nothingToPay() || netSessionPrice.signum() <= 0) {
            return PAYMENT_PAID;
        }
        BigDecimal allocated = coverage.allocated();
        if (allocated.signum() <= 0) {
            return PAYMENT_UNPAID;
        }
        return allocated.compareTo(netSessionPrice) >= 0 ? PAYMENT_PAID : PAYMENT_PARTIAL;
    }

    // ===================== Helper methods ======================

    /** Montant à l'échelle monétaire du projet (2 décimales, HALF_UP). */
    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** Zéro à l'échelle monétaire : une absence de montant attendu s'affiche « 0,00 ». */
    private BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /** Somme des montants versés, les montants nuls comptant pour zéro. */
    private double sumAmounts(List<PaymentDetailEntity> details) {
        return details.stream()
                .map(PaymentDetailEntity::getAmountPaid)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }
}
