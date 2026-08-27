package com.school.management.service.payment;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.service.payment.CatchUpBillingQualifier.CatchUpView;
import com.school.management.service.payment.CatchUpBillingQualifier.Qualification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link CatchUpBillingQualifierImpl} (exigences 2.1, 2.7).
 *
 * <p>La qualification décide si un rattrapage est gratuit côté groupe d'accueil, parce que la séance
 * manquée est déjà facturée dans sa série d'origine, ou facturable, parce que rien ne la facture
 * ailleurs. C'est la seule décision qui empêche un étudiant de payer deux fois une séance suivie une
 * fois.</p>
 */
class CatchUpBillingQualifierTest {

    private static final long STUDENT_ID = 1L;
    private static final long ORIGIN_GROUP_ID = 10L;
    private static final long HOST_SESSION_ID = 100L;
    private static final long MISSED_SESSION_ID = 200L;

    private AttendanceRepository attendanceRepository;
    private SessionRepository sessionRepository;
    private StudentGroupRepository studentGroupRepository;
    private CatchUpBillingQualifierImpl qualifier;

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        sessionRepository = mock(SessionRepository.class);
        studentGroupRepository = mock(StudentGroupRepository.class);
        qualifier = new CatchUpBillingQualifierImpl(
                attendanceRepository, sessionRepository, studentGroupRepository);
    }

    private static Date date(String iso) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(iso);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static SessionEntity session(Long id, Long groupId, Long seriesId, Date start) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setSessionTimeStart(start);
        if (groupId != null) {
            GroupEntity group = new GroupEntity();
            group.setId(groupId);
            session.setGroup(group);
        }
        if (seriesId != null) {
            SessionSeriesEntity series = new SessionSeriesEntity();
            series.setId(seriesId);
            session.setSessionSeries(series);
        }
        return session;
    }

    private AttendanceEntity catchUp(SessionEntity host, SessionEntity missed) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setId(1L);
        attendance.setStudent(StudentEntity.builder().id(STUDENT_ID).build());
        attendance.setSession(host);
        attendance.setMissedSession(missed);
        attendance.setIsCatchUp(true);
        attendance.setIsPresent(true);
        return attendance;
    }

    private void givenEnrolment(Long groupId, Date dateAssigned) {
        GroupEntity group = new GroupEntity();
        group.setId(groupId);
        StudentGroupEntity enrolment = StudentGroupEntity.builder()
                .group(group).dateAssigned(dateAssigned).build();
        when(studentGroupRepository.findByStudentIdAndActiveTrue(STUDENT_ID))
                .thenReturn(List.of(enrolment));
    }

    private void givenNoEnrolment() {
        when(studentGroupRepository.findByStudentIdAndActiveTrue(STUDENT_ID)).thenReturn(List.of());
    }

    private void givenCatchUp(SessionEntity host, SessionEntity missed) {
        when(attendanceRepository.findByStudentIdAndIsCatchUpTrueAndActiveTrue(STUDENT_ID))
                .thenReturn(List.of(catchUp(host, missed)));
        if (missed != null) {
            when(sessionRepository.findAllById(any())).thenReturn(List.of(missed));
        } else {
            when(sessionRepository.findAllById(any())).thenReturn(List.of());
        }
    }

    @Nested
    @DisplayName("Rattrapage compensatoire")
    class Compensatoire {

        @Test
        @DisplayName("séance manquée postérieure à l'inscription : compensatoire, donc gratuit côté accueil")
        void seanceManqueePosterieureAInscription() {
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
            givenCatchUp(host, missed);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            CatchUpView view = qualifier.view(STUDENT_ID);

            assertThat(view.qualificationsBySessionId().get(HOST_SESSION_ID))
                    .containsExactly(Qualification.COMPENSATOIRE);
            assertThat(view.isFullyCompensated(HOST_SESSION_ID)).isTrue();
            // La séance manquée doit compter comme suivie dans sa série d'origine.
            assertThat(view.isCompensatedAway(MISSED_SESSION_ID)).isTrue();
        }

        @Test
        @DisplayName("séance manquée le jour même de l'inscription : compensatoire (borne incluse)")
        void seanceManqueeLeJourDeLInscription() {
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-01-01"));
            givenCatchUp(host, missed);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isTrue();
        }

        @Test
        @DisplayName("la qualification ignore le statut de paiement et la réduction de la série d'origine")
        void independanteDuStatutDePaiement() {
            // Exigence 2.1 : faire dépendre la qualification du statut de paiement rendrait le coût
            // d'une série sensible aux versements faits sur une autre.
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
            givenCatchUp(host, missed);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            // Aucun repository de paiement n'est même injecté : l'indépendance est structurelle.
            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isTrue();
        }
    }

    @Nested
    @DisplayName("Rattrapage consommé")
    class Consomme {

        @Test
        @DisplayName("séance manquée antérieure à l'inscription : consommé, donc facturable")
        void seanceManqueeAnterieureAInscription() {
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2025-12-01"));
            givenCatchUp(host, missed);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            CatchUpView view = qualifier.view(STUDENT_ID);

            assertThat(view.qualificationsBySessionId().get(HOST_SESSION_ID))
                    .containsExactly(Qualification.CONSOMME);
            assertThat(view.isFullyCompensated(HOST_SESSION_ID)).isFalse();
            assertThat(view.isCompensatedAway(MISSED_SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("séance manquée absente : consommé (repli déterministe)")
        void seanceManqueeAbsente() {
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            givenCatchUp(host, null);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("séance manquée sans série : consommé")
        void seanceManqueeSansSerie() {
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, null, date("2026-02-05"));
            givenCatchUp(host, missed);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("séance manquée sans groupe : consommé")
        void seanceManqueeSansGroupe() {
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, null, 400L, date("2026-02-05"));
            givenCatchUp(host, missed);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("aucune inscription active dans le groupe d'origine : consommé")
        void aucuneInscriptionDansLeGroupeDOrigine() {
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
            givenCatchUp(host, missed);
            givenNoEnrolment();

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("séance manquée sans date : consommé")
        void seanceManqueeSansDate() {
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, null);
            givenCatchUp(host, missed);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("Cas dégradés")
    class CasDegrades {

        @Test
        @DisplayName("aucun rattrapage : vue vide, aucune requête inutile")
        void aucunRattrapage() {
            when(attendanceRepository.findByStudentIdAndIsCatchUpTrueAndActiveTrue(STUDENT_ID))
                    .thenReturn(List.of());

            CatchUpView view = qualifier.view(STUDENT_ID);

            assertThat(view.qualificationsBySessionId()).isEmpty();
            assertThat(view.compensatedMissedSessionIds()).isEmpty();
            assertThat(view.isFullyCompensated(HOST_SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("rattrapage sans séance d'accueil : ignoré sans faire échouer le calcul")
        void rattrapageSansSeanceDAccueil() {
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
            givenCatchUp(null, missed);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            assertThat(qualifier.view(STUDENT_ID).qualificationsBySessionId()).isEmpty();
        }

        @Test
        @DisplayName("deux inscriptions actives au même groupe : la plus ancienne est retenue")
        void deuxInscriptionsAuMemeGroupe() {
            // Ne devrait pas exister, mais la plus ancienne ouvre le droit à facturation : retenir
            // la plus récente écarterait à tort des séances déjà facturées.
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-01-15"));
            givenCatchUp(host, missed);

            GroupEntity group = new GroupEntity();
            group.setId(ORIGIN_GROUP_ID);
            when(studentGroupRepository.findByStudentIdAndActiveTrue(STUDENT_ID)).thenReturn(List.of(
                    StudentGroupEntity.builder().group(group).dateAssigned(date("2026-02-01")).build(),
                    StudentGroupEntity.builder().group(group).dateAssigned(date("2026-01-01")).build()));

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isTrue();
        }

        @Test
        @DisplayName("deux inscriptions dans l'ordre inverse : la plus ancienne reste retenue")
        void deuxInscriptionsOrdreInverse() {
            // Même situation que ci-dessus, inscriptions présentées de la plus ancienne à la plus
            // récente : le résultat ne doit pas dépendre de l'ordre de lecture.
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-01-15"));
            givenCatchUp(host, missed);

            GroupEntity group = new GroupEntity();
            group.setId(ORIGIN_GROUP_ID);
            when(studentGroupRepository.findByStudentIdAndActiveTrue(STUDENT_ID)).thenReturn(List.of(
                    StudentGroupEntity.builder().group(group).dateAssigned(date("2026-01-01")).build(),
                    StudentGroupEntity.builder().group(group).dateAssigned(date("2026-02-01")).build()));

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isTrue();
        }

        @Test
        @DisplayName("inscription sans date : ignorée, donc consommé")
        void inscriptionSansDate() {
            // Une inscription sans date n'établit aucun droit : impossible de dire qu'une séance
            // lui est postérieure.
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
            givenCatchUp(host, missed);
            givenEnrolment(ORIGIN_GROUP_ID, null);

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("séance sans rattrapage du tout : non compensée")
        void seanceSansAucunRattrapage() {
            // La liste de qualifications est absente pour cette séance : elle ne doit pas être
            // considérée comme gratuite.
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
            givenCatchUp(host, missed);
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(888_888L)).isFalse();
        }

        @Test
        @DisplayName("inscription sans groupe : ignorée")
        void inscriptionSansGroupe() {
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
            givenCatchUp(host, missed);
            when(studentGroupRepository.findByStudentIdAndActiveTrue(STUDENT_ID)).thenReturn(
                    List.of(StudentGroupEntity.builder().group(null).dateAssigned(date("2026-01-01")).build()));

            assertThat(qualifier.view(STUDENT_ID).isFullyCompensated(HOST_SESSION_ID)).isFalse();
        }

        @Test
        @DisplayName("une séance couverte par un compensatoire ET un consommé n'est pas gratuite")
        void melangeSurLaMemeSeance() {
            // isFullyCompensated exige que TOUTES les qualifications soient compensatoires.
            SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
            SessionEntity compense = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
            SessionEntity consomme = session(201L, ORIGIN_GROUP_ID, 400L, date("2025-11-01"));

            AttendanceEntity a1 = catchUp(host, compense);
            AttendanceEntity a2 = catchUp(host, consomme);
            when(attendanceRepository.findByStudentIdAndIsCatchUpTrueAndActiveTrue(STUDENT_ID))
                    .thenReturn(List.of(a1, a2));
            when(sessionRepository.findAllById(any())).thenReturn(List.of(compense, consomme));
            givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

            CatchUpView view = qualifier.view(STUDENT_ID);

            assertThat(view.qualificationsBySessionId().get(HOST_SESSION_ID))
                    .containsExactlyInAnyOrder(Qualification.COMPENSATOIRE, Qualification.CONSOMME);
            assertThat(view.isFullyCompensated(HOST_SESSION_ID)).isFalse();
        }
    }

    @Test
    @DisplayName("isCompensatedAway distingue la séance rattrapée de celle qui ne l'est pas")
    void compensatedAwayDistingueLesDeuxCas() {
        SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
        SessionEntity missed = session(MISSED_SESSION_ID, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
        givenCatchUp(host, missed);
        givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

        CatchUpView view = qualifier.view(STUDENT_ID);

        assertThat(view.isCompensatedAway(MISSED_SESSION_ID)).isTrue();
        // Une séance quelconque non rattrapée ne doit pas être considérée comme compensée.
        assertThat(view.isCompensatedAway(999_999L)).isFalse();
    }

    @Test
    @DisplayName("le nombre de requêtes ne dépend pas du nombre de rattrapages")
    void nombreDeRequetesConstant() {
        SessionEntity host = session(HOST_SESSION_ID, 20L, 300L, date("2026-02-10"));
        List<AttendanceEntity> nombreux = new java.util.ArrayList<>();
        List<SessionEntity> manquees = new java.util.ArrayList<>();
        for (long i = 1; i <= 30; i++) {
            SessionEntity missed = session(1000L + i, ORIGIN_GROUP_ID, 400L, date("2026-02-05"));
            manquees.add(missed);
            nombreux.add(catchUp(session(HOST_SESSION_ID + i, 20L, 300L, date("2026-02-10")), missed));
        }
        when(attendanceRepository.findByStudentIdAndIsCatchUpTrueAndActiveTrue(STUDENT_ID))
                .thenReturn(nombreux);
        when(sessionRepository.findAllById(any())).thenReturn(manquees);
        givenEnrolment(ORIGIN_GROUP_ID, date("2026-01-01"));

        qualifier.view(STUDENT_ID);

        // Trois lectures pour trente rattrapages : c'est ce qui rend la vue utilisable à chaque
        // calcul de coût sans dégrader la réponse.
        org.mockito.Mockito.verify(attendanceRepository)
                .findByStudentIdAndIsCatchUpTrueAndActiveTrue(anyLong());
        org.mockito.Mockito.verify(sessionRepository).findAllById(any());
        org.mockito.Mockito.verify(studentGroupRepository).findByStudentIdAndActiveTrue(anyLong());
        org.mockito.Mockito.verifyNoMoreInteractions(
                attendanceRepository, sessionRepository, studentGroupRepository);
        assertThat(host).isNotNull();
    }
}
