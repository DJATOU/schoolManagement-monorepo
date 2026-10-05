package com.school.management.service.payment;

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
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) de la neutralité d'un rattrapage à préciser.
 *
 * <p>Feature: catch-up-billing-routing, Property 3: Un rattrapage en attente est neutre sur les
 * montants.</p>
 *
 * <p>Un rattrapage dont la séance manquée et la décision « déjà payée » ne sont pas renseignées ne
 * doit produire <strong>ni dette ni gratuité</strong>. Le facturer inventerait une dette que
 * personne n'a décidée ; l'écarter accorderait une gratuité définitive et retirerait à
 * l'administrateur la décision qu'il doit précisément prendre. La séance reste donc hors du calcul,
 * et l'énoncé vérifié ici est que sa présence, en nombre quelconque, ne déplace aucune des
 * grandeurs monétaires de la série.</p>
 *
 * <p>Le test compare deux résolutions de la <strong>même</strong> série : sans, puis avec des
 * présences {@code PENDING} ajoutées. Toute divergence signale une fuite du rattrapage à préciser
 * dans le calcul. Les deux côtés de la branche sont ainsi exercés — condition du seuil JaCoCo
 * 100 % lignes et branches qui s'applique à cette classe.</p>
 *
 * <p><b>Validates: PENDING inerte, série comme unité de facturation</b></p>
 */
class PendingCatchUpNeutralityPropertyTest {

    private static final Long STUDENT_ID = 5L;
    private static final Long SERIES_ID = 20L;
    private static final Long GROUP_ID = 3L;
    private static final Date ENROLMENT_DATE = date("2025-01-10");

    // Feature: catch-up-billing-routing, Property 3: Un rattrapage en attente est neutre sur les montants
    @Property(tries = 100)
    void property3_pendingCatchUpIsNeutralOnAmounts(
            @ForAll("sessionOffsets") List<Integer> sessionDayOffsets,
            @ForAll @IntRange(min = 0, max = 4) int pendingCount,
            @ForAll boolean enrolled) {

        // Séances de la série, réparties avant et après l'inscription : le coût au prorata dépend
        // de cette répartition, ce qui rend la comparaison sensible.
        List<SessionEntity> sessions = new ArrayList<>();
        for (int i = 0; i < sessionDayOffsets.size(); i++) {
            sessions.add(session((long) (i + 1), shiftDays(ENROLMENT_DATE, sessionDayOffsets.get(i))));
        }

        // Présences ordinaires : l'étudiant a suivi la première séance, s'il y en a une. Sans quoi
        // le décompte des séances suivies serait nul dans tous les cas et la comparaison
        // porterait sur trop peu de choses.
        List<AttendanceEntity> baseAttendances = new ArrayList<>();
        if (!sessions.isEmpty()) {
            baseAttendances.add(ordinaryAttendance(sessions.get(0), true));
        }

        // --- Référence : la série sans aucun rattrapage à préciser ---
        BillableSessions withoutPending = resolveWith(sessions, baseAttendances, enrolled);

        // --- Comparaison : les mêmes séances, plus des rattrapages à préciser ---
        // Les séances visées sont NOUVELLES (identifiants au-delà des précédentes) : un rattrapage
        // à préciser porte sur une séance suivie dans ce groupe sans y être inscrit.
        List<SessionEntity> withPendingSessions = new ArrayList<>(sessions);
        List<AttendanceEntity> withPendingAttendances = new ArrayList<>(baseAttendances);
        for (int i = 0; i < pendingCount; i++) {
            SessionEntity pendingSession = session(1000L + i, shiftDays(ENROLMENT_DATE, i + 1));
            withPendingSessions.add(pendingSession);
            withPendingAttendances.add(pendingAttendance(pendingSession));
        }

        BillableSessions withPending = resolveWith(withPendingSessions, withPendingAttendances, enrolled);

        // (1) Le décompte des séances facturables est inchangé : le coût au prorata s'en déduit
        //     directement (séances facturables × prix net), donc le coût l'est aussi.
        assertThat(withPending.billableCount())
                .as("%d rattrapage(s) à préciser ne doivent pas changer le nombre de séances facturables",
                        pendingCount)
                .isEqualTo(withoutPending.billableCount());

        // (2) Les séances facturables sont exactement les mêmes, et non seulement en nombre :
        //     une substitution laisserait le décompte intact tout en changeant la ventilation.
        assertThat(withPending.billable())
                .as("les séances facturables doivent être identiques")
                .extracting(SessionEntity::getId)
                .containsExactlyElementsOf(withoutPending.billable().stream()
                        .map(SessionEntity::getId).toList());

        // (3) Le décompte des séances suivies est inchangé : c'est le seuil du retard de paiement
        //     (montant dû à ce jour). Une fuite ici mettrait un étudiant en retard sans raison.
        assertThat(withPending.attendedCount())
                .as("le montant dû à ce jour ne doit pas bouger")
                .isEqualTo(withoutPending.attendedCount());

        // (4) Une séance à préciser n'est pas non plus rangée parmi les écartées : elle n'y serait
        //     pas neutre, l'interface l'annoncerait « non facturée », donc décidée.
        assertThat(withPending.excluded())
                .as("une séance à préciser n'est pas une séance écartée")
                .extracting(SessionEntity::getId)
                .containsExactlyElementsOf(withoutPending.excluded().stream()
                        .map(SessionEntity::getId).toList());

        // (5) Aucune séance à préciser n'apparaît, sous aucune des deux formes.
        //     Le cas pendingCount = 0 est conservé : il exerce l'autre côté de la branche, celui
        //     où aucune séance n'est à préciser. AssertJ refusant une liste de recherche vide, la
        //     vérification est portée par une intersection explicite.
        List<Long> pendingIds = withPendingAttendances.stream()
                .filter(a -> a.getCatchUpBillingState() == CatchUpBillingState.PENDING)
                .map(a -> a.getSession().getId())
                .toList();
        assertThat(withPending.billable().stream().map(SessionEntity::getId).filter(pendingIds::contains).toList())
                .as("aucune séance à préciser ne doit être facturable")
                .isEmpty();
        assertThat(withPending.excluded().stream().map(SessionEntity::getId).filter(pendingIds::contains).toList())
                .as("aucune séance à préciser ne doit être écartée")
                .isEmpty();
    }

    /**
     * Résout la série avec des dépôts simulés. Les mandataires sont recréés à chaque appel : deux
     * résolutions doivent être indépendantes, sinon la comparaison ne prouverait rien.
     */
    private BillableSessions resolveWith(List<SessionEntity> sessions,
                                         List<AttendanceEntity> attendances,
                                         boolean enrolled) {
        SessionSeriesRepository sessionSeriesRepository = Mockito.mock(SessionSeriesRepository.class);
        SessionRepository sessionRepository = Mockito.mock(SessionRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        StudentGroupRepository studentGroupRepository = Mockito.mock(StudentGroupRepository.class);
        CatchUpBillingQualifier qualifier = Mockito.mock(CatchUpBillingQualifier.class);

        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        series.setGroup(group);

        Mockito.when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));
        Mockito.when(sessionRepository.findBySessionSeriesId(SERIES_ID)).thenReturn(sessions);
        Mockito.when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_ID))
                .thenReturn(attendances);
        // Aucun rattrapage compensatoire : la propriété porte sur les rattrapages à préciser, pas
        // sur la compensation, déjà couverte ailleurs.
        Mockito.when(qualifier.view(STUDENT_ID)).thenReturn(CatchUpBillingQualifier.CatchUpView.empty());

        if (enrolled) {
            StudentGroupEntity enrolment = new StudentGroupEntity();
            enrolment.setGroup(group);
            enrolment.setDateAssigned(ENROLMENT_DATE);
            enrolment.setActive(true);
            Mockito.when(studentGroupRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                    .thenReturn(List.of(enrolment));
        } else {
            Mockito.when(studentGroupRepository.findByGroupIdAndStudentId(GROUP_ID, STUDENT_ID))
                    .thenReturn(List.of());
        }

        BillableSessionsResolverImpl resolver = new BillableSessionsResolverImpl(
                sessionSeriesRepository, sessionRepository, attendanceRepository,
                studentGroupRepository, qualifier);

        return resolver.resolve(STUDENT_ID, SERIES_ID);
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private static SessionEntity session(Long id, Date start) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setSessionTimeStart(start);
        return session;
    }

    private static AttendanceEntity ordinaryAttendance(SessionEntity session, boolean present) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setSession(session);
        attendance.setIsPresent(present);
        attendance.setActive(true);
        return attendance;
    }

    /** Présence de rattrapage à préciser : marquée rattrapage, sans séance manquée ni décision. */
    private static AttendanceEntity pendingAttendance(SessionEntity session) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setSession(session);
        attendance.setIsPresent(true);
        attendance.setActive(true);
        attendance.setIsCatchUp(true);
        attendance.setCatchUpBillingState(CatchUpBillingState.PENDING);
        return attendance;
    }

    private static Date date(String isoDate) {
        return Date.from(LocalDate.parse(isoDate).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date shiftDays(Date reference, int days) {
        return Date.from(reference.toInstant().plusSeconds(days * 86_400L));
    }

    /**
     * Décalages en jours des séances par rapport à la date d'inscription, valeurs négatives
     * comprises : une séance antérieure à l'inscription et non suivie est écartée du prorata, ce
     * qui garantit que la comparaison porte sur des séries mêlant facturables et écartées.
     */
    @Provide
    Arbitrary<List<Integer>> sessionOffsets() {
        return Arbitraries.integers().between(-20, 20).list().ofMinSize(1).ofMaxSize(6);
    }
}
