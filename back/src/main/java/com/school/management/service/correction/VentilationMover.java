package com.school.management.service.correction;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.service.payment.PaymentDistributionService;
import com.school.management.service.payment.PaymentDistributionService.Placement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Déplacement de ventilation, jamais d'argent (spec admin-corrections, exigence 5.9, D6).
 *
 * <p>Quand une correction rend non facturable une Séance qui porte une ventilation, ses lignes sont
 * retirées et leur montant ventilé de nouveau sur les Séances facturables <strong>de la même Série
 * et du même Encaissement</strong>, par la règle ordinaire ({@link PaymentDistributionService#place}).
 * Le cumul de la Série est la somme des Imputations, que rien ici ne touche : montant versé, dû et
 * statut sont inchangés, seule la répartition affichée bouge (propriété P6).</p>
 *
 * <p>Ce qu'aucune Séance ne peut plus recevoir reste non ventilé, et c'est dit : traiter ce
 * trop-perçu — report, remboursement — reste une décision explicite, par les outils existants.</p>
 */
@Service
public class VentilationMover {

    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentDistributionService distribution;

    public VentilationMover(PaymentDetailRepository paymentDetailRepository,
                            PaymentDistributionService distribution) {
        this.paymentDetailRepository = paymentDetailRepository;
        this.distribution = distribution;
    }

    /**
     * Ce qu'un déplacement a fait.
     *
     * @param effects un effet par ligne déplacée, puis un par reliquat
     * @param moved   vrai si une ligne a été retirée, donc la Série écrite
     */
    public record Move(List<CorrectionEffect> effects, boolean moved) {
    }

    /**
     * Déplace la ventilation de l'étudiant portée par des Séances devenues non facturables.
     *
     * @param studentId      l'étudiant
     * @param series         la Série
     * @param lostSessionIds Séances de la Série qui ont cessé d'être facturables
     * @return les effets, à annoncer dans l'Aperçu
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Move move(Long studentId, SessionSeriesEntity series, Set<Long> lostSessionIds) {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(series, "series");
        List<PaymentDetailEntity> lines = paymentDetailRepository
                .findByPayment_StudentIdAndSession_SessionSeriesId(studentId, series.getId()).stream()
                .filter(line -> Boolean.TRUE.equals(line.getActive()))
                .filter(line -> lostSessionIds.contains(line.getSession().getId()))
                .sorted(Comparator.comparing((PaymentDetailEntity line) -> line.getSession().getSessionTimeStart(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PaymentDetailEntity::getId))
                .toList();

        List<CorrectionEffect> effects = new ArrayList<>();
        for (PaymentDetailEntity line : lines) {
            line.setActive(false);
            paymentDetailRepository.saveAndFlush(line);

            BigDecimal amount = money(BigDecimal.valueOf(line.getAmountPaid()));
            Placement placement = distribution.place(line.getEncashmentAllocation(), amount);
            String receipt = line.getEncashmentAllocation().getEncashment().getReceiptNumber();
            Date start = line.getSession().getSessionTimeStart();
            String from = start == null ? "une séance non datée" : "la séance du " + day(line.getSession());
            String targets = placement.lines().stream()
                    .map(placed -> day(placed.getSession()) + " (" + AmountEffectWriter.money(
                            money(BigDecimal.valueOf(placed.getAmountPaid()))) + " DA)")
                    .collect(Collectors.joining(", "));
            effects.add(new CorrectionEffect(CorrectionEffectType.VENTILATION_MOVED,
                    "Reçu " + receipt + " : " + AmountEffectWriter.money(amount) + " DA ventilés sur "
                            + from + " (« " + series.getName() + " »), devenue non facturable, "
                            + (placement.lines().isEmpty() ? "ne trouvent aucune autre séance"
                                    : "passent sur " + targets)));
            if (placement.unplaced().signum() > 0) {
                // Le trop-perçu de la série, lui, est annoncé par la correction : ici, seule la
                // répartition est en cause.
                effects.add(new CorrectionEffect(CorrectionEffectType.VENTILATION_MOVED,
                        "Reçu " + receipt + " : " + AmountEffectWriter.money(placement.unplaced())
                                + " DA restent non ventilés sur « " + series.getName()
                                + " » : ni reportés ni remboursés par cette correction"));
            }
        }
        return new Move(effects, !lines.isEmpty());
    }

    /** Jour d'une séance qui reçoit une part : facturable, donc datée ou suivie ; « non datée » sinon. */
    private static String day(SessionEntity session) {
        Date start = session.getSessionTimeStart();
        return start == null ? "non datée" : EnrolmentWindow.format(EnrolmentWindow.dayOf(start));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
