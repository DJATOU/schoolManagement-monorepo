package com.school.management.service;

import com.school.management.persistance.LevelEntity;
import com.school.management.repository.LevelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service d'ordonnancement des niveaux (Level_Sequence).
 *
 * <p>Interprète le champ {@code levelSequence} pour répondre aux questions de promotion :
 * quel est le niveau suivant et quel est le niveau le plus élevé (Highest_Level).</p>
 *
 * <p>La logique de comparaison est <strong>pure</strong> : elle opère uniquement sur la liste
 * fournie en argument. Le dépôt ({@link LevelRepository}) sert exclusivement au chargement des
 * niveaux, jamais à la logique d'ordonnancement, afin de rester testable sans base de données.</p>
 */
@Service
public class LevelSequenceService {

    private final LevelRepository levelRepository;

    @Autowired
    public LevelSequenceService(LevelRepository levelRepository) {
        this.levelRepository = levelRepository;
    }

    /**
     * Charge tous les niveaux triés par {@code levelSequence} croissant.
     *
     * <p>Le tri est réappliqué de manière défensive côté service pour garantir l'ordre attendu,
     * indépendamment de la requête du dépôt.</p>
     *
     * @return la liste des niveaux triée par rang croissant.
     * @throws IllegalArgumentException si un niveau chargé possède un {@code levelSequence} nul.
     */
    @Transactional(readOnly = true)
    public List<LevelEntity> orderedBySequence() {
        List<LevelEntity> levels = levelRepository.findAllByOrderByLevelSequenceAsc();
        return sortAscending(levels);
    }

    /**
     * Retourne le niveau ayant le plus petit {@code levelSequence} strictement supérieur à celui
     * du niveau courant, ou {@link Optional#empty()} si le niveau courant est le plus élevé.
     *
     * @param current le niveau courant (non nul, {@code levelSequence} non nul).
     * @param ordered la liste des niveaux servant de référence (non nulle, sans rang nul).
     * @return le niveau suivant, ou vide si aucun.
     * @throws IllegalArgumentException si les entrées sont malformées.
     */
    public Optional<LevelEntity> nextLevel(LevelEntity current, List<LevelEntity> ordered) {
        int currentSequence = requireSequence(current, "current");
        List<LevelEntity> sorted = sortAscending(ordered);

        return sorted.stream()
                .filter(level -> level.getLevelSequence() > currentSequence)
                .findFirst();
    }

    /**
     * Indique si le niveau donné est le niveau le plus élevé (Highest_Level), c'est-à-dire s'il
     * n'existe aucun niveau suivant.
     *
     * @param level   le niveau à évaluer (non nul, {@code levelSequence} non nul).
     * @param ordered la liste des niveaux servant de référence (non nulle, sans rang nul).
     * @return {@code true} si et seulement si {@link #nextLevel(LevelEntity, List)} est vide.
     * @throws IllegalArgumentException si les entrées sont malformées.
     */
    public boolean isHighest(LevelEntity level, List<LevelEntity> ordered) {
        return nextLevel(level, ordered).isEmpty();
    }

    /**
     * Trie (publiquement) une liste de niveaux déjà validés par rang croissant.
     *
     * @param levels la liste à trier (non nulle, sans rang nul).
     * @return une nouvelle liste triée.
     * @throws IllegalArgumentException si la liste est nulle ou contient un rang nul.
     */
    public List<LevelEntity> sortBySequence(List<LevelEntity> levels) {
        return sortAscending(levels);
    }

    /**
     * Trie une copie de la liste par {@code levelSequence} croissant en validant les entrées.
     *
     * @param levels la liste à trier (non nulle).
     * @return une nouvelle liste triée.
     * @throws IllegalArgumentException si la liste est nulle ou contient un rang nul.
     */
    private List<LevelEntity> sortAscending(List<LevelEntity> levels) {
        if (levels == null) {
            throw new IllegalArgumentException("La liste des niveaux ne peut pas être nulle.");
        }
        return levels.stream()
                .map(level -> {
                    requireSequence(level, "ordered");
                    return level;
                })
                .sorted(Comparator.comparingInt(LevelEntity::getLevelSequence))
                .toList();
    }

    /**
     * Valide qu'un niveau et son {@code levelSequence} sont renseignés, puis retourne le rang.
     *
     * @param level le niveau à valider.
     * @param label libellé de l'argument pour le message d'erreur.
     * @return le {@code levelSequence} du niveau.
     * @throws IllegalArgumentException si le niveau ou son rang est nul.
     */
    private int requireSequence(LevelEntity level, String label) {
        if (level == null) {
            throw new IllegalArgumentException("Le niveau '" + label + "' ne peut pas être nul.");
        }
        Integer sequence = level.getLevelSequence();
        if (sequence == null) {
            throw new IllegalArgumentException(
                    "Le niveau '" + label + "' doit avoir un levelSequence non nul.");
        }
        return sequence;
    }
}
