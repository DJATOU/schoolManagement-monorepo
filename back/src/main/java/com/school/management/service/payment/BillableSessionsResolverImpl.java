package com.school.management.service.payment;

import com.school.management.persistance.AttendanceEntity;
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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
 * {@code findByGroupIdAndStudentIdAndActiveTrue} est déterministe et testable.</p>
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

        Optional<StudentGroupEntity> enrolment = resolveEnrolment(series.getGroup(), studentId);
        Date enrollmentDate = enrolment.map(StudentGroupEntity::getDateAssigned).orElse(null);

        List<AttendanceEntity> attendances =
                attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, seriesId);
        Set<Long> attendedSessionIds = sessionIdsOf(attendances, false);
        Set<Long> presentSessionIds = sessionIdsOf(attendances, true);
        Set<Long> nonCompensatorySessionIds = nonCompensatorySessionIdsOf(attendances);

        // Vue des rattrapages de l'étudiant, tous groupes confondus : une séance rattrapée ailleurs
        // n'apparaît pas dans les présences de cette série.
        CatchUpBillingQualifier.CatchUpView catchUpView = catchUpBillingQualifier.view(studentId);

        List<SessionEntity> billable = new ArrayList<>();
        List<SessionEntity> excluded = new ArrayList<>();
        Set<Long> compensatedAway = new HashSet<>();
        int attendedCount = 0;

        for (SessionEntity session : sessionRepository.findBySessionSeriesId(seriesId)) {
            Long sessionId = session.getId();

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
            if (hasAttendance || isOnOrAfterEnrolment(session, enrollmentDate)) {
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

        return new BillableSessions(List.copyOf(billable), List.copyOf(excluded),
                attendedCount, enrolment.isPresent(), enrollmentDate, Set.copyOf(compensatedAway));
    }

    /** Inscription active de l'étudiant au groupe de la série, vide si le groupe est absent. */
    private Optional<StudentGroupEntity> resolveEnrolment(GroupEntity group, Long studentId) {
        if (group == null || group.getId() == null) {
            return Optional.empty();
        }
        return studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(group.getId(), studentId);
    }

    /**
     * Séance postérieure ou égale à la date d'inscription (exigence 1.1).
     *
     * <p>Sans date d'inscription, aucune séance n'est retenue à ce titre : seules les séances
     * suivies sont facturables (exigence 1.4). C'est le cas du rattrapage pur.</p>
     */
    private boolean isOnOrAfterEnrolment(SessionEntity session, Date enrollmentDate) {
        if (enrollmentDate == null) {
            return false;
        }
        Date sessionDate = session.getSessionTimeStart();
        return sessionDate != null && !sessionDate.before(enrollmentDate);
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
}
