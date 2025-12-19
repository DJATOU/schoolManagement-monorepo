package com.school.management.service.payment;

import com.school.management.persistance.*;
import com.school.management.repository.*;
import com.school.management.service.GroupPaymentStatus;
import com.school.management.service.SeriesPaymentStatus;
import com.school.management.service.SessionPaymentStatus;
import com.school.management.service.StudentPaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsable du calcul des statuts de paiement.
 *
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

    public PaymentStatusService(
            PaymentRepository paymentRepository,
            PaymentDetailRepository paymentDetailRepository,
            StudentRepository studentRepository,
            GroupRepository groupRepository,
            SessionRepository sessionRepository,
            SessionSeriesRepository sessionSeriesRepository,
            AttendanceRepository attendanceRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.attendanceRepository = attendanceRepository;
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

        GroupEntity group = groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("Group not found with ID: " + groupId));

        List<StudentEntity> students = studentRepository.findByGroups_Id(groupId);
        List<StudentPaymentStatus> result = new ArrayList<>();

        for (StudentEntity student : students) {
            boolean isOverdue = isStudentPaymentOverdueForSeries(
                student.getId(),
                groupId,
                group.getPrice().getPrice()
            );

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
                student.getGroups().stream().map(GroupEntity::getId).collect(Collectors.toSet()),
                student.getTutor() != null ? student.getTutor().getId() : null,
                student.getEstablishment(),
                student.getAverageScore(),
                isOverdue,
                student.getActive()
            );
            result.add(paymentStatus);
        }

        LOGGER.info("Found {} students in group {}, {} with overdue payments",
            result.size(), groupId, result.stream().filter(StudentPaymentStatus::isPaymentOverdue).count());

        return result;
    }

    /**
     * Vérifie si un étudiant est en retard de paiement pour une série.
     *
     * Calcule le montant dû basé sur les sessions auxquelles l'étudiant a assisté,
     * et compare avec le montant payé.
     *
     * @param studentId l'ID de l'étudiant
     * @param sessionSeriesId l'ID de la série de sessions
     * @param pricePerSession le prix par session
     * @return true si le paiement est en retard
     */
    public boolean isStudentPaymentOverdueForSeries(Long studentId, Long sessionSeriesId, double pricePerSession) {
        LOGGER.debug("Checking payment status for student {} in series {}", studentId, sessionSeriesId);

        long sessionsAttended = attendanceRepository
            .countByStudentIdAndSessionSeriesIdAndIsPresent(studentId, sessionSeriesId, true);

        double totalDue = sessionsAttended * pricePerSession;

        Double totalPaid = paymentRepository.findAmountPaidForStudentAndSeries(studentId, sessionSeriesId);
        if (totalPaid == null) totalPaid = 0.0;

        boolean isOverdue = totalPaid < totalDue;

        LOGGER.debug("Student {} - Attended: {}, Total due: {}, Total paid: {}, Overdue: {}",
            studentId, sessionsAttended, totalDue, totalPaid, isOverdue);

        return isOverdue;
    }

    /**
     * Récupère le statut de paiement détaillé pour un étudiant.
     *
     * Retourne le statut pour chaque groupe, série et session.
     *
     * @param studentId l'ID de l'étudiant
     * @return la liste des statuts de paiement par groupe
     */
    @Transactional(readOnly = true)
    public List<GroupPaymentStatus> getPaymentStatusForStudent(Long studentId) {
        LOGGER.info("Fetching payment status for student: {}", studentId);

        List<GroupPaymentStatus> groupStatuses = new ArrayList<>();
        List<GroupEntity> groups = groupRepository.findByStudents_Id(studentId);

        for (GroupEntity group : groups) {
            List<SeriesPaymentStatus> seriesStatuses = new ArrayList<>();
            List<SessionSeriesEntity> seriesList = sessionSeriesRepository.findByGroupId(group.getId());

            for (SessionSeriesEntity series : seriesList) {
                List<SessionPaymentStatus> sessionStatuses = getSessionPaymentStatuses(studentId, series);
                seriesStatuses.add(new SeriesPaymentStatus(series.getId(), sessionStatuses));
            }

            groupStatuses.add(new GroupPaymentStatus(
                group.getId(),
                group.getName(),
                seriesStatuses
            ));
        }

        LOGGER.info("Found {} groups for student {}", groupStatuses.size(), studentId);

        return groupStatuses;
    }

    /**
     * Récupère le statut de paiement pour toutes les sessions d'une série.
     *
     * IMPORTANT: Retourne UNIQUEMENT les sessions où l'étudiant a une fiche de présence
     * (attendance record) ou est lié via un rattrapage (catch-up).
     *
     * @param studentId l'ID de l'étudiant
     * @param series la série de sessions
     * @return la liste des statuts de paiement par session
     */
    private List<SessionPaymentStatus> getSessionPaymentStatuses(Long studentId, SessionSeriesEntity series) {
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

            // Calculer le montant dû pour cette session
            double sessionPrice = session.getGroup().getPrice().getPrice();
            boolean isPresent = attendance.getIsPresent() != null && attendance.getIsPresent();
            boolean isPaidEvenIfAbsent = false;  // TODO: Implémenter la logique "absence payable"

            // La session est payable si l'étudiant est présent OU si absence est payable
            boolean isPayable = isPresent || isPaidEvenIfAbsent;
            double amountDue = isPayable ? sessionPrice : 0.0;

            // Calculer le montant payé pour cette session
            // IMPORTANT: Ignorer les paiements CANCELLED
            List<PaymentDetailEntity> details = paymentDetailRepository
                .findByPayment_StudentIdAndSessionId(studentId, session.getId());

            double amountPaid = details.stream()
                .filter(detail -> detail.getActive() &&
                       (detail.getPermanentlyDeleted() == null || !detail.getPermanentlyDeleted()) &&
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
                amountPaid
            ));
        }

        return result;
    }

    /**
     * Vérifie si le paiement pour une session spécifique est en retard.
     *
     * @param studentId l'ID de l'étudiant
     * @param sessionId l'ID de la session
     * @return true si le paiement est en retard
     */
    private boolean isPaymentOverdueForSession(Long studentId, Long sessionId) {
        List<PaymentDetailEntity> details = paymentDetailRepository
            .findByPayment_StudentIdAndSessionId(studentId, sessionId);

        SessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found with ID: " + sessionId));

        double sessionCost = session.getGroup().getPrice().getPrice();

        // IMPORTANT: Ignorer les paiements CANCELLED
        double totalPaidForSession = details.stream()
            .filter(detail -> !"CANCELLED".equals(detail.getPayment().getStatus()))
            .mapToDouble(PaymentDetailEntity::getAmountPaid)
            .sum();

        return totalPaidForSession < sessionCost;
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
     * Récupère les sessions auxquelles un étudiant a assisté mais qu'il n'a pas payées.
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
