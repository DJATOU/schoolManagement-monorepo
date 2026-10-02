package com.school.management.dto.session;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Feuille_Appel d'une Séance : les étudiants qu'elle concerne, et ceux du groupe qu'elle ne
 * concerne pas, avec leurs fenêtres (exigences 6.2, 7.1, 7.2).
 *
 * <p>Les non-concernés sont renvoyés pour qu'une feuille vide, ou un absent de la liste,
 * s'explique d'elle-même : « arrive le 15/01/2030 », « parti le 30/11/2029 ». Les dates sont des
 * jours, sans heure ni fuseau ; l'écran les met en forme dans la langue choisie.</p>
 *
 * @param sessionId    la Séance
 * @param groupId      son groupe, {@code null} si elle n'en a pas
 * @param sessionDay   le jour de la Séance, celui que les fenêtres doivent contenir
 * @param students     concernés, par nom puis prénom
 * @param notConcerned étudiants du groupe dont aucune fenêtre ne contient ce jour
 */
public record RollCallDTO(
        Long sessionId,
        Long groupId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate sessionDay,
        List<ConcernedStudent> students,
        List<NotConcernedStudent> notConcerned) {

    /**
     * Étudiant attendu à la Séance.
     *
     * @param departure jour de départ si l'inscription est close : un étudiant parti figure sur
     *                  la feuille d'une Séance de sa fenêtre validée après son départ (6.2)
     */
    public record ConcernedStudent(
            Long id,
            String firstName,
            String lastName,
            String gender,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrival,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate departure) {
    }

    /** Étudiant du groupe hors fenêtre ce jour-là, et ses fenêtres dans le groupe. */
    public record NotConcernedStudent(Long id, String firstName, String lastName, List<Window> windows) {
    }

    /** Une Fenêtre_Inscription, départ {@code null} si elle est ouverte. */
    public record Window(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrival,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate departure) {
    }
}
