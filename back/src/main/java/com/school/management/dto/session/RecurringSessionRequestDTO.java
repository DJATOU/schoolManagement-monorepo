package com.school.management.dto.session;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Demande de création de séances récurrentes pour un groupe.
 *
 * <p>Décrit un créneau fixe (par exemple « lundi et mercredi, 12:00-14:00 ») répété entre
 * deux dates. Le serveur en déduit les occurrences, les rattache aux séries du groupe et
 * signale celles qu'il refuse de créer.</p>
 *
 * @see com.school.management.service.session.RecurringSessionService
 */
@Getter
@Setter
@NoArgsConstructor
public class RecurringSessionRequestDTO {

    @NotNull(message = "Le groupe est obligatoire.")
    private Long groupId;

    private Long teacherId;

    private Long roomId;

    /** Titre commun aux occurrences (le numéro d'occurrence est ajouté automatiquement). */
    private String title;

    private String sessionType;

    /** Première date possible (incluse). */
    @NotNull(message = "La date de début est obligatoire.")
    private LocalDate startDate;

    /** Dernière date possible (incluse). */
    @NotNull(message = "La date de fin est obligatoire.")
    private LocalDate endDate;

    /** Jours de la semaine concernés. */
    @NotEmpty(message = "Au moins un jour de la semaine est requis.")
    private Set<DayOfWeek> daysOfWeek;

    @NotNull(message = "L'heure de début est obligatoire.")
    private LocalTime startTime;

    @NotNull(message = "L'heure de fin est obligatoire.")
    private LocalTime endTime;

    /**
     * Comportement en cas de conflit (salle ou enseignant déjà occupé) :
     * {@code true} → l'occurrence est ignorée et signalée ; {@code false} → toute la
     * demande est refusée. Par défaut on ignore, afin qu'un seul créneau occupé
     * n'empêche pas de planifier l'année entière.
     */
    private boolean skipConflicts = true;

    /** Numéroter les titres (« Cours 1 », « Cours 2 »...). */
    private boolean numberTitles = true;
}
