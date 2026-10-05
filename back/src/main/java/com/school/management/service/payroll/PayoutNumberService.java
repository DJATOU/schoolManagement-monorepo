package com.school.management.service.payroll;

import com.school.management.persistance.PayoutCounterEntity;
import com.school.management.repository.PayoutCounterRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Date;

/**
 * Numéros des paies d'enseignant, {@code PAIE-AAAA-NNNN} (spec teacher-payroll, D8).
 *
 * <p>Même mécanique que {@code ReceiptNumberService} : un compteur à une ligne, verrouillé, modifié
 * dans la transaction de la paie. Une paie refusée, ou un aperçu exécuté puis annulé, ne consomme
 * donc aucun numéro (exigence 4.6), et deux paies simultanées obtiennent deux rangs consécutifs.</p>
 */
@Service
public class PayoutNumberService {

    private static final String PREFIX = "PAIE-";

    /** Largeur minimale du rang. Au-delà de 9999, le rang s'écrit sans troncature. */
    private static final int RANK_WIDTH = 4;

    private final PayoutCounterRepository counterRepository;

    public PayoutNumberService(PayoutCounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    /**
     * Numéro suivant pour une paie enregistrée à {@code paidAt}. Le rang repart à 1 au premier
     * numéro d'une nouvelle année civile.
     *
     * @throws CustomServiceException 409 si l'horloge du poste est revenue à une année antérieure :
     *                                repartir à 1 réattribuerait un numéro déjà imprimé
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Date paidAt) {
        int year = paidAt.toInstant().atZone(ZoneId.systemDefault()).getYear();

        PayoutCounterEntity counter = counterRepository.lockSingleton().orElseGet(this::createCounter);
        if (year < counter.getCounterYear()) {
            throw new CustomServiceException(String.format(
                    "Date du serveur incohérente : année %d, alors que des paies %d existent déjà. "
                            + "Vérifiez la date et l'heure du poste avant de payer.",
                    year, counter.getCounterYear()), HttpStatus.CONFLICT);
        }

        int rank = counter.getCounterYear() == year ? counter.getLastRank() + 1 : 1;
        counter.setCounterYear(year);
        counter.setLastRank(rank);
        counterRepository.save(counter);

        return PREFIX + year + "-" + String.format("%0" + RANK_WIDTH + "d", rank);
    }

    /** Crée la ligne du compteur ; la migration V9 la crée déjà, ce chemin ne sert qu'aux tests. */
    private PayoutCounterEntity createCounter() {
        return counterRepository.saveAndFlush(new PayoutCounterEntity(PayoutCounterEntity.SINGLETON_ID, 0, 0));
    }
}
