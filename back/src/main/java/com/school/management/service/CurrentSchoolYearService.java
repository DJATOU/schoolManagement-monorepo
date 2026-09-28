package com.school.management.service;

import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.NoCurrentSchoolYearException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service dédié qui répond à la question « quelle est l'année scolaire courante ? » et
 * garantit l'invariant d'une seule année courante à la fois.
 *
 * <p>Utilisé par la création de groupe (valeur par défaut), le workflow de fin d'année et le
 * garde lecture seule. La désignation de l'année courante ({@link #makeCurrent(SchoolYearEntity)})
 * bascule les drapeaux en une seule transaction afin de préserver l'invariant
 * (Exigences 2.1, 2.2).</p>
 */
@Service
public class CurrentSchoolYearService {

    private final SchoolYearRepository schoolYearRepository;

    @Autowired
    public CurrentSchoolYearService(SchoolYearRepository schoolYearRepository) {
        this.schoolYearRepository = schoolYearRepository;
    }

    /**
     * Retourne l'année scolaire courante si elle existe (Exigences 2.5, 13.1).
     *
     * @return l'année courante, ou {@link Optional#empty()} si aucune n'est définie
     */
    @Transactional(readOnly = true)
    public Optional<SchoolYearEntity> findCurrent() {
        return schoolYearRepository.findByIsCurrentTrue();
    }

    /**
     * Retourne l'année scolaire courante ou lève une {@link NoCurrentSchoolYearException}
     * si aucune n'est définie (Exigence 13.1).
     *
     * @return l'année scolaire courante
     * @throws NoCurrentSchoolYearException si aucune année courante n'est définie
     */
    @Transactional(readOnly = true)
    public SchoolYearEntity requireCurrent() {
        return findCurrent().orElseThrow(NoCurrentSchoolYearException::new);
    }

    /**
     * Désigne {@code target} comme année scolaire courante de façon atomique : le drapeau
     * courant de l'année précédemment courante passe à {@code false} et celui de la cible à
     * {@code true}, le tout dans une seule transaction (Exigences 2.1, 2.2).
     *
     * <p>Toute opération qui laisserait aucune année courante est rejetée : la cible doit être
     * non nulle (Exigence 2.4).</p>
     *
     * @param target l'année scolaire à marquer comme courante (non nulle)
     * @throws CustomServiceException (HTTP 400) si {@code target} est nulle
     */
    @Transactional
    public void makeCurrent(SchoolYearEntity target) {
        if (target == null) {
            // Une opération qui laisserait aucune année courante est rejetée (Exigence 2.4).
            throw new CustomServiceException(
                    "L'année scolaire cible est obligatoire : "
                            + "une opération ne peut pas laisser aucune année courante.",
                    HttpStatus.BAD_REQUEST);
        }

        // Bascule le drapeau courant de l'année précédemment courante à false (Exigence 2.2),
        // sauf s'il s'agit déjà de la cible.
        Optional<SchoolYearEntity> previousCurrent = schoolYearRepository.findByIsCurrentTrue();
        previousCurrent.ifPresent(previous -> {
            if (!previous.equals(target)) {
                previous.setIsCurrent(false);
                schoolYearRepository.save(previous);
            }
        });

        // Marque la cible comme courante (Exigence 2.1).
        target.setIsCurrent(true);
        schoolYearRepository.save(target);
    }
}
