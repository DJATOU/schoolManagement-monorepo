package com.school.management.repository;

import com.school.management.persistance.ReceiptCounterEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptCounterRepository extends JpaRepository<ReceiptCounterEntity, Long> {

    /**
     * Le compteur, verrouillé en écriture jusqu'à la fin de la transaction.
     *
     * <p>Un second encaissement concurrent attend ici que le premier ait validé ou annulé : les
     * numéros sont attribués l'un après l'autre, jamais en double.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ReceiptCounterEntity c WHERE c.id = " + ReceiptCounterEntity.SINGLETON_ID)
    Optional<ReceiptCounterEntity> lockSingleton();
}
