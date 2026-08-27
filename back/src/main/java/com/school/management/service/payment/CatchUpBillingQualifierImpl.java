package com.school.management.service.payment;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.StudentGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implémentation du qualificateur de rattrapage (exigences 2.1, 2.7).
 *
 * <h2>Trois lectures, quel que soit le nombre de rattrapages</h2>
 * La vue est construite en trois requêtes à nombre constant : les rattrapages actifs de l'étudiant,
 * les séances manquées qu'ils désignent, et les inscriptions actives de l'étudiant. Une résolution
 * rattrapage par rattrapage aurait produit deux requêtes par rattrapage, donc un coût qui grandit
 * avec l'historique de l'étudiant — précisément là où il ne faut pas qu'il grandisse, puisque la vue
 * est consultée à chaque calcul de coût.
 *
 * <h2>Le repli est délibérément permissif pour l'école</h2>
 * Lorsque la qualification ne peut pas être établie — séance manquée absente, séance ou série
 * disparue, aucune inscription active dans le groupe d'origine — le rattrapage est traité comme
 * {@code CONSOMME}, donc <strong>facturable</strong> côté accueil (exigence 2.7). Ce choix mérite
 * d'être explicite : il facture une séance dont on ne peut pas prouver qu'elle est facturée
 * ailleurs. L'inverse, la gratuité par défaut, ferait disparaître silencieusement des recettes sur
 * des données incomplètes, ce qui est plus difficile à détecter qu'une facturation contestée par une
 * famille.
 */
@Service
public class CatchUpBillingQualifierImpl implements CatchUpBillingQualifier {

    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final StudentGroupRepository studentGroupRepository;

    public CatchUpBillingQualifierImpl(AttendanceRepository attendanceRepository,
                                       SessionRepository sessionRepository,
                                       StudentGroupRepository studentGroupRepository) {
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.studentGroupRepository = studentGroupRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CatchUpView view(Long studentId) {
        // 1) Rattrapages actifs de l'étudiant. Le filtre sur active fait partie de la définition
        //    d'une présence de rattrapage : une présence désactivée ne compte pour rien.
        List<AttendanceEntity> catchUps =
                attendanceRepository.findByStudentIdAndIsCatchUpTrueAndActiveTrue(studentId);
        if (catchUps.isEmpty()) {
            return CatchUpView.empty();
        }

        // 2) Séances manquées désignées, chargées en une fois.
        Set<Long> missedIds = new HashSet<>();
        for (AttendanceEntity catchUp : catchUps) {
            Long missedId = idOf(catchUp.getMissedSession());
            if (missedId != null) {
                missedIds.add(missedId);
            }
        }
        Map<Long, SessionEntity> missedSessions = new HashMap<>();
        if (!missedIds.isEmpty()) {
            for (SessionEntity session : sessionRepository.findAllById(missedIds)) {
                missedSessions.put(session.getId(), session);
            }
        }

        // 3) Dates d'inscription de l'étudiant, par groupe.
        Map<Long, Date> enrolmentDates = new HashMap<>();
        for (StudentGroupEntity enrolment : studentGroupRepository.findByStudentIdAndActiveTrue(studentId)) {
            Long groupId = idOf(enrolment.getGroup());
            Date candidate = enrolment.getDateAssigned();
            if (groupId == null || candidate == null) {
                // Une inscription sans groupe ou sans date n'établit aucun droit : elle ne permet
                // pas de dire qu'une séance lui est postérieure. Même traitement que dans le
                // résolveur, où une date d'inscription absente ne rend aucune séance facturable.
                continue;
            }
            // Plusieurs inscriptions actives au même groupe ne devraient pas exister ; si le cas
            // survient, la plus ancienne est retenue, car c'est elle qui ouvre le droit à
            // facturation des séances suivantes.
            Date connue = enrolmentDates.get(groupId);
            if (connue == null || candidate.before(connue)) {
                enrolmentDates.put(groupId, candidate);
            }
        }

        Map<Long, List<Qualification>> bySession = new HashMap<>();
        Set<Long> compensatedAway = new HashSet<>();

        for (AttendanceEntity catchUp : catchUps) {
            Long hostSessionId = idOf(catchUp.getSession());
            if (hostSessionId == null) {
                // Une présence sans séance ne peut être rattachée à rien : elle est ignorée plutôt
                // que de faire échouer tout le calcul de coût de l'étudiant.
                continue;
            }

            Long missedId = idOf(catchUp.getMissedSession());
            Qualification qualification =
                    qualify(missedSessions.get(missedId), enrolmentDates);

            bySession.computeIfAbsent(hostSessionId, k -> new ArrayList<>()).add(qualification);

            if (qualification == Qualification.COMPENSATOIRE) {
                compensatedAway.add(missedId);
            }
        }

        return new CatchUpView(Map.copyOf(bySession), Set.copyOf(compensatedAway));
    }

    /**
     * Qualifie un rattrapage à partir de sa séance manquée et des inscriptions de l'étudiant.
     *
     * <p>Compensatoire si la séance manquée est postérieure ou égale à la date d'inscription de
     * l'étudiant dans le groupe de cette séance : elle est alors facturée dans sa série d'origine
     * par la règle du prorata, et la rattraper ailleurs ne doit rien coûter de plus.</p>
     *
     * <p>La qualification ignore délibérément la réduction applicable à la série d'origine, son
     * montant versé et son statut de paiement (exigence 2.1) : ce sont des états qui changent, alors
     * qu'une séance déjà facturée quelque part le reste. Faire dépendre la qualification du statut
     * de paiement rendrait le coût d'une série sensible aux versements faits sur une autre.</p>
     */
    private Qualification qualify(SessionEntity missedSession, Map<Long, Date> enrolmentDates) {
        if (missedSession == null || missedSession.getSessionSeries() == null) {
            // Séance manquée absente, supprimée, ou sans série : rien ne la facture ailleurs.
            return Qualification.CONSOMME;
        }
        Long originGroupId = idOf(missedSession.getGroup());
        if (originGroupId == null) {
            return Qualification.CONSOMME;
        }
        Date enrolmentDate = enrolmentDates.get(originGroupId);
        if (enrolmentDate == null) {
            // Aucune inscription active dans le groupe d'origine : la séance manquée n'y est pas
            // facturable au titre du prorata, donc le rattrapage l'est ici.
            return Qualification.CONSOMME;
        }
        Date missedDate = missedSession.getSessionTimeStart();
        if (missedDate == null || missedDate.before(enrolmentDate)) {
            return Qualification.CONSOMME;
        }
        return Qualification.COMPENSATOIRE;
    }

    private Long idOf(SessionEntity session) {
        return session == null ? null : session.getId();
    }

    private Long idOf(com.school.management.persistance.GroupEntity group) {
        return group == null ? null : group.getId();
    }
}
