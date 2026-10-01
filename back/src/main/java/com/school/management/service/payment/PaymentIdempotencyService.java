package com.school.management.service.payment;

import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.PaymentIdempotencyEntity;
import com.school.management.repository.EncashmentAllocationRepository;
import com.school.management.repository.PaymentIdempotencyRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Distingue le rejeu d'un encaissement d'un second encaissement réel.
 *
 * <p><b>Pourquoi ce service existe.</b> Un double clic sur « Encaisser » et un second versement
 * du même montant le même jour produisent une requête rigoureusement identique. Accepter les
 * deux inscrit au registre de l'argent qui n'est jamais entré en caisse ; refuser les deux perd
 * un versement réel. Aucune donnée de la requête ne les sépare, seule l'intention le fait : le
 * client engendre une clé à l'ouverture du formulaire, si bien qu'un rejeu porte la même clé et
 * qu'un nouvel encaissement en porte une neuve. Le serveur n'a plus rien à deviner, et aucun
 * seuil de temps n'entre en jeu — un délai n'aurait déplacé l'arbitraire que vers la vitesse de
 * frappe de la caissière.</p>
 *
 * <p><b>Sans clé, le comportement d'avant est conservé.</b> La clé est facultative. Un appelant
 * qui n'en fournit pas obtient exactement l'ancien comportement, chaque requête étant un
 * encaissement. Rendre la clé obligatoire aurait cassé tous les clients existants d'un seul coup,
 * et un défaut engendré côté serveur n'aurait rien distingué : deux requêtes auraient reçu deux
 * clés, donc deux versements, ce que ce service existe pour éviter.</p>
 *
 * <p><b>Deux chemins, une seule règle</b> (spec admin-corrections, exigence 1.7). Le versement de
 * série et le rattrapage partagent la clé et l'empreinte. Le rattrapage y ajoute sa séance : deux
 * rattrapages du même montant sur deux séances d'une même série sont deux encaissements, et une
 * clé passée d'un chemin à l'autre est une clé réutilisée, jamais un rejeu.</p>
 */
@Service
public class PaymentIdempotencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentIdempotencyService.class);

    /** Longueur maximale acceptée, alignée sur la colonne. */
    private static final int MAX_KEY_LENGTH = 100;

    private final PaymentIdempotencyRepository idempotencyRepository;
    private final EncashmentAllocationRepository allocationRepository;

    public PaymentIdempotencyService(PaymentIdempotencyRepository idempotencyRepository,
                                     EncashmentAllocationRepository allocationRepository) {
        this.idempotencyRepository = idempotencyRepository;
        this.allocationRepository = allocationRepository;
    }

    /**
     * Valide la forme d'une clé fournie par le client.
     *
     * <p>Une clé vide n'est pas une absence de clé : elle traduit un client qui a cru en envoyer
     * une. La refuser vaut mieux que la traiter comme une absence, ce qui rétablirait
     * silencieusement le double encaissement que l'appelant cherchait à éviter.</p>
     *
     * @param idempotencyKey la clé brute, éventuellement nulle
     * @return la clé nettoyée, ou {@code null} si aucune clé n'a été fournie
     * @throws CustomServiceException 400 si la clé est vide ou trop longue
     */
    public String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.isEmpty()) {
            throw new CustomServiceException(
                    "Clé d'idempotence vide : fournir une clé ou ne pas envoyer l'en-tête.",
                    HttpStatus.BAD_REQUEST);
        }
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new CustomServiceException(
                    "Clé d'idempotence trop longue : " + trimmed.length() + " caractères pour un "
                            + "maximum de " + MAX_KEY_LENGTH + ".",
                    HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    /**
     * Retrouve le résultat d'un versement de série déjà traité sous cette clé.
     *
     * <p>L'empreinte de la requête est confrontée à celle conservée. Une clé réutilisée pour un
     * versement différent est un défaut du client, jamais un rejeu : la signaler vaut mieux que
     * renvoyer le résultat d'un autre encaissement, qui produirait un reçu portant un montant que
     * personne n'a versé.</p>
     *
     * @param key       la clé normalisée, ou {@code null} si l'appelant n'en fournit pas
     * @param studentId l'étudiant de la requête courante
     * @param groupId   le groupe de la requête courante
     * @param seriesId  la série visée par la requête courante
     * @param amount    le montant de la requête courante, échelle monétaire
     * @return le résultat du traitement original si la clé a déjà servi, vide sinon
     * @throws CustomServiceException 409 si la clé a servi pour un encaissement différent, y
     *                                compris un rattrapage
     */
    public Optional<PaymentAllocationResult> findReplay(String key, Long studentId, Long groupId,
                                                        Long seriesId, BigDecimal amount) {
        return find(key).map(record -> {
            boolean sameRequest = record.getSessionId() == null
                    && record.getStudentId().equals(studentId)
                    && record.getGroupId().equals(groupId)
                    && record.getSessionSeriesId().equals(seriesId)
                    && record.getAmountReceived().compareTo(amount) == 0;
            return replay(record, key, sameRequest);
        });
    }

    /**
     * Retrouve le résultat d'un encaissement de rattrapage déjà traité sous cette clé.
     *
     * <p>L'empreinte porte sur ce que la requête désigne : l'étudiant, la séance rattrapée et le
     * montant. Groupe et série s'en déduisent et n'ont pas à être comparés.</p>
     *
     * @param key       la clé normalisée, ou {@code null} si l'appelant n'en fournit pas
     * @param studentId l'étudiant de la requête courante
     * @param sessionId la séance payée par la requête courante
     * @param amount    le montant de la requête courante, échelle monétaire
     * @return le résultat du traitement original si la clé a déjà servi, vide sinon
     * @throws CustomServiceException 409 si la clé a servi pour un encaissement différent, y
     *                                compris un versement de série
     */
    public Optional<PaymentAllocationResult> findCatchUpReplay(String key, Long studentId, Long sessionId,
                                                               BigDecimal amount) {
        return find(key).map(record -> {
            boolean sameRequest = sessionId != null
                    && sessionId.equals(record.getSessionId())
                    && record.getStudentId().equals(studentId)
                    && record.getAmountReceived().compareTo(amount) == 0;
            return replay(record, key, sameRequest);
        });
    }

    /**
     * Conserve l'empreinte d'un encaissement qui vient d'être traité.
     *
     * <p>L'écriture prend place dans la transaction de l'encaissement : si la ventilation échoue,
     * l'empreinte disparaît avec elle et une nouvelle tentative reste possible. Une empreinte
     * survivant à un échec bloquerait la reprise d'un versement qui n'a jamais abouti.</p>
     *
     * @param key              la clé normalisée, ou {@code null} pour ne rien conserver
     * @param result           le résultat du traitement, Encaissement compris
     * @param catchUpSessionId la séance payée pour un rattrapage, {@code null} pour un versement
     *                         de série
     */
    public void remember(String key, PaymentAllocationResult result, Long catchUpSessionId) {
        if (key == null) {
            return;
        }
        EncashmentEntity encashment = result.encashment();
        idempotencyRepository.save(PaymentIdempotencyEntity.builder()
                .idempotencyKey(key)
                .studentId(result.studentId())
                .groupId(result.groupId())
                .sessionSeriesId(result.seriesId())
                .sessionId(catchUpSessionId)
                .amountReceived(result.amountReceived())
                .amountAllocated(result.amountAllocated())
                .payment(result.payment())
                .originPaymentDate(encashment.getReceivedAt())
                .encashment(encashment)
                .build());
    }

    // ------------------------------------------------------------------
    // Interne
    // ------------------------------------------------------------------

    private Optional<PaymentIdempotencyEntity> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return idempotencyRepository.findByIdempotencyKey(key);
    }

    private PaymentAllocationResult replay(PaymentIdempotencyEntity record, String key, boolean sameRequest) {
        if (!sameRequest) {
            throw new CustomServiceException(
                    "La clé d'idempotence " + key + " a déjà servi pour un encaissement différent "
                            + "(étudiant " + record.getStudentId() + ", série "
                            + record.getSessionSeriesId()
                            + (record.getSessionId() == null ? "" : ", rattrapage de la séance " + record.getSessionId())
                            + ", " + record.getAmountReceived().toPlainString() + " DA). Utiliser une clé "
                            + "neuve pour un nouveau versement.",
                    HttpStatus.CONFLICT);
        }

        LOGGER.info("Rejeu détecté sur la clé {} : aucun second versement créé, résultat du "
                + "traitement original restitué.", key);

        return rebuild(record);
    }

    /**
     * Reconstitue le résultat original depuis les Imputations de l'Encaissement.
     *
     * <p>Les Imputations sont relues plutôt que recopiées : elles sont la source de l'argent
     * compté sur chaque série. Actives ou non : si l'Encaissement a été annulé depuis, le rejeu
     * rend tout de même la répartition telle qu'elle a été faite, c'est-à-dire la réponse
     * originale. La ligne de paiement, elle, est relue telle qu'elle est aujourd'hui.</p>
     */
    private PaymentAllocationResult rebuild(PaymentIdempotencyEntity record) {
        EncashmentEntity encashment = Objects.requireNonNull(record.getEncashment(),
                () -> "Empreinte d'idempotence " + record.getIdempotencyKey() + " sans encaissement.");

        List<EncashmentAllocationEntity> allocations =
                allocationRepository.findByEncashmentIdOrderByIdAsc(encashment.getId());

        BigDecimal direct = allocations.stream()
                .filter(allocation -> !Boolean.TRUE.equals(allocation.getCarriedOver()))
                .map(EncashmentAllocationEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(PaymentCostCalculator.MONEY_SCALE, PaymentCostCalculator.MONEY_ROUNDING);

        List<PaymentAllocationResult.CarriedOverAmount> carryOvers = allocations.stream()
                .filter(allocation -> Boolean.TRUE.equals(allocation.getCarriedOver()))
                .map(allocation -> new PaymentAllocationResult.CarriedOverAmount(
                        allocation.getSeries().getId(),
                        allocation.getSeries().getName(),
                        allocation.getAmount()))
                .toList();

        return new PaymentAllocationResult(
                record.getStudentId(),
                record.getGroupId(),
                record.getSessionSeriesId(),
                record.getAmountReceived(),
                direct,
                carryOvers,
                record.getPayment(),
                encashment);
    }
}
