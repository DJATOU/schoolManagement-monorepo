package com.school.management.service.payment;

import com.school.management.persistance.PaymentCarryOverEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Enregistrement de la trace d'un report de versement (exigence 6.1).
 *
 * <p>Une ligne est écrite pour chaque montant imputé sur une série <em>autre</em> que celle
 * visée par l'administrateur à la saisie — soit, dans le plan de répartition, chaque
 * allocation marquée comme reportée. Une imputation directe sur la série visée ne produit
 * aucune ligne : c'est précisément l'absence de trace qui distingue un montant imputé
 * directement d'un montant reçu par report (exigence 6.4).</p>
 *
 * <p><strong>Transaction.</strong> La méthode porte un simple {@link Transactional}, donc la
 * propagation par défaut : elle rejoint la transaction de l'encaissement au lieu d'en ouvrir
 * une nouvelle. C'est délibéré et non un oubli — avec {@code REQUIRES_NEW}, l'échec du report
 * laisserait le versement enregistré sans sa trace, alors que l'exigence 5.5 demande
 * l'annulation du versement entier, y compris la part déjà imputée sur la série en cours.</p>
 *
 * <p><strong>Signature.</strong> Le service reçoit des identifiants, un montant et une date,
 * jamais le plan de répartition : la décision de répartir appartient au service d'allocation,
 * l'écriture de la trace à celui-ci. Cette frontière permet de tester chacun sans l'autre.</p>
 */
@Service
public class PaymentCarryOverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCarryOverService.class);

    private final PaymentCarryOverRepository paymentCarryOverRepository;
    private final StudentRepository studentRepository;
    private final SessionSeriesRepository sessionSeriesRepository;

    public PaymentCarryOverService(PaymentCarryOverRepository paymentCarryOverRepository,
                                   StudentRepository studentRepository,
                                   SessionSeriesRepository sessionSeriesRepository) {
        this.paymentCarryOverRepository = paymentCarryOverRepository;
        this.studentRepository = studentRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
    }

    /**
     * Enregistre un report : montant, série source, série destination, ligne de paiement
     * créditée, étudiant et date du versement d'origine (exigence 6.1).
     *
     * <p>Le montant est ramené à l'échelle monétaire <em>avant</em> le contrôle de positivité :
     * un montant de 0,004 DA arrondirait à 0,00 et écrirait une trace vide, ce qui ferait
     * apparaître dans l'historique un report qui n'a rien crédité. Il est donc refusé comme un
     * montant nul.</p>
     *
     * @param studentId         l'étudiant dont le versement a produit le surplus
     * @param sourceSeriesId    la série visée à la saisie, d'où provient le surplus
     * @param targetSeriesId    la série effectivement créditée
     * @param targetPayment     la ligne de paiement de la série destination créditée
     * @param amount            le montant reporté, strictement positif
     * @param originPaymentDate la date du versement d'origine
     * @return la trace enregistrée
     * @throws CustomServiceException 400 si un argument est absent, si le montant est nul ou
     *                                négatif, ou si source et destination sont la même série ;
     *                                404 si l'étudiant ou une série est introuvable
     */
    @Transactional
    public PaymentCarryOverEntity record(Long studentId,
                                         Long sourceSeriesId,
                                         Long targetSeriesId,
                                         PaymentEntity targetPayment,
                                         BigDecimal amount,
                                         Date originPaymentDate) {
        requirePresent(studentId, "l'identifiant de l'étudiant");
        requirePresent(sourceSeriesId, "l'identifiant de la série source");
        requirePresent(targetSeriesId, "l'identifiant de la série destination");
        requirePresent(targetPayment, "la ligne de paiement créditée");
        requirePresent(originPaymentDate, "la date du versement d'origine");
        requirePresent(amount, "le montant reporté");

        // Un report vers la série visée à la saisie n'est pas un report : ce serait une
        // imputation directe, et l'historique présenterait « série A vers série A ».
        if (sourceSeriesId.equals(targetSeriesId)) {
            throw new CustomServiceException(
                    "Un report ne peut pas viser sa propre série source (série "
                            + sourceSeriesId + ").",
                    HttpStatus.BAD_REQUEST);
        }

        BigDecimal normalizedAmount = amount
                .setScale(PaymentCostCalculator.MONEY_SCALE, PaymentCostCalculator.MONEY_ROUNDING);
        if (normalizedAmount.signum() <= 0) {
            throw new CustomServiceException(
                    "Le montant d'un report doit être strictement positif, reçu : "
                            + amount.toPlainString() + " DA.",
                    HttpStatus.BAD_REQUEST);
        }

        StudentEntity student = studentRepository.findById(studentId)
                .orElseThrow(() -> notFound("Étudiant", studentId));
        SessionSeriesEntity sourceSeries = sessionSeriesRepository.findById(sourceSeriesId)
                .orElseThrow(() -> notFound("Série source", sourceSeriesId));
        SessionSeriesEntity targetSeries = sessionSeriesRepository.findById(targetSeriesId)
                .orElseThrow(() -> notFound("Série destination", targetSeriesId));

        PaymentCarryOverEntity carryOver = PaymentCarryOverEntity.builder()
                .student(student)
                .sourceSeries(sourceSeries)
                .targetSeries(targetSeries)
                .targetPayment(targetPayment)
                .amount(normalizedAmount)
                .originPaymentDate(originPaymentDate)
                .build();

        PaymentCarryOverEntity saved = paymentCarryOverRepository.save(carryOver);

        LOGGER.info("Report enregistré : {} DA de la série {} vers la série {} pour l'étudiant {}",
                normalizedAmount.toPlainString(), sourceSeriesId, targetSeriesId, studentId);

        return saved;
    }

    private void requirePresent(Object value, String label) {
        if (value == null) {
            throw new CustomServiceException(
                    "Report impossible : " + label + " est absent.", HttpStatus.BAD_REQUEST);
        }
    }

    private CustomServiceException notFound(String label, Long id) {
        return new CustomServiceException(
                label + " introuvable pour l'identifiant : " + id, HttpStatus.NOT_FOUND);
    }
}
