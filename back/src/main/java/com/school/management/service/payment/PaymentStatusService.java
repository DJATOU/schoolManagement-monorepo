package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.*;
import com.school.management.repository.*;
import com.school.management.service.DiscountService;
import com.school.management.service.GroupPaymentStatus;
import com.school.management.service.SeriesPaymentStatus;
import com.school.management.service.SessionPaymentStatus;
import com.school.management.service.StudentPaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsable du calcul des statuts de paiement.
 * Calcule si les paiements sont en retard, le montant dû, etc.
 * pour les étudiants, groupes, et séries de sessions.
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Service
public class PaymentStatusService {

        private static final Logger LOGGER = LoggerFactory.getLogger(PaymentStatusService.class);

        private final PaymentRepository paymentRepository;
        private final PaymentDetailRepository paymentDetailRepository;
        private final StudentRepository studentRepository;
        private final GroupRepository groupRepository;
        private final SessionRepository sessionRepository;
        private final SessionSeriesRepository sessionSeriesRepository;
        private final AttendanceRepository attendanceRepository;
        private final PaymentCostResolver paymentCostResolver;
        private final DiscountService discountService;

        /**
         * Source unique du Coût_Série_Prorata (exigences 11.1, 11.2).
         *
         * <p>Le statut d'une série ne peut pas être déduit des seuls statuts de ses séances :
         * une séance sans fiche de présence est ignorée ici, si bien qu'un étudiant arrivé à la
         * dernière séance d'une série paraissait n'avoir aucune situation de série. Le verdict
         * « soldée » est donc porté explicitement, comparé au coût au prorata annoncé par le
         * devis — exactement le montant que {@code PaymentProcessingService} et
         * {@code StudentHistoryService} comparent de leur côté.</p>
         */
        private final PaymentQuoteService paymentQuoteService;

        public PaymentStatusService(
                        PaymentRepository paymentRepository,
                        PaymentDetailRepository paymentDetailRepository,
                        StudentRepository studentRepository,
                        GroupRepository groupRepository,
                        SessionRepository sessionRepository,
                        SessionSeriesRepository sessionSeriesRepository,
                        AttendanceRepository attendanceRepository,
                        PaymentCostResolver paymentCostResolver,
                        DiscountService discountService,
                        PaymentQuoteService paymentQuoteService) {
                this.paymentRepository = paymentRepository;
                this.paymentDetailRepository = paymentDetailRepository;
                this.studentRepository = studentRepository;
                this.groupRepository = groupRepository;
                this.sessionRepository = sessionRepository;
                this.sessionSeriesRepository = sessionSeriesRepository;
                this.attendanceRepository = attendanceRepository;
                this.paymentCostResolver = paymentCostResolver;
                this.discountService = discountService;
                this.paymentQuoteService = paymentQuoteService;
        }

        /**
         * Récupère le statut de paiement pour tous les étudiants d'un groupe.
         *
         * @param groupId l'ID du groupe
         * @return la liste des statuts de paiement des étudiants
         */
        @Transactional(readOnly = true)
        public List<StudentPaymentStatus> getPaymentStatusForGroup(Long groupId) {
                LOGGER.info("Fetching payment status for group: {}", groupId);

                GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                                .orElseThrow(() -> new RuntimeException("Group not found with ID: " + groupId));

                List<StudentEntity> students = studentRepository.findByGroups_Id(groupId);
                List<StudentPaymentStatus> result = new ArrayList<>();

                for (StudentEntity student : students) {
                        // NOTE: bug latent préexistant conservé — on passe ici {@code groupId}
                        // à l'argument {@code sessionSeriesId} (l'ancien code faisait déjà cet
                        // amalgame). Comme le résolveur peut lever une exception si l'identifiant
                        // ne correspond à aucune série (ex. NOT_FOUND quand groupId n'est pas un
                        // id de série), on protège l'appel : un échec de résolution est traité
                        // comme "non en retard" (false) afin de ne pas casser le listing complet
                        // du groupe.
                        boolean isOverdue;
                        try {
                                isOverdue = isStudentPaymentOverdueForSeries(student.getId(), groupId);
                        } catch (RuntimeException e) {
                                LOGGER.debug("Résolution du statut impossible pour l'étudiant {} "
                                                + "et l'identifiant {} (traité comme non en retard) : {}",
                                                student.getId(), groupId, e.getMessage());
                                isOverdue = false;
                        }

                        StudentPaymentStatus paymentStatus = new StudentPaymentStatus(
                                        student.getId(),
                                        student.getFirstName(),
                                        student.getLastName(),
                                        student.getEmail(),
                                        student.getGender(),
                                        student.getPhoneNumber(),
                                        student.getDateOfBirth(),
                                        student.getPlaceOfBirth(),
                                        student.getPhoto(),
                                        student.getLevel() != null ? student.getLevel().getId() : null,
                                        student.getGroups().stream().map(GroupEntity::getId)
                                                        .collect(Collectors.toSet()),
                                        student.getTutor() != null ? student.getTutor().getId() : null,
                                        student.getEstablishment(),
                                        student.getAverageScore(),
                                        isOverdue,
                                        student.getActive());
                        result.add(paymentStatus);
                }

                LOGGER.info("Found {} students in group {}, {} with overdue payments",
                                result.size(), groupId,
                                result.stream().filter(StudentPaymentStatus::isPaymentOverdue).count());

                return result;
        }

        /**
         * Vérifie si un étudiant est en retard de paiement pour une série.
         *
         * <p>Cette dérivation de statut délègue désormais au {@link PaymentCostResolver}
         * (câblage vers le calculateur pur {@link PaymentCostCalculator}). Toute la logique
         * monétaire (prix par séance en {@link java.math.BigDecimal}, comptage present-only
         * cross-group, réduction, montant versé effectif = paiements − remboursements) est
         * résolue en interne : le paramètre {@code double pricePerSession} a été supprimé
         * conformément au design (audit H4, requirement 4.2). Aucune arithmétique flottante
         * n'intervient dans la dérivation du retard.</p>
         *
         * @param studentId       l'ID de l'étudiant
         * @param sessionSeriesId l'ID de la série de sessions
         * @return true si le paiement est en retard ({@code amountPaid < amountDueSoFar})
         */
        public boolean isStudentPaymentOverdueForSeries(Long studentId, Long sessionSeriesId) {
                LOGGER.debug("Checking payment status for student {} in series {}", studentId, sessionSeriesId);

                boolean isOverdue = paymentCostResolver.resolve(studentId, sessionSeriesId).late();

                LOGGER.debug("Student {} in series {} - Overdue: {}", studentId, sessionSeriesId, isOverdue);

                return isOverdue;
        }

        /**
         * Récupère le statut de paiement détaillé pour un étudiant
         * Retourne le statut pour chaque groupe, série et session.
         * IMPORTANT: Inclut AUSSI les groupes où l'étudiant a des sessions de
         * rattrapage.
         *
         * @param studentId l'ID de l'étudiant
         * @return la liste des statuts de paiement par groupe
         */
        @Transactional(readOnly = true)
        public List<GroupPaymentStatus> getPaymentStatusForStudent(Long studentId) {
                LOGGER.info("Fetching payment status for student: {}", studentId);

                List<GroupPaymentStatus> groupStatuses = new ArrayList<>();

                // 1) Groupes officiels de l'étudiant
                List<GroupEntity> officialGroups = groupRepository.findByStudents_Id(studentId);

                // 2) Groupes où l'étudiant a des sessions de rattrapage
                List<AttendanceEntity> catchUpAttendances = attendanceRepository
                                .findByStudentIdAndIsCatchUp(studentId, true);

                List<GroupEntity> catchUpGroups = catchUpAttendances.stream()
                                .filter(a -> a.isActive() && a.getIsPresent() != null && a.getIsPresent())
                                .map(AttendanceEntity::getGroup)
                                .distinct()
                                .toList();

                // 3) Fusionner les deux listes (Set pour éviter doublons)
                Set<GroupEntity> allGroups = new HashSet<>(officialGroups);
                allGroups.addAll(catchUpGroups);

                LOGGER.debug("Student {} - Official groups: {}, Catch-up groups: {}, Total unique: {}",
                                studentId, officialGroups.size(), catchUpGroups.size(), allGroups.size());

                for (GroupEntity group : allGroups) {
                        List<SeriesPaymentStatus> seriesStatuses = new ArrayList<>();
                        List<SessionSeriesEntity> seriesList = sessionSeriesRepository.findByGroupId(group.getId());

                        for (SessionSeriesEntity series : seriesList) {
                                BigDecimal discountRate = discountService.resolveRate(studentId, series.getId());
                                List<SessionPaymentStatus> sessionStatuses = getSessionPaymentStatuses(studentId,
                                                series, discountRate);

                                // Ne pas ajouter les séries vides (sans sessions pour cet étudiant)
                                if (!sessionStatuses.isEmpty()) {
                                        seriesStatuses.add(seriesStatus(studentId, series, sessionStatuses,
                                                        discountRate));
                                }
                        }

                        // Ne pas ajouter les groupes vides (sans séries pour cet étudiant)
                        if (!seriesStatuses.isEmpty()) {
                                groupStatuses.add(new GroupPaymentStatus(
                                                group.getId(),
                                                group.getName(),
                                                seriesStatuses));
                        }
                }

                LOGGER.info("Found {} groups with sessions for student {}", groupStatuses.size(), studentId);

                return groupStatuses;
        }

        /**
         * Récupère le statut de paiement pour toutes les sessions d'une série.
         * IMPORTANT: Retourne UNIQUEMENT les sessions où l'étudiant a une fiche de
         * présence
         * (attendance record) ou est lié via un rattrapage (catch-up).
         *
         * @param studentId l'ID de l'étudiant
         * @param series    la série de sessions
         * @return la liste des statuts de paiement par session
         */
        private List<SessionPaymentStatus> getSessionPaymentStatuses(Long studentId, SessionSeriesEntity series,
                        BigDecimal discountRate) {
                List<SessionPaymentStatus> result = new ArrayList<>();
                List<SessionEntity> sessions = sessionRepository.findBySessionSeries(series);

                for (SessionEntity session : sessions) {
                        // CRITIQUE: Ne retourner que les sessions où l'étudiant a une fiche de présence
                        AttendanceEntity attendance = attendanceRepository
                                        .findBySessionIdAndStudentId(session.getId(), studentId)
                                        .orElse(null);

                        // Si pas de fiche de présence, ignorer cette session
                        if (attendance == null) {
                                continue;
                        }

                        // NOTE (audit H4 / requirement 4.2) : les champs monétaires par session
                        // ci-dessous (amountDue / amountPaid en double) sont uniquement
                        // présentationnels et conservés pour garder stable le contrat du DTO
                        // {@link SessionPaymentStatus}. La source de vérité monétaire (retard,
                        // mois soldé) est le {@link PaymentCostCalculator} via le
                        // {@link PaymentCostResolver} — la dérivation du statut de retard ne
                        // s'appuie PAS sur ce calcul flottant.
                        // Calculer le montant dû pour cette session, réduction appliquée.
                        // Sans cette minoration, un étudiant exempté (réduction 100 %) affichait
                        // un montant dû plein et apparaissait « en retard ».
                        double sessionPrice = netPrice(session.getGroup().getPrice().getPrice(), discountRate);
                        boolean isPresent = attendance.getIsPresent() != null && attendance.getIsPresent();

                        // Point d'extension (décision différée — cf. business-rules.md) :
                        // la logique « absence payable » (facturer une séance manquée en fin
                        // d'année, justifiée ou non) N'EST PAS encore implémentée et ne doit
                        // pas être présumée ici. Le drapeau reste exposé dans le DTO pour la
                        // future implémentation, mais tant qu'il vaut false, seule la présence
                        // rend la séance payable. On évite volontairement l'expression
                        // {@code isPresent || isPaidEvenIfAbsent} qui produirait une branche
                        // morte inatteignable (isPaidEvenIfAbsent étant constant).
                        boolean isPaidEvenIfAbsent = false; // TODO: Implémenter la logique "absence payable"

                        // La session est payable si l'étudiant est présent (extension future :
                        // OU si l'absence devient payable, cf. point d'extension ci-dessus).
                        boolean isPayable = isPresent;
                        double amountDue = isPayable ? sessionPrice : 0.0;

                        // Calculer le montant payé pour cette session
                        // IMPORTANT: Ignorer les paiements CANCELLED
                        List<PaymentDetailEntity> details = paymentDetailRepository
                                        .findByPayment_StudentIdAndSessionId(studentId, session.getId());

                        double amountPaid = details.stream()
                                        .filter(detail -> detail.getActive() &&
                                                        (detail.getPermanentlyDeleted() == null
                                                                        || !detail.getPermanentlyDeleted())
                                                        &&
                                                        !"CANCELLED".equals(detail.getPayment().getStatus()))
                                        .mapToDouble(PaymentDetailEntity::getAmountPaid)
                                        .sum();

                        // Déterminer si en retard
                        boolean isOverdue = amountDue > 0 && amountPaid < amountDue;

                        result.add(new SessionPaymentStatus(
                                        session.getId(),
                                        session.getTitle(),
                                        isOverdue,
                                        isPresent,
                                        isPaidEvenIfAbsent,
                                        amountDue,
                                        amountPaid));
                }

                return result;
        }

        /**
         * Situation de paiement d'une série : le détail par séance, plus le verdict de série
         * évalué contre le Coût_Série_Prorata (exigences 11.1, 11.2).
         *
         * <p>Le coût, le montant versé et le dû à ce jour proviennent du devis, donc du même
         * calcul que celui utilisé par {@code PaymentProcessingService} pour fixer le statut
         * d'une ligne de paiement et par {@code StudentHistoryService} pour marquer une série
         * soldée. C'est ce partage qui empêche les trois écrans de se contredire.</p>
         *
         * <p>Un échec de résolution est journalisé et laisse les champs monétaires vides plutôt
         * que de faire échouer la fiche entière : la même précaution que celle du listing de
         * groupe et du relevé de groupe. Le détail par séance, lui, reste renseigné.</p>
         */
        private SeriesPaymentStatus seriesStatus(Long studentId, SessionSeriesEntity series,
                        List<SessionPaymentStatus> sessionStatuses, BigDecimal discountRate) {
                SeriesPaymentStatus status = new SeriesPaymentStatus(series.getId(), sessionStatuses,
                                isExemption(discountRate));
                status.setSeriesName(series.getName());
                try {
                        PaymentQuoteDTO quote = paymentQuoteService.quote(studentId, series.getId());
                        status.setProrataCost(quote.monthTotalCost());
                        status.setAmountPaid(quote.amountPaid());
                        status.setBillableSessions(quote.billableSessions());
                        // Soldée dès que le montant versé atteint le coût au prorata, même s'il
                        // est très inférieur au coût nominal de la série (exigence 11.2).
                        status.setFullyPaid(quote.amountPaid().compareTo(quote.monthTotalCost()) >= 0);
                        // En retard uniquement si le versé n'atteint pas le dû à ce jour, lui aussi
                        // borné aux séances facturables.
                        status.setLate(quote.amountPaid().compareTo(quote.amountDueSoFar()) < 0);
                } catch (RuntimeException e) {
                        LOGGER.warn("Coût au prorata non résolu pour l'étudiant {} et la série {} : {}",
                                        studentId, series.getId(), e.getMessage());
                }
                return status;
        }

        /**
         * Prix d'une séance après application du taux de réduction.
         *
         * <p>Calcul en {@link BigDecimal} (audit H4) puis conversion : les champs monétaires
         * par séance du DTO restent des {@code double} présentationnels.</p>
         *
         * @param grossPrice   prix catalogue de la séance
         * @param discountRate taux de réduction dans {@code [0.00, 1.00]}
         * @return le prix net, échelle 2, arrondi HALF_UP
         */
        private double netPrice(double grossPrice, BigDecimal discountRate) {
                return BigDecimal.valueOf(grossPrice)
                                .multiply(BigDecimal.ONE.subtract(discountRate))
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue();
        }

        /** Vrai si le taux vaut exactement 1.00, c'est-à-dire une exemption totale. */
        private boolean isExemption(BigDecimal discountRate) {
                return discountRate.compareTo(BigDecimal.ONE) == 0;
        }

        /**
         * Récupère les sessions auxquelles un étudiant a assisté.
         *
         * @param studentId l'ID de l'étudiant
         * @return la liste des sessions avec présence
         */
        public List<SessionEntity> getAttendedSessions(Long studentId) {
                return attendanceRepository.findByStudentIdAndIsPresent(studentId, true);
        }

        /**
         * Récupère les sessions qui ont été payées par un étudiant.
         *
         * @param studentId l'ID de l'étudiant
         * @return l'ensemble des sessions payées
         */
        public Set<SessionEntity> getPaidSessions(Long studentId) {
                List<PaymentDetailEntity> details = paymentDetailRepository.findByPayment_StudentId(studentId);
                return details.stream()
                                .map(PaymentDetailEntity::getSession)
                                .collect(Collectors.toSet());
        }

        /**
         * Récupère les sessions auxquelles un étudiant a assisté mais qu'il n'a pas
         * payées.
         *
         * @param studentId l'ID de l'étudiant
         * @return la liste des sessions impayées
         */
        public List<SessionEntity> getUnpaidAttendedSessions(Long studentId) {
                List<SessionEntity> attended = getAttendedSessions(studentId);
                Set<SessionEntity> paid = getPaidSessions(studentId);

                return attended.stream()
                                .filter(session -> !paid.contains(session))
                                .toList();
        }
}
