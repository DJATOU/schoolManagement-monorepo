package com.school.management.controller;

import com.school.management.dto.group.GroupChangeDTO;
import com.school.management.service.group.GroupChangeDetector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Signalements de changement de groupe d'un étudiant (exigence 10.2).
 *
 * <p>Contrôleur mince : la détection appartient au {@link GroupChangeDetector}, seul à connaître
 * la règle — une inscription clôturée dans un groupe et une inscription ouverte dans un
 * <em>autre</em> groupe, les deux sur le même mois civil. Le contrôleur ne fait que projeter son
 * résultat sur le contrat exposé au client.</p>
 *
 * <h2>Lecture seule, hors du chemin d'encaissement</h2>
 * Ce point d'entrée n'écrit rien et n'est appelé par aucun service d'encaissement : la fiche
 * étudiant et le formulaire de versement l'interrogent séparément. Un signalement ne peut donc ni
 * modifier un montant ni empêcher l'enregistrement d'un versement (exigences 10.6, 10.7).
 *
 * <h2>Réponse vide plutôt que 404</h2>
 * L'absence de changement est le cas normal, pas une erreur : une liste vide est renvoyée. Un
 * étudiant inconnu produit également une liste vide — le client interroge ce point d'entrée pour
 * décider s'il affiche une alerte, pas pour vérifier l'existence de l'étudiant.
 */
@RestController
@RequestMapping("/api/students")
public class GroupChangeController {

    private final GroupChangeDetector groupChangeDetector;

    public GroupChangeController(GroupChangeDetector groupChangeDetector) {
        this.groupChangeDetector = groupChangeDetector;
    }

    /**
     * Signalements de changement de groupe d'un étudiant, du plus ancien au plus récent.
     *
     * @param studentId identifiant de l'étudiant
     * @return les signalements avec, pour chacun, le mois civil, le groupe quitté, le groupe
     *         rejoint et les séances suivies dans chacun sur ce mois ; liste vide en l'absence de
     *         changement
     */
    @GetMapping("/{studentId}/group-changes")
    public ResponseEntity<List<GroupChangeDTO>> getGroupChanges(@PathVariable Long studentId) {
        List<GroupChangeDTO> changes = groupChangeDetector.detect(studentId).stream()
                .map(GroupChangeDTO::from)
                .toList();
        return ResponseEntity.ok(changes);
    }
}
