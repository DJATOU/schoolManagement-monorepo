package com.school.management.service.payment;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implémentation du résolveur de séances facturables (exigences 1.1 à 1.4, 1.6).
 *
 * <p>Trois choix de conception méritent d'être explicités :</p>
 *
 * <p><strong>L'inscription est lue en base, pas en mémoire.</strong>
 * {@code StudentHistoryService} déterminait l'inscription officielle par
 * {@code student.getGroups().contains(group)} : une collection {@code @ManyToMany} dont
 * l'appartenance dépend de {@code equals}/{@code hashCode} et du chargement de la session
 * Hibernate. Un inscrit régulier pouvait ainsi basculer en mode rattrapage.
 * {@code findByGroupIdAndStudentId} est déterministe et testable. Il rend les inscriptions closes
 * aussi : leurs Fenêtres_Inscription bornent ce qui reste dû (spec admin-corrections, C.5).</p>
 *
 * <p><strong>Une séance suivie est toujours facturable</strong>, y compris antérieure à
 * l'inscription : elle a été consommée (exigence 1.2). L'ensemble des séances suivies est donc
 * inclus dans celui des facturables, ce qui donne gratuitement l'invariant
 * {@code amountDueSoFar ≤ Coût_Série_Prorata} (exigence 2.4).</p>
 *
 * <p><strong>Les présences sont lues par série</strong>, via la même clé que
 * {@code AttendanceRepository.countPresentForStudentAndSeries} qu'{@code attendedCount}
 * remplace : les deux décomptes portent ainsi sur le même ensemble de séances.</p>
 */
@Service
public class BillableSessionsResolverImpl implements BillableSessionsResolver {

    private final SessionSeriesRepository sessionSeriesRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final CatchUpBillingQualifier catchUpBillingQualifier;

    public BillableSessionsResolverImpl(SessionSeriesRepository sessionSeriesRepository,
                                        SessionRepository sessionRepository,
                                        AttendanceRepository attendanceRepository,
                                        StudentGroupRepository studentGroupRepository,
                                        CatchUpBillingQualifier catchUpBillingQualifier) {
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.catchUpBillingQualifier = catchUpBillingQualifier;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Les séances proviennent de {@code findBySessionSeriesId}, déjà triée par date de
     * début croissante : l'ordre chronologique est porté par le résolveur, pas reconstitué par
     * chaque appelant. La ventilation des versements s'appuie sur cet ordre. Une séance
     * ajoutée après coup à la série entre donc naturellement dans le décompte (exigence
     * 1.6).</p>
     *
     * @throws CustomServiceException 404 si la série est introuvable
     */
    @Override
    @Transactional(readOnly = true)
    public BillableSessions resolve(Long studentId, Long seriesId) {
        SessionSeriesEntity series = sessionSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new CustomServiceException(
                        "Série introuvable pour l'identifiant : " + seriesId,
                        HttpStatus.NOT_FOUND));

        // Toutes les inscriptions de l'étudiant au groupe, closes comprises : un étudiant parti doit
        // encore les séances de sa fenêtre, et un étudiant revenu en a deux. Le résolveur ne lisait
        // que l'inscription active : à la clôture, l'étudiant devenait « sans inscription », et une
        // séance de sa période pas encore validée cessait d'être due.
        List<StudentGroupEntity> enrolments = resolveEnrolments(series.getGroup(), studentId);
        List<EnrolmentWindow> windows = enrolments.stream().map(StudentGroupEntity::window).toList();
        boolean activeEnrolment = enrolments.stream().anyMatch(e -> !Boolean.FALSE.equals(e.getActive()));

        List<AttendanceEntity> attendances =
                attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, seriesId);
        Set<Long> attendedSessionIds = sessionIdsOf(attendances, false);
        Set<Long> presentSessionIds = sessionIdsOf(attendances, true);
        Set<Long> nonCompensatorySessionIds = nonCompensatorySessionIdsOf(attendances);
        Set<Long> pendingSessionIds = pendingSessionIdsOf(attendances);

        // Vue des rattrapages de l'étudiant, tous groupes confondus : une séance rattrapée ailleurs
        // n'apparaît pas dans les présences de cette série.
        CatchUpBillingQualifier.CatchUpView catchUpView = catchUpBillingQualifier.view(studentId);

        List<SessionEntity> billable = new ArrayList<>();
        List<SessionEntity> excluded = new ArrayList<>();
        Set<Long> compensatedAway = new HashSet<>();
        Set<Long> withinEnrolment = new HashSet<>();
        int attendedCount = 0;

        for (SessionEntity session : sessionRepository.findBySessionSeriesId(seriesId)) {
            Long sessionId = session.getId();
            boolean inWindow = windows.stream().anyMatch(window -> window.contains(session.getSessionTimeStart()));
            if (inWindow) {
                withinEnrolment.add(sessionId);
            }

            // Rattrapage à préciser : la séance est INERTE. Elle n'entre ni dans les facturables,
            // ni dans les écartées, et n'alimente pas le décompte des séances suivies.
            //
            // Ne pas la ranger parmi les écartées, malgré l'apparente commodité : une séance
            // écartée est une décision PRISE (« déjà facturée ailleurs »), que l'interface annonce
            // comme telle. Une séance à préciser n'est pas décidée. Les confondre reproduirait le
            // défaut d'étiquetage qui présentait une séance à venir comme non facturée, et surtout
            // rendrait la gratuité définitive : l'administrateur n'aurait plus rien à trancher.
            if (pendingSessionIds.contains(sessionId)) {
                continue;
            }

            // Exigences 2.3 et 2.11 : la séance est écartée seulement si TOUTES les présences
            // actives qui la couvrent sont des rattrapages compensatoires. Une présence ordinaire
            // suffit à la ramener dans les facturables : la gratuité ne vaut que si le rattrapage
            // est la seule raison d'être là.
            if (catchUpView.isFullyCompensated(sessionId)
                    && !nonCompensatorySessionIds.contains(sessionId)) {
                excluded.add(session);
                compensatedAway.add(sessionId);
                continue;
            }

            boolean hasAttendance = attendedSessionIds.contains(sessionId);
            if (hasAttendance || inWindow) {
                billable.add(session);
                // Exigence 2.12 : une séance rattrapée ailleurs compte comme suivie ICI, dans sa
                // série d'origine, alors que sa présence reste une absence. Sans cela, la séance
                // consommée n'augmenterait le montant dû d'aucune série : ni de l'accueil, où elle
                // est écartée, ni de l'origine, où l'étudiant était absent.
                if (presentSessionIds.contains(sessionId) || catchUpView.isCompensatedAway(sessionId)) {
                    attendedCount++;
                }
            } else {
                excluded.add(session);
            }
        }

        // Membre du groupe pour cette série : inscription active — comme avant, une série entière
        // antérieure à l'arrivée reste affichée avec ses séances écartées — ou inscription close
        // dont la fenêtre touche la série. Une série postérieure au départ ne le concerne plus.
        boolean enrolled = activeEnrolment || !withinEnrolment.isEmpty();
        return new BillableSessions(List.copyOf(billable), List.copyOf(excluded),
                attendedCount, enrolled, withinEnrolment, compensatedAway);
    }

    /**
     * Inscriptions de l'étudiant au groupe de la série, actives et closes ; vide si le groupe est
     * absent.
     *
     * <p>Une inscription sans date d'arrivée a une fenêtre vide : elle ne rend aucune séance
     * facturable à ce titre, seules les séances suivies le sont (exigence 1.4).</p>
     */
    private List<StudentGroupEntity> resolveEnrolments(GroupEntity group, Long studentId) {
        if (group == null || group.getId() == null) {
            return List.of();
        }
        return studentGroupRepository.findByGroupIdAndStudentId(group.getId(), studentId);
    }

    /**
     * Identifiants des séances couvertes par une présence active de l'étudiant.
     *
     * @param onlyPresent vrai pour ne retenir que les fiches marquées présent, qui alimentent
     *                    {@code attendedCount} (le seuil de retard) ; faux pour retenir toute
     *                    présence active, qui rend la séance facturable
     */
    private Set<Long> sessionIdsOf(List<AttendanceEntity> attendances, boolean onlyPresent) {
        Set<Long> ids = new HashSet<>();
        for (AttendanceEntity attendance : attendances) {
            if (onlyPresent && !Boolean.TRUE.equals(attendance.getIsPresent())) {
                continue;
            }
            SessionEntity session = attendance.getSession();
            if (session != null && session.getId() != null) {
                ids.add(session.getId());
            }
        }
        return ids;
    }

    /**
     * Séances de cette série couvertes par une présence active qui <strong>n'est pas</strong> un
     * rattrapage (exigence 2.11).
     *
     * <p>Sert à ne pas rendre gratuite une séance à laquelle l'étudiant a aussi assisté au titre de
     * son inscription. Le test porte sur l'indicateur de rattrapage de la présence, et non sur la
     * qualification : une présence ordinaire n'est jamais compensatoire, quelle que soit l'histoire
     * du rattrapage qui la double.</p>
     */
    private Set<Long> nonCompensatorySessionIdsOf(List<AttendanceEntity> attendances) {
        Set<Long> ids = new HashSet<>();
        for (AttendanceEntity attendance : attendances) {
            if (Boolean.TRUE.equals(attendance.getIsCatchUp())) {
                continue;
            }
            SessionEntity session = attendance.getSession();
            if (session != null && session.getId() != null) {
                ids.add(session.getId());
            }
        }
        return ids;
    }

    /**
     * Séances couvertes par un rattrapage <strong>à préciser</strong>.
     *
     * <p>Tant que la séance manquée et la décision « déjà payée » ne sont pas renseignées, rien ne
     * permet de dire si cette séance est due au groupe d'accueil ou déjà payée ailleurs. La
     * facturer serait un pari, l'écarter en serait un autre : elle reste donc hors du calcul, et
     * l'interface la signale comme à préciser. C'est la seule issue qui ne fabrique pas une dette
     * ni une gratuité que personne n'a décidée.</p>
     */
    private Set<Long> pendingSessionIdsOf(List<AttendanceEntity> attendances) {
        Set<Long> ids = new HashSet<>();
        for (AttendanceEntity attendance : attendances) {
            if (attendance.getCatchUpBillingState() != CatchUpBillingState.PENDING) {
                continue;
            }
            SessionEntity session = attendance.getSession();
            if (session != null && session.getId() != null) {
                ids.add(session.getId());
            }
        }
        return ids;
    }
}
