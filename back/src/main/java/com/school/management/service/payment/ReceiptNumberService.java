package com.school.management.service.payment;

import com.school.management.persistance.ReceiptCounterEntity;
import com.school.management.repository.ReceiptCounterRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Date;

/**
 * Attribue le numéro de reçu d'un Encaissement (spec admin-corrections, exigence 1.2).
 *
 * <p>Format {@code RECU-AAAA-NNNN} : {@code AAAA} l'année civile de l'encaissement, dans le fuseau
 * de l'école, {@code NNNN} son rang dans l'année, complété à quatre chiffres.</p>
 *
 * <h2>Un compteur verrouillé, et non un calcul par MAX + 1</h2>
 * {@code RefundNumberService} lit le plus grand rang de l'année puis compte sur l'index unique pour
 * refuser un doublon, et rejoue. Ce rejeu ne peut pas réussir sur PostgreSQL : la violation de
 * contrainte interrompt toute la transaction, et Spring la marque à annuler. Ici, l'unique ligne du
 * compteur est verrouillée en écriture : deux encaissements simultanés obtiennent leurs numéros
 * l'un après l'autre, et aucune collision n'a à être rattrapée. L'index unique reste en place comme
 * filet de sécurité.
 *
 * <h2>Aucun numéro consommé pour rien</h2>
 * Le compteur avance dans la transaction de l'encaissement ({@link Propagation#MANDATORY}). Un
 * encaissement refusé, ou un aperçu exécuté puis annulé, rend son numéro : aucun trou n'apparaît
 * dans la série imprimée, et aucun numéro n'est attribué hors d'un encaissement.
 */
@Service
public class ReceiptNumberService {

    private static final String PREFIX = "RECU-";

    /** Largeur minimale du rang. Au-delà de 9999, le rang s'écrit sans troncature. */
    private static final int RANK_WIDTH = 4;

    private final ReceiptCounterRepository counterRepository;

    public ReceiptNumberService(ReceiptCounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    /**
     * Numéro de reçu suivant pour un encaissement reçu à {@code receivedAt}.
     *
     * <p>Le rang repart à 1 au premier encaissement d'une nouvelle année civile.</p>
     *
     * @param receivedAt date d'encaissement fixée par le serveur
     * @return un numéro de la forme {@code RECU-2027-0001}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Date receivedAt) {
        int year = receivedAt.toInstant().atZone(ZoneId.systemDefault()).getYear();

        ReceiptCounterEntity counter = counterRepository.lockSingleton()
                .orElseGet(this::createCounter);

        // Horloge du poste revenue en arrière d'une année : repartir à 1 réattribuerait un numéro
        // déjà imprimé sur un reçu remis à une famille. Mieux vaut refuser et le dire.
        if (year < counter.getCounterYear()) {
            throw new CustomServiceException(String.format(
                    "Date du serveur incohérente : année %d, alors que des reçus %d existent déjà. "
                            + "Vérifiez la date et l'heure du poste avant d'encaisser.",
                    year, counter.getCounterYear()), HttpStatus.CONFLICT);
        }

        int rank = counter.getCounterYear() == year ? counter.getLastRank() + 1 : 1;
        counter.setCounterYear(year);
        counter.setLastRank(rank);
        counterRepository.save(counter);

        return PREFIX + year + "-" + String.format("%0" + RANK_WIDTH + "d", rank);
    }

    /**
     * Crée la ligne du compteur. La migration V6 la crée déjà : ce chemin ne sert que sur un schéma
     * généré par Hibernate, celui des tests.
     */
    private ReceiptCounterEntity createCounter() {
        return counterRepository.saveAndFlush(
                new ReceiptCounterEntity(ReceiptCounterEntity.SINGLETON_ID, 0, 0));
    }
}
