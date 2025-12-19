package com.school.management.service.payment;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentDistributionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentDistributionService.class);

    private final SessionRepository sessionRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final AttendanceRepository attendanceRepository;

    public PaymentDistributionService(
            SessionRepository sessionRepository,
            PaymentDetailRepository paymentDetailRepository,
            AttendanceRepository attendanceRepository) {
        this.sessionRepository = sessionRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional
    public void distributePayment(PaymentEntity payment, Long sessionSeriesId, double amountPaid) {
        LOGGER.info("Distributing payment {} of amount {} for series {}",
                payment.getId(), amountPaid, sessionSeriesId);

        Long studentId = payment.getStudent().getId();

        List<AttendanceEntity> existingAttendances = attendanceRepository
                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, sessionSeriesId);

        List<SessionEntity> sessions;
        if (!existingAttendances.isEmpty()) {
            LOGGER.info("Student {} has {} attendances - using CATCH-UP distribution mode",
                    studentId, existingAttendances.size());
            sessions = getAttendedSessionsForStudent(studentId, sessionSeriesId);
        } else {
            LOGGER.info("Student {} has no attendances - using NORMAL distribution mode", studentId);
            sessions = getAllSessionsForSeries(sessionSeriesId);
        }

        if (sessions.isEmpty()) {
            LOGGER.warn("No sessions found for series: {}", sessionSeriesId);
            return;
        }

        double remaining = amountPaid;
        double pricePerSession = payment.getGroup().getPrice().getPrice();

        LOGGER.debug("Price per session: {}, Total sessions: {}", pricePerSession, sessions.size());

        for (SessionEntity session : sessions) {
            if (remaining <= 0) {
                LOGGER.debug("No remaining amount, stopping distribution");
                break;
            }

            remaining = distributeToSession(payment, session, pricePerSession, remaining);
        }

        double totalCost = calculateTotalCost(payment.getGroup());
        if (payment.getAmountPaid() >= totalCost) {
            double surplus = payment.getAmountPaid() - totalCost;
            if (surplus > 0) {
                throw new CustomServiceException(
                        "Le paiement a été complété. Le montant excédentaire de " + surplus + " euros sera remboursé.",
                        HttpStatus.OK
                );
            }
        }

        LOGGER.info("Payment distribution completed. Remaining: {}", remaining);
    }

    private double distributeToSession(PaymentEntity payment, SessionEntity session,
                                       double pricePerSession, double remaining) {
        Optional<PaymentDetailEntity> existingDetail = paymentDetailRepository
                .findByPaymentIdAndSessionId(payment.getId(), session.getId());

        if (existingDetail.isPresent()) {
            PaymentDetailEntity detail = existingDetail.get();

            // CRITICAL: Check if payment was permanently deleted (irreversible)
            if (detail.getPermanentlyDeleted() != null && detail.getPermanentlyDeleted()) {
                throw new IllegalStateException(
                    "Cannot create a new payment for session " + session.getId() +
                    ". This session had a payment that was permanently deleted (irreversible). " +
                    "Payment detail ID: " + detail.getId()
                );
            }

            // CRITICAL: Ignore PaymentDetails from CANCELLED payments
            if ("CANCELLED".equals(detail.getPayment().getStatus())) {
                LOGGER.debug("Ignoring PaymentDetail from CANCELLED payment for session {}", session.getId());
                // Treat as if no detail exists - will create new one below
                double amountForThisSession = Math.min(pricePerSession, remaining);
                PaymentDetailEntity newDetail = PaymentDetailEntity.builder()
                        .payment(payment)
                        .session(session)
                        .amountPaid(amountForThisSession)
                        .build();
                paymentDetailRepository.save(newDetail);
                remaining -= amountForThisSession;
                LOGGER.debug("Created new PaymentDetail for session {} (ignored CANCELLED) - amount: {}",
                        session.getId(), amountForThisSession);
                return remaining;
            }

            // Only consider the payment if it's ACTIVE
            // If inactive (but not permanently deleted), treat it as if it doesn't exist (create new one)
            if (detail.getActive() != null && detail.getActive()) {
                double alreadyPaid = detail.getAmountPaid();
                double stillOwed = pricePerSession - alreadyPaid;

                if (stillOwed > 0) {
                    double amountToAdd = Math.min(stillOwed, remaining);
                    detail.setAmountPaid(alreadyPaid + amountToAdd);
                    paymentDetailRepository.save(detail);
                    remaining -= amountToAdd;

                    LOGGER.debug("Updated existing ACTIVE PaymentDetail for session {} - added: {}, new total: {}",
                            session.getId(), amountToAdd, detail.getAmountPaid());
                }
            } else {
                // Inactive detail exists (but not permanently deleted), create a new active one
                LOGGER.info("Found INACTIVE PaymentDetail for session {}, creating new active one", session.getId());
                double amountForThisSession = Math.min(pricePerSession, remaining);
                PaymentDetailEntity newDetail = PaymentDetailEntity.builder()
                        .payment(payment)
                        .session(session)
                        .amountPaid(amountForThisSession)
                        .build();
                paymentDetailRepository.save(newDetail);
                remaining -= amountForThisSession;

                LOGGER.debug("Created new ACTIVE PaymentDetail for session {} - amount: {}",
                        session.getId(), amountForThisSession);
            }
        } else {
            double amountForThisSession = Math.min(pricePerSession, remaining);
            PaymentDetailEntity newDetail = PaymentDetailEntity.builder()
                    .payment(payment)
                    .session(session)
                    .amountPaid(amountForThisSession)
                    .build();
            paymentDetailRepository.save(newDetail);
            remaining -= amountForThisSession;

            LOGGER.debug("Created new PaymentDetail for session {} - amount: {}",
                    session.getId(), amountForThisSession);
        }

        return remaining;
    }

    public List<SessionEntity> getAttendedSessionsForStudent(Long studentId, Long sessionSeriesId) {
        List<AttendanceEntity> attendances = attendanceRepository
                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, sessionSeriesId);

        return attendances.stream()
                .map(AttendanceEntity::getSession)
                .sorted(Comparator.comparing(SessionEntity::getSessionTimeStart))
                .toList();
    }

    public List<SessionEntity> getAllSessionsForSeries(Long sessionSeriesId) {
        return sessionRepository
                .findBySessionSeriesId(sessionSeriesId)
                .stream()
                .sorted(Comparator.comparing(SessionEntity::getSessionTimeStart))
                .toList();
    }

    public double calculateAttendedSessionsCost(Long studentId, Long sessionSeriesId, GroupEntity group) {
        List<AttendanceEntity> existingAttendances = attendanceRepository
                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, sessionSeriesId);

        double pricePerSession = group.getPrice().getPrice();

        if (!existingAttendances.isEmpty()) {
            int attendedSessionsCount = existingAttendances.size();
            double cost = attendedSessionsCount * pricePerSession;
            LOGGER.debug("Student {} (CATCH-UP): {} sessions attended, cost: {}",
                    studentId, attendedSessionsCount, cost);
            return cost;
        } else {
            List<SessionEntity> allSessions = getAllSessionsForSeries(sessionSeriesId);
            double cost = allSessions.size() * pricePerSession;
            LOGGER.debug("Student {} (NORMAL): {} sessions in series, cost: {}",
                    studentId, allSessions.size(), cost);
            return cost;
        }
    }

    public boolean canProcessPayment(Long studentId, Long sessionSeriesId, GroupEntity group, double newAmount) {
        double totalPaidBefore = getTotalPaidForSeries(studentId, sessionSeriesId);
        double newTotal = totalPaidBefore + newAmount;
        double expectedCost = calculateAttendedSessionsCost(studentId, sessionSeriesId, group);

        LOGGER.debug("Payment validation - Paid before: {}, New amount: {}, Expected cost: {}",
                totalPaidBefore, newAmount, expectedCost);

        if (newTotal > expectedCost) {
            double surplus = newTotal - expectedCost;
            throw new CustomServiceException(
                    String.format("Le montant payé dépasse le coût total (%d sessions) de %.2f DA. Le paiement ne peut pas être effectué.",
                            (int)(expectedCost / group.getPrice().getPrice()), surplus),
                    HttpStatus.BAD_REQUEST
            );
        }

        return true;
    }

    private double getTotalPaidForSeries(Long studentId, Long sessionSeriesId) {
        List<PaymentDetailEntity> details = paymentDetailRepository
                .findByPayment_StudentIdAndSession_SessionSeriesId(studentId, sessionSeriesId);

        // IMPORTANT: Only count ACTIVE PaymentDetails AND ignore CANCELLED payments
        // This allows re-payment after cancellation/deactivation
        return details.stream()
                .filter(detail -> detail.getActive() != null && detail.getActive())
                .filter(detail -> !"CANCELLED".equals(detail.getPayment().getStatus()))
                .mapToDouble(PaymentDetailEntity::getAmountPaid)
                .sum();
    }

    private double calculateTotalCost(GroupEntity group) {
        double pricePerSession = group.getPrice().getPrice();
        int sessionNumberPerSerie = group.getSessionNumberPerSerie();
        return pricePerSession * sessionNumberPerSerie;
    }
}