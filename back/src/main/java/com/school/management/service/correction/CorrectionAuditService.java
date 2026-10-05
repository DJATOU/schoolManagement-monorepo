package com.school.management.service.correction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.school.management.persistance.CorrectionAuditEntity;
import com.school.management.repository.CorrectionAuditRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Écrit la Trace d'une correction (spec admin-corrections, exigences 11.3 à 11.6, D8).
 *
 * <ul>
 *   <li><b>Dans la transaction de la correction</b> ({@code MANDATORY}) : une correction annulée ou
 *       refusée n'en laisse aucune (11.5), et une trace ne peut pas s'écrire seule.</li>
 *   <li><b>Attribuée à l'utilisateur authentifié</b>, lu dans le contexte de sécurité, jamais dans
 *       la requête (11.4). Sans utilisateur authentifié, la correction est refusée : une trace
 *       signée « system » ne dirait pas qui a corrigé.</li>
 *   <li><b>Rang = identifiant</b>, attribué par la base, strictement croissant (11.4).</li>
 *   <li><b>Lisible sans la donnée</b> : résumé et effet sur les montants sont rédigés maintenant,
 *       et aucune clé étrangère ne lie la trace à ce qu'elle décrit (11.6).</li>
 *   <li><b>Aucune trace sans changement</b> : valeurs avant et après identiques, la correction est
 *       refusée (11.3).</li>
 * </ul>
 */
@Service
public class CorrectionAuditService {

    /** Longueur maximale des colonnes de texte de la trace. */
    static final int MAX_TEXT_LENGTH = 500;

    private final CorrectionAuditRepository repository;
    private final ObjectMapper objectMapper;

    public CorrectionAuditService(CorrectionAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        // Un montant garde son échelle : « 3000.00 », et non « 3E+3 » que produirait la
        // normalisation par défaut des nombres décimaux.
        this.objectMapper = objectMapper.copy()
                .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
    }

    /**
     * Écrit une trace.
     *
     * @param draft   la trace rédigée par la correction
     * @param changes Séries dont un montant change, toutes Séries confondues : seules celles de
     *                l'étudiant de la trace entrent dans son effet sur les montants
     * @return la trace écrite
     * @throws CustomServiceException 401 sans utilisateur authentifié ; 400 si les valeurs avant et
     *                                après sont identiques
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CorrectionAuditEntity record(AuditDraft draft, List<SeriesAmountChange> changes) {
        Objects.requireNonNull(draft, "draft");
        String oldValue = json(draft.oldValue());
        String newValue = json(draft.newValue());
        if (oldValue != null && oldValue.equals(newValue)) {
            throw new CustomServiceException(
                    "Correction sans changement effectif : rien à enregistrer.", HttpStatus.BAD_REQUEST);
        }

        List<SeriesAmountChange> own = draft.studentId() == null ? changes
                : changes.stream().filter(change -> draft.studentId().equals(change.studentId())).toList();

        return repository.save(CorrectionAuditEntity.builder()
                .domain(draft.domain())
                .action(draft.action())
                .entityId(draft.entityId())
                .studentId(draft.studentId())
                .groupId(draft.groupId())
                .sessionId(draft.sessionId())
                .seriesId(draft.seriesId())
                .oldValue(oldValue)
                .newValue(newValue)
                .summary(fit(draft.summary()))
                .amountEffect(fit(AmountEffectWriter.describe(own)))
                .reasonType(draft.reason().type())
                .reasonText(draft.reason().text())
                .performedBy(authenticatedUser())
                .performedAt(LocalDateTime.now())
                .build());
    }

    /** Utilisateur authentifié ; refus si la requête n'en porte pas. */
    private static String authenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new CustomServiceException(
                    "Correction refusée : aucun administrateur authentifié.", HttpStatus.UNAUTHORIZED);
        }
        return auth.getName();
    }

    /** Valeur structurée en JSON, clés triées : deux écritures des mêmes valeurs sont identiques. */
    private String json(Map<String, ?> value) {
        return value == null ? null : objectMapper.valueToTree(new TreeMap<>(value)).toString();
    }

    /** Texte ramené à la longueur de sa colonne, en le signalant par des points de suspension. */
    private static String fit(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH - 1) + "…";
    }
}
