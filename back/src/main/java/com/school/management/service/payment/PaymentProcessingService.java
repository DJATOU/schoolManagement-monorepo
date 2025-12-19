package com.school.management.service.payment;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class PaymentProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProcessingService.class);

    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final SessionRepository sessionRepository;
    private final SessionSeriesRepository sessionSeriesRepository;
    private final PaymentDistributionService distributionService;

    public PaymentProcessingService(
            PaymentRepository paymentRepository,
            StudentRepository studentRepository,
            GroupRepository groupRepository,
            SessionRepository sessionRepository,
            SessionSeriesRepository sessionSeriesRepository,
            PaymentDistributionService distributionService) {
        this.paymentRepository = paymentRepository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.distributionService = distributionService;
    }

    @Transactional
    public PaymentEntity processPayment(Long studentId, Long groupId, Long sessionSeriesId, double amountPaid) {
        LOGGER.info("Processing payment for student {} on series {} - amount: {}",
                studentId, sessionSeriesId, amountPaid);

        StudentEntity student = studentRepository.findById(studentId)
                .orElseThrow(() -> new CustomServiceException("Student not found with ID: " + studentId));

        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomServiceException("Group not found with ID: " + groupId));

        SessionSeriesEntity sessionSeries = sessionSeriesRepository.findById(sessionSeriesId)
                .orElseThrow(() -> new CustomServiceException("Session series not found with ID: " + sessionSeriesId));

        double attendedSessionsCost = distributionService.calculateAttendedSessionsCost(studentId, sessionSeriesId, group);

        distributionService.canProcessPayment(studentId, sessionSeriesId, group, amountPaid);

        PaymentEntity payment = getOrCreateSeriesPayment(student, group, sessionSeriesId, attendedSessionsCost);

        double previousTotal = payment.getAmountPaid();
        double newTotal = previousTotal + amountPaid;
        payment.setAmountPaid(newTotal);
        payment.setPaymentDate(new Date());

        if (newTotal >= attendedSessionsCost) {
            payment.setStatus("COMPLETED");
            LOGGER.info("Payment COMPLETED for student {} on series {} - Total: {}/{}",
                    studentId, sessionSeriesId, newTotal, attendedSessionsCost);
        } else {
            payment.setStatus("IN_PROGRESS");
            LOGGER.info("Payment IN_PROGRESS for student {} on series {} - Total: {}/{}",
                    studentId, sessionSeriesId, newTotal, attendedSessionsCost);
        }

        payment = paymentRepository.save(payment);

        distributionService.distributePayment(payment, sessionSeriesId, amountPaid);

        LOGGER.info("Payment processed successfully - Payment ID: {}, Status: {}",
                payment.getId(), payment.getStatus());

        return payment;
    }

    @Transactional
    public PaymentEntity processCatchUpPayment(Long studentId, Long sessionId, double amountPaid) {
        LOGGER.info("Processing catch-up payment for student {} on session {} - amount: {}",
                studentId, sessionId, amountPaid);

        StudentEntity student = studentRepository.findById(studentId)
                .orElseThrow(() -> new CustomServiceException("Student not found with ID: " + studentId));

        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomServiceException("Session not found with ID: " + sessionId));

        GroupEntity group = session.getGroup();
        SessionSeriesEntity sessionSeries = session.getSessionSeries();

        if (sessionSeries == null) {
            throw new CustomServiceException("Session is not part of a series");
        }

        Long sessionSeriesId = sessionSeries.getId();

        double sessionCost = group.getPrice().getPrice();

        if (amountPaid > sessionCost) {
            throw new CustomServiceException(
                    String.format("Le montant payé (%.2f DA) dépasse le coût de la session (%.2f DA)",
                            amountPaid, sessionCost));
        }

        PaymentEntity payment = getOrCreateSeriesPayment(student, group, sessionSeriesId, sessionCost);

        double previousTotal = payment.getAmountPaid();
        double newTotal = previousTotal + amountPaid;
        payment.setAmountPaid(newTotal);
        payment.setPaymentDate(new Date());

        double attendedSessionsCost = distributionService.calculateAttendedSessionsCost(
                studentId, sessionSeriesId, group);

        if (newTotal >= attendedSessionsCost) {
            payment.setStatus("COMPLETED");
            LOGGER.info("Payment COMPLETED for student {} - Total: {}/{}",
                    studentId, newTotal, attendedSessionsCost);
        } else {
            payment.setStatus("IN_PROGRESS");
            LOGGER.info("Payment IN_PROGRESS for student {} - Total: {}/{}",
                    studentId, newTotal, attendedSessionsCost);
        }

        payment = paymentRepository.save(payment);

        distributionService.distributePayment(payment, sessionSeriesId, amountPaid);

        LOGGER.info("Catch-up payment processed successfully - Payment ID: {}, Status: {}",
                payment.getId(), payment.getStatus());

        return payment;
    }

    private PaymentEntity getOrCreateSeriesPayment(StudentEntity student, GroupEntity group,
                                                   Long sessionSeriesId, double attendedSessionsCost) {
        // IMPORTANT: Utiliser findActive... pour ignorer les paiements CANCELLED
        // Cela permet de créer un nouveau paiement même si un paiement CANCELLED existe
        return paymentRepository.findActiveByStudentIdAndSessionSeriesId(student.getId(), sessionSeriesId)
                .map(existingPayment -> {
                    LOGGER.info("Using existing active payment {} for student {} and series {}",
                            existingPayment.getId(), student.getId(), sessionSeriesId);
                    return existingPayment;
                })
                .orElseGet(() -> {
                    LOGGER.info("Creating new payment for student {} and series {} (no active payment found, CANCELLED payments are ignored)",
                            student.getId(), sessionSeriesId);

                    SessionSeriesEntity sessionSeries = sessionSeriesRepository.findById(sessionSeriesId)
                            .orElseThrow(() -> new CustomServiceException("Session series not found"));

                    PaymentEntity newPayment = PaymentEntity.builder()
                            .student(student)
                            .group(group)
                            .sessionSeries(sessionSeries)
                            .amountPaid(0.0)
                            .paymentDate(new Date())
                            .status("IN_PROGRESS")
                            .build();

                    return paymentRepository.save(newPayment);
                });
    }
}