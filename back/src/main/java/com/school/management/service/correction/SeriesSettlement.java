package com.school.management.service.correction;

import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.service.payment.BillableSessionsResolver;
import com.school.management.service.payment.EncashmentService;
import com.school.management.service.payment.PaymentCostResolver;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ce qu'une correction de présence ou de dates entraîne sur les Séries d'un étudiant, une fois les
 * présences écrites (spec admin-corrections, exigence 5.9, D6) :
 * <ul>
 *   <li>la ventilation d'une séance devenue non facturable passe sur les autres séances facturables
 *       de sa série, sans changer de série ni d'Encaissement ({@link VentilationMover}) ;</li>
 *   <li>le statut stocké de chaque ligne de paiement suit le coût, qui a pu changer ;</li>
 *   <li>un trop-perçu apparu est annoncé, ni reporté ni remboursé.</li>
 * </ul>
 *
 * <p>En deux temps, autour de l'écriture : {@link #capture} avant, {@link #settle} après. Partagé par
 * les corrections d'inscription et de présence, pour qu'une même cause produise partout les mêmes
 * effets, annoncés dans les mêmes mots.</p>
 */
@Component
public class SeriesSettlement {

    private final BillableSessionsResolver billableSessionsResolver;
    private final PaymentCostResolver costResolver;
    private final PaymentRepository paymentRepository;
    private final EncashmentService encashmentService;
    private final VentilationMover ventilationMover;

    public SeriesSettlement(BillableSessionsResolver billableSessionsResolver, PaymentCostResolver costResolver,
                            PaymentRepository paymentRepository, EncashmentService encashmentService,
                            VentilationMover ventilationMover) {
        this.billableSessionsResolver = billableSessionsResolver;
        this.costResolver = costResolver;
        this.paymentRepository = paymentRepository;
        this.encashmentService = encashmentService;
        this.ventilationMover = ventilationMover;
    }

    /**
     * État des Séries avant la correction : séances facturables et trop-perçu de chacune.
     *
     * @param series Séries que la correction peut toucher ; un doublon est ignoré, l'ordre est celui
     *               des identifiants, celui de l'Aperçu
     */
    public Before capture(Long studentId, Collection<SessionSeriesEntity> series) {
        Objects.requireNonNull(studentId, "studentId");
        Map<Long, SessionSeriesEntity> byId = new LinkedHashMap<>();
        series.stream()
                .sorted(Comparator.comparing(SessionSeriesEntity::getId))
                .forEach(one -> byId.putIfAbsent(one.getId(), one));
        Map<Long, Set<Long>> billable = new HashMap<>();
        Map<Long, BigDecimal> excess = new HashMap<>();
        for (SessionSeriesEntity one : byId.values()) {
            billable.put(one.getId(), billableIds(studentId, one));
            excess.put(one.getId(), excessOf(studentId, one));
        }
        return new Before(studentId, List.copyOf(byId.values()), billable, excess);
    }

    /**
     * Tire les conséquences de la correction déjà écrite, et les ajoute aux effets.
     *
     * @return les Séries dont la ventilation ou la ligne de paiement a été réécrite
     */
    public Set<SeriesKey> settle(Before before, List<CorrectionEffect> effects) {
        Long studentId = before.studentId();
        Set<SeriesKey> touched = new HashSet<>();
        for (SessionSeriesEntity series : before.series()) {
            SeriesKey key = new SeriesKey(studentId, series.getId());
            Set<Long> lost = new HashSet<>(before.billable().get(series.getId()));
            lost.removeAll(billableIds(studentId, series));
            if (!lost.isEmpty()) {
                VentilationMover.Move move = ventilationMover.move(studentId, series, lost);
                effects.addAll(move.effects());
                if (move.moved()) {
                    touched.add(key);
                }
            }
            paymentRepository.findByStudentIdAndGroupIdAndSessionSeriesId(studentId, series.getGroup().getId(),
                    series.getId()).ifPresent(payment -> {
                        // Le coût a pu changer : le statut stocké de la ligne de paiement suit.
                        encashmentService.refreshSeriesCumul(payment);
                        touched.add(key);
                    });
            announceExcess(studentId, series, before.excess().get(series.getId()), effects);
        }
        return touched;
    }

    private Set<Long> billableIds(Long studentId, SessionSeriesEntity series) {
        return billableSessionsResolver.resolve(studentId, series.getId()).billable().stream()
                .map(SessionEntity::getId)
                .collect(Collectors.toSet());
    }

    /** Excédent du versé sur le coût ; zéro sans excédent. */
    private BigDecimal excessOf(Long studentId, SessionSeriesEntity series) {
        PaymentCostResolver.PaymentStatusResult status = costResolver.resolve(studentId, series.getId());
        return status.amountPaid().subtract(status.monthTotalCost()).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Trop-perçu apparu ou accru par la correction : annoncé, jamais traité par elle (5.9). */
    private void announceExcess(Long studentId, SessionSeriesEntity series, BigDecimal before,
                                List<CorrectionEffect> effects) {
        BigDecimal after = excessOf(studentId, series);
        if (after.compareTo(before) > 0) {
            effects.add(new CorrectionEffect(CorrectionEffectType.EXCESS_LEFT, "Trop-perçu de "
                    + AmountEffectWriter.money(after) + " DA sur « " + series.getName() + " » : ni reporté ni "
                    + "remboursé par cette correction"));
        }
    }

    /** Séries d'un étudiant photographiées avant une correction. */
    public record Before(Long studentId, List<SessionSeriesEntity> series, Map<Long, Set<Long>> billable,
                         Map<Long, BigDecimal> excess) {
    }
}
