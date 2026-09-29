package com.school.management.repository;

import com.school.management.persistance.LevelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevelRepository extends JpaRepository<LevelEntity, Long> {
    Optional<LevelEntity> findByName(String name);

    // Garde d'import : un niveau est désigné par son nom dans les CSV d'élèves et de groupes.
    // Deux niveaux homonymes rendraient findByName indécidable, donc l'import doit pouvoir
    // refuser le doublon avant de créer la ligne.
    boolean existsByName(String name);

    // Niveaux triés par rang croissant (ordre de promotion défini par level_sequence)
    List<LevelEntity> findAllByOrderByLevelSequenceAsc();

}
