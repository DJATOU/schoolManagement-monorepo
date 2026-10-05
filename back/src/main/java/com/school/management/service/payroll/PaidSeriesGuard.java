package com.school.management.service.payroll;

import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.TeacherPayoutEntity;
import com.school.management.repository.TeacherPayoutRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Une série payée ne redevient pas non terminée sans que l'administratrice le sache (spec
 * teacher-payroll, exigence 8, D9).
 *
 * <p>Appelée par chaque écriture qui retirerait une séance validée d'une série, ou y en remettrait
 * une non validée : dévalidation, suppression, désactivation, réactivation, changement de groupe.
 * Le refus nomme les paies à annuler, la plus récente d'abord : c'est l'ordre qu'impose la règle de
 * correction (D3). Elle s'ajoute à la garde de l'année close, elle ne la remplace pas.</p>
 */
@Service
public class PaidSeriesGuard {

    private final TeacherPayoutRepository payoutRepository;

    public PaidSeriesGuard(TeacherPayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    /**
     * Refuse si la série porte une paie active.
     *
     * @param series    série de la séance visée ; sans série, rien n'est payé
     * @param attempted l'opération refusée, à l'infinitif : « dévalider une de ses séances »
     * @throws CustomServiceException 409 nommant les paies actives de la série
     */
    public void assertNoActivePayout(SessionSeriesEntity series, String attempted) {
        if (series == null || series.getId() == null) {
            return;
        }
        List<TeacherPayoutEntity> active = payoutRepository.findActiveForSeries(series.getId());
        if (active.isEmpty()) {
            return;
        }
        String numbers = active.stream().map(TeacherPayoutEntity::getPayoutNumber).collect(Collectors.joining(", "));
        String what = active.size() == 1
                ? "annulez cette paie"
                : "annulez ces paies, en commençant par " + active.get(active.size() - 1).getPayoutNumber() + ",";
        throw new CustomServiceException("La série « " + series.getName() + " » est payée à l'enseignant ("
                + numbers + ") : " + what + " avant de " + attempted + ".", HttpStatus.CONFLICT);
    }
}
