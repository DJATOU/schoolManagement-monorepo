package com.school.management.service.payment;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriétés de la facturation unique d'une séance consommée (exigences 2.3, 2.4, 2.6, 2.12).
 *
 * <p>Le scénario simulé est celui qui produit le défaut : un étudiant inscrit dans un groupe A, qui
 * manque des séances de A et les rattrape dans un groupe B. Chaque séance manquée est facturée dans
 * A ; la séance de rattrapage ne doit rien coûter dans B.</p>
 */
class CatchUpBillingPropertyTest {

    private static final long STUDENT_ID = 1L;
    private static final long GROUP_A = 10L;
    private static final long GROUP_B = 11L;
    private static final long SERIES_A = 20L;
    private static final long SERIES_B = 21L;
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final Date INSCRIPTION_A = new Date(1_770_000_000_000L);

    /**
     * Monte un résolveur sur un scénario complet : {@code nbSeancesA} séances dans la série A dont
     * {@code nbManquees} sont manquées puis rattrapées dans la série B.
     */
    private record Montage(BillableSessionsResolver resolver,
                           List<SessionEntity> seancesA,
                           List<SessionEntity> seancesB) { }

    private Montage monter(int nbSeancesA, int nbManquees, boolean inscritEnB) {
        SessionSeriesEntity serieA = serie(SERIES_A, GROUP_A);
        SessionSeriesEntity serieB = serie(SERIES_B, GROUP_B);

        List<SessionEntity> seancesA = new ArrayList<>();
        for (int i = 0; i < nbSeancesA; i++) {
            seancesA.add(seance(100L + i, GROUP_A, serieA,
                    new Date(INSCRIPTION_A.getTime() + (i + 1) * DAY)));
        }
        List<SessionEntity> seancesB = new ArrayList<>();
        for (int i = 0; i < nbManquees; i++) {
            seancesB.add(seance(200L + i, GROUP_B, serieB,
                    new Date(INSCRIPTION_A.getTime() + (i + 10) * DAY)));
        }

        List<AttendanceEntity> rattrapages = new ArrayList<>();
        List<SessionEntity> manquees = new ArrayList<>();
        for (int i = 0; i < nbManquees; i++) {
            SessionEntity manquee = seancesA.get(i);
            manquees.add(manquee);
            rattrapages.add(rattrapage(seancesB.get(i), manquee));
        }

        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        SessionSeriesRepository seriesRepository = mock(SessionSeriesRepository.class);
        StudentGroupRepository studentGroupRepository = mock(StudentGroupRepository.class);

        when(seriesRepository.findById(SERIES_A)).thenReturn(Optional.of(serieA));
        when(seriesRepository.findById(SERIES_B)).thenReturn(Optional.of(serieB));
        when(sessionRepository.findBySessionSeriesId(SERIES_A)).thenReturn(seancesA);
        when(sessionRepository.findBySessionSeriesId(SERIES_B)).thenReturn(seancesB);
        when(sessionRepository.findAllById(any())).thenReturn(manquees);

        // Les présences de la série A : les séances manquées y sont des ABSENCES, non réécrites.
        List<AttendanceEntity> presencesA = new ArrayList<>();
        for (int i = 0; i < nbManquees; i++) {
            presencesA.add(absence(seancesA.get(i)));
        }
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_A))
                .thenReturn(presencesA);
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_B))
                .thenReturn(rattrapages);
        when(attendanceRepository.findByStudentIdAndIsCatchUpTrueAndActiveTrue(STUDENT_ID))
                .thenReturn(rattrapages);

        List<StudentGroupEntity> inscriptions = new ArrayList<>();
        inscriptions.add(inscription(GROUP_A, INSCRIPTION_A));
        if (inscritEnB) {
            inscriptions.add(inscription(GROUP_B, INSCRIPTION_A));
        }
        when(studentGroupRepository.findByStudentIdAndActiveTrue(STUDENT_ID)).thenReturn(inscriptions);
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_A, STUDENT_ID))
                .thenReturn(Optional.of(inscription(GROUP_A, INSCRIPTION_A)));
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_B, STUDENT_ID))
                .thenReturn(inscritEnB
                        ? Optional.of(inscription(GROUP_B, INSCRIPTION_A)) : Optional.empty());

        CatchUpBillingQualifier qualifier = new CatchUpBillingQualifierImpl(
                attendanceRepository, sessionRepository, studentGroupRepository);
        BillableSessionsResolver resolver = new BillableSessionsResolverImpl(
                seriesRepository, sessionRepository, attendanceRepository,
                studentGroupRepository, qualifier);

        return new Montage(resolver, seancesA, seancesB);
    }

    // Feature: absence-justification-and-refund-receipts, Property 1: For any set of sessions,
    // attendances and catch-up attendances of a student, each attended session is billable in at
    // most one series, and the outcome does not depend on the order in which series are evaluated.
    @Property(tries = 100)
    void property1_uneSeanceConsommeeEstFactureeDansUneSerieAuPlus(
            @ForAll("nbSeances") int nbSeancesA,
            @ForAll("nbManquees") int nbManquees) {

        int manquees = Math.min(nbManquees, nbSeancesA);
        Montage m = monter(nbSeancesA, manquees, false);

        BillableSessions a = m.resolver().resolve(STUDENT_ID, SERIES_A);
        BillableSessions b = m.resolver().resolve(STUDENT_ID, SERIES_B);

        // Aucune séance ne doit apparaître comme facturable dans les deux séries.
        Set<Long> idsA = new HashSet<>(a.billable().stream().map(SessionEntity::getId).toList());
        Set<Long> idsB = new HashSet<>(b.billable().stream().map(SessionEntity::getId).toList());
        Set<Long> intersection = new HashSet<>(idsA);
        intersection.retainAll(idsB);
        assertThat(intersection).as("séances facturées dans deux séries").isEmpty();

        // Les séances de rattrapage compensatoire sont écartées côté accueil (exigences 2.3, 2.4).
        assertThat(b.billable()).as("le rattrapage compensatoire est facturé côté accueil").isEmpty();
        assertThat(b.attendedCount()).as("le rattrapage compensatoire compte comme suivi en accueil")
                .isZero();

        // Indépendance à l'ordre d'évaluation (exigence 2.6).
        BillableSessions bPuisA = m.resolver().resolve(STUDENT_ID, SERIES_B);
        BillableSessions aApres = m.resolver().resolve(STUDENT_ID, SERIES_A);
        assertThat(aApres.billableCount()).isEqualTo(a.billableCount());
        assertThat(aApres.attendedCount()).isEqualTo(a.attendedCount());
        assertThat(bPuisA.billableCount()).isEqualTo(b.billableCount());
    }

    // Feature: absence-justification-and-refund-receipts, Property 3: For any origin series, the
    // amount due grows by one net session price for each missed session covered by a compensatory
    // catch-up, without the original attendance ceasing to be an absence.
    @Property(tries = 100)
    void property3_laSeanceConsommeeCompteCommeSuivieCoteOrigine(
            @ForAll("nbSeances") int nbSeancesA,
            @ForAll("nbManquees") int nbManquees) {

        int manquees = Math.min(nbManquees, nbSeancesA);
        Montage m = monter(nbSeancesA, manquees, false);

        BillableSessions a = m.resolver().resolve(STUDENT_ID, SERIES_A);

        // Exigence 2.12 : chaque séance manquée rattrapée compte comme suivie dans SA série.
        // Sans cela, la séance consommée n'augmenterait le montant dû d'aucune série.
        assertThat(a.attendedCount())
                .as("les %d séances rattrapées ne comptent pas comme suivies côté origine", manquees)
                .isEqualTo(manquees);

        // Et elle reste facturable côté origine, où la place était réservée (exigence 2.2).
        assertThat(a.billableCount()).isEqualTo(nbSeancesA);
        assertThat(a.attendedCount()).isLessThanOrEqualTo(a.billableCount());
    }

    // Feature: absence-justification-and-refund-receipts, Property 2: For any host series, the
    // billable count and attended count computed with compensatory catch-ups equal those computed
    // without them.
    @Property(tries = 100)
    void property2_leCoutCoteAccueilIgnoreLesCompensatoires(
            @ForAll("nbSeances") int nbSeancesA,
            @ForAll("nbManquees") int nbManquees) {

        int manquees = Math.min(nbManquees, nbSeancesA);

        BillableSessions avec = monter(nbSeancesA, manquees, false).resolver()
                .resolve(STUDENT_ID, SERIES_B);
        BillableSessions sans = monter(nbSeancesA, 0, false).resolver()
                .resolve(STUDENT_ID, SERIES_B);

        // La série d'accueil coûte la même chose avec ou sans rattrapages compensatoires.
        assertThat(avec.billableCount()).isEqualTo(sans.billableCount());
        assertThat(avec.attendedCount()).isEqualTo(sans.attendedCount());
    }

    @Provide
    Arbitrary<Integer> nbSeances() {
        return Arbitraries.integers().between(1, 8);
    }

    @Provide
    Arbitrary<Integer> nbManquees() {
        return Arbitraries.integers().between(0, 8);
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private static SessionSeriesEntity serie(Long id, Long groupId) {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(id);
        GroupEntity group = new GroupEntity();
        group.setId(groupId);
        series.setGroup(group);
        return series;
    }

    private static SessionEntity seance(Long id, Long groupId, SessionSeriesEntity series, Date start) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setSessionTimeStart(start);
        GroupEntity group = new GroupEntity();
        group.setId(groupId);
        session.setGroup(group);
        session.setSessionSeries(series);
        return session;
    }

    private static AttendanceEntity rattrapage(SessionEntity hote, SessionEntity manquee) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setId(hote.getId() * 1000);
        attendance.setStudent(StudentEntity.builder().id(STUDENT_ID).build());
        attendance.setSession(hote);
        attendance.setSessionSeries(hote.getSessionSeries());
        attendance.setMissedSession(manquee);
        attendance.setIsCatchUp(true);
        attendance.setIsPresent(true);
        return attendance;
    }

    private static AttendanceEntity absence(SessionEntity session) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setId(session.getId() * 7);
        attendance.setStudent(StudentEntity.builder().id(STUDENT_ID).build());
        attendance.setSession(session);
        attendance.setSessionSeries(session.getSessionSeries());
        attendance.setIsCatchUp(false);
        attendance.setIsPresent(false);
        return attendance;
    }

    private static StudentGroupEntity inscription(Long groupId, Date dateAssigned) {
        GroupEntity group = new GroupEntity();
        group.setId(groupId);
        return StudentGroupEntity.builder().group(group).dateAssigned(dateAssigned).build();
    }
}
