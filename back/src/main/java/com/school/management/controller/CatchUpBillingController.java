package com.school.management.controller;

import com.school.management.dto.StudentAbsenceDTO;
import com.school.management.dto.catchup.CatchUpBillingAuditDTO;
import com.school.management.dto.catchup.CorrectCatchUpRequestDTO;
import com.school.management.dto.catchup.PendingCatchUpDTO;
import com.school.management.dto.catchup.ResolveCatchUpRequestDTO;
import com.school.management.service.CatchUpBillingResolutionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Facturation des rattrapages à préciser : liste, résolution, correction, piste d'audit.
 *
 * <p>Contrôleur mince : les règles vivent dans {@link CatchUpBillingResolutionService}. Les codes
 * d'erreur (400 / 404 / 409) et leurs messages français proviennent des
 * {@code CustomServiceException} traduites par {@code GlobalExceptionHandler}.</p>
 *
 * <p><strong>Autorisation.</strong> La chaîne de sécurité réserve déjà toute écriture sous
 * {@code /api/**} au rôle ADMIN et ouvre la lecture à ADMIN et VIEWER. Résoudre et corriger sont
 * des {@code PATCH} : elles sont donc protégées sans annotation supplémentaire ici, ce qui évite
 * deux endroits susceptibles de diverger. Un VIEWER peut consulter la liste et la piste d'audit,
 * ce qui est voulu : constater qu'une décision est en attente n'est pas la prendre.</p>
 */
@RestController
@RequestMapping("/api/catch-up-billing")
public class CatchUpBillingController {

    private final CatchUpBillingResolutionService resolutionService;

    public CatchUpBillingController(CatchUpBillingResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    /**
     * Rattrapages en attente de décision, du plus ancien au plus récent.
     *
     * @return la liste des rattrapages à préciser
     */
    @GetMapping("/pending")
    public ResponseEntity<List<PendingCatchUpDTO>> pending() {
        return ResponseEntity.ok(resolutionService.findPendingForDisplay());
    }

    /**
     * Séances manquées proposables pour un rattrapage : absences de l'étudiant dans les groupes de
     * même niveau et même matière que le groupe d'accueil.
     *
     * @param studentId   identifiant de l'étudiant
     * @param hostGroupId groupe où la séance a été suivie
     * @return les absences proposables
     */
    @GetMapping("/eligible-missed-sessions")
    public ResponseEntity<List<StudentAbsenceDTO>> eligibleMissedSessions(
            @RequestParam Long studentId,
            @RequestParam Long hostGroupId) {
        return ResponseEntity.ok(resolutionService.eligibleMissedSessions(studentId, hostGroupId));
    }

    /**
     * Résout un rattrapage : désigne la séance manquée et tranche « déjà payée ».
     *
     * <p>Les deux décisions sont obligatoires. La validation Jakarta rejette une requête
     * incomplète avant d'atteindre le service, et le service refuse également une décision nulle :
     * ce doublon est délibéré, le service étant appelable autrement que par HTTP.</p>
     *
     * @param attendanceId présence de rattrapage à résoudre
     * @param body         séance manquée, décision, commentaire
     * @return le rattrapage résolu, tel qu'il apparaîtra dans les listes
     */
    @PatchMapping("/{attendanceId}/resolve")
    public ResponseEntity<PendingCatchUpDTO> resolve(
            @PathVariable Long attendanceId,
            @Valid @RequestBody ResolveCatchUpRequestDTO body) {
        resolutionService.resolve(attendanceId, body.missedSessionId(), body.alreadyPaid(), body.comment());
        return ResponseEntity.ok(resolutionService.pendingViewOf(attendanceId));
    }

    /**
     * Corrige la séance manquée et/ou la décision de facturation d'un rattrapage résolu.
     *
     * <p>Chaque changement effectif est tracé. Un corps sans aucun champ renseigné est refusé :
     * il laisserait croire qu'une correction a eu lieu.</p>
     *
     * @param attendanceId présence de rattrapage à corriger
     * @param body         nouvelle séance manquée et/ou nouvelle décision, et motif
     * @return le rattrapage corrigé
     */
    @PatchMapping("/{attendanceId}/correct")
    public ResponseEntity<PendingCatchUpDTO> correct(
            @PathVariable Long attendanceId,
            @RequestBody CorrectCatchUpRequestDTO body) {
        resolutionService.correct(attendanceId, body.missedSessionId(), body.alreadyPaid(), body.comment());
        return ResponseEntity.ok(resolutionService.pendingViewOf(attendanceId));
    }

    /**
     * Piste d'audit d'un rattrapage, de la correction la plus récente à la plus ancienne.
     *
     * @param attendanceId présence de rattrapage
     * @return les entrées d'audit
     */
    @GetMapping("/{attendanceId}/audit")
    public ResponseEntity<List<CatchUpBillingAuditDTO>> auditTrail(@PathVariable Long attendanceId) {
        return ResponseEntity.ok(resolutionService.auditTrailForDisplay(attendanceId));
    }
}
