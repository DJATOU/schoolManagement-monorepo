package com.school.management.repository;

import com.school.management.persistance.PayoutCounterEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayoutCounterRepository extends JpaRepository<PayoutCounterEntity, Long> {

    /**
     * Le compteur, verrouillé en écriture jusqu'à la fin de la transaction : une seconde paie
     * concurrente attend ici, les numéros sont attribués l'un après l'autre, jamais en double.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PayoutCounterEntity c WHERE c.id = " + PayoutCounterEntity.SINGLETON_ID)
    Optional<PayoutCounterEntity> lockSingleton();
}
