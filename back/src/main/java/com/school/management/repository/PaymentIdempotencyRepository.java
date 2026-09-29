package com.school.management.repository;

import com.school.management.persistance.PaymentIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Accès aux empreintes d'encaissements déjà traités (clé d'idempotence).
 *
 * <p>Une seule lecture est nécessaire : retrouver l'empreinte portant une clé donnée, pour
 * décider si la requête est un rejeu ou un nouvel encaissement. Aucun filtre sur
 * {@code active} : une empreinte désactivée rouvrirait la porte au double encaissement qu'elle
 * existe précisément pour fermer.</p>
 */
@Repository
public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotencyEntity, Long> {

    /**
     * Empreinte associée à une clé d'idempotence.
     *
     * @param idempotencyKey la clé fournie par le client
     * @return l'empreinte si cette clé a déjà servi, vide sinon
     */
    Optional<PaymentIdempotencyEntity> findByIdempotencyKey(String idempotencyKey);
}
