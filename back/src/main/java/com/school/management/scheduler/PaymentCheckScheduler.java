package com.school.management.scheduler;

import com.school.management.repository.GroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.payment.PaymentStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler pour vérifier les paiements en retard.
 *
 * PHASE 2 REFACTORING:
 * - Utilise PaymentStatusService au lieu de l'ancien PaymentService monolithique
 * - Prêt pour implémentation future de vérifications automatiques
 */
@Component
public class PaymentCheckScheduler {

    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final PaymentStatusService paymentStatusService;

    @Autowired
    public PaymentCheckScheduler(
            StudentRepository studentRepository,
            GroupRepository groupRepository,
            PaymentStatusService paymentStatusService) {
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.paymentStatusService = paymentStatusService;
    }

    // TODO: Implémenter la vérification automatique des paiements en retard
    // Exécuter cette méthode une fois par jour à minuit
    // @Scheduled(cron = "0 0 0 * * ?")
    // public void checkOverduePayments() {
    //     List<StudentPaymentStatus> overdueStudents = ...
    //     // Send notifications, etc.
    // }
}
