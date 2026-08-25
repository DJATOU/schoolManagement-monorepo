package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentCarryOverDTO;
import com.school.management.dto.payment.StudentPaymentHistoryDTO;
import com.school.management.dto.payment.StudentPaymentHistoryDTO.SeriesPaymentHistoryDTO;
import com.school.management.persistance.PaymentCarryOverEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Restitution de l'historique de paiement d'un étudiant, reports compris (exigences 6.2, 6.4).
 *
 * <p>Ce service est en <strong>lecture seule</strong> et hors du chemin d'encaissement :
 * l'écriture des traces de report appartient au {@link PaymentCarryOverService}. La frontière est
 * volontaire — restituer un historique et encaisser un versement n'ont ni les mêmes contraintes
 * transactionnelles ni les mêmes appelants.</p>
 *
 * <h2>Ce que « distinguer » signifie ici (exigence 6.4)</h2>
 * La distinction ne demande pas un nouvel indicateur en base : elle se lit par la présence ou
 * l'absence d'une trace de report pointant la ligne de paiement. La part reçue par report est la
 * somme des reports actifs qui la visent ; la part imputée directement est le reste. Poser un
 * drapeau sur les {@code payment_detail} aurait dispersé la même information sur plusieurs lignes,
 * un report se ventilant couramment sur plusieurs séances.
 */
@Service
public class PaymentHistoryService {

    private static final int MONEY_SCALE = PaymentCostCalculator.MONEY_SCALE;

    private final PaymentRepository paymentRepository;
    private final PaymentCarryOverRepository paymentCarryOverRepository;

    public PaymentHistoryService(PaymentRepository paymentRepository,
                                 PaymentCarryOverRepository paymentCarryOverRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentCarryOverRepository = paymentCarryOverRepository;
    }

    /**
     * Historique de paiement d'un étudiant : une ligne par série créditée, la provenance de chaque
     * cumul, et la liste de ses reports.
     *
     * @param studentId identifiant de l'étudiant
     * @return l'historique, jamais nul ; les listes peuvent être vides
     */
    @Transactional(readOnly = true)
    public StudentPaymentHistoryDTO getStudentPaymentHistory(Long studentId) {
        Objects.requireNonNull(studentId, "studentId ne doit pas être nul.");

        List<PaymentCarryOverDTO> carryOvers = getCarryOversForStudent(studentId);

        List<SeriesPaymentHistoryDTO> series =
                paymentRepository.findActiveByStudentIdOrderByPaymentDateDesc(studentId).stream()
                        .map(payment -> toSeriesHistory(payment, carryOvers))
                        .toList();

        return new StudentPaymentHistoryDTO(studentId, series, carryOvers);
    }

    /**
     * Reports produits par les versements d'un étudiant, avec montant, série source et série
     * destination (exigence 6.2).
     *
     * @param studentId identifiant de l'étudiant
     * @return les reports actifs, par identifiant croissant ; liste vide si aucun
     */
    @Transactional(readOnly = true)
    public List<PaymentCarryOverDTO> getCarryOversForStudent(Long studentId) {
        Objects.requireNonNull(studentId, "studentId ne doit pas être nul.");

        return paymentCarryOverRepository.findByStudentIdAndActiveTrueOrderByIdAsc(studentId).stream()
                .map(this::toDto)
                .toList();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SeriesPaymentHistoryDTO toSeriesHistory(PaymentEntity payment,
                                                    List<PaymentCarryOverDTO> carryOvers) {
        BigDecimal amountPaid = money(payment.getAmountPaid());
        BigDecimal carriedIn = carryOvers.stream()
                .filter(carryOver -> payment.getId() != null
                        && payment.getId().equals(carryOver.targetPaymentId()))
                .map(PaymentCarryOverDTO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, PaymentCostCalculator.MONEY_ROUNDING);

        // La part directe est le complément. Elle est bornée à zéro : les données antérieures à
        // cette fonctionnalité peuvent porter un cumul désormais inférieur aux reports qui le
        // visent (ligne remboursée, versement désactivé), et une part directe négative
        // s'afficherait comme une dette imaginaire.
        BigDecimal directly = amountPaid.subtract(carriedIn);
        if (directly.signum() < 0) {
            directly = zero();
        }

        SessionSeriesEntity series = payment.getSessionSeries();

        return new SeriesPaymentHistoryDTO(
                payment.getId(),
                series != null ? series.getId() : null,
                series != null ? series.getName() : null,
                amountPaid,
                directly,
                carriedIn,
                payment.getStatus(),
                payment.getPaymentDate());
    }

    private PaymentCarryOverDTO toDto(PaymentCarryOverEntity carryOver) {
        SessionSeriesEntity source = carryOver.getSourceSeries();
        SessionSeriesEntity target = carryOver.getTargetSeries();
        PaymentEntity targetPayment = carryOver.getTargetPayment();

        return new PaymentCarryOverDTO(
                carryOver.getId(),
                carryOver.getStudent() != null ? carryOver.getStudent().getId() : null,
                carryOver.getAmount() != null
                        ? carryOver.getAmount().setScale(MONEY_SCALE, PaymentCostCalculator.MONEY_ROUNDING)
                        : zero(),
                source != null ? source.getId() : null,
                source != null ? source.getName() : null,
                target != null ? target.getId() : null,
                target != null ? target.getName() : null,
                targetPayment != null ? targetPayment.getId() : null,
                carryOver.getOriginPaymentDate());
    }

    private BigDecimal money(Double amount) {
        if (amount == null) {
            return zero();
        }
        return BigDecimal.valueOf(amount).setScale(MONEY_SCALE, PaymentCostCalculator.MONEY_ROUNDING);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, PaymentCostCalculator.MONEY_ROUNDING);
    }
}
