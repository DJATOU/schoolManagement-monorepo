package com.school.management.service;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingAuditEntity;
import com.school.management.persistance.CatchUpBillingAuditField;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.GroupTypeEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.SubjectEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.CatchUpBillingAuditRepository;
import com.school.management.repository.CatchUpRequestRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.GroupTypeRepository;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.SubjectRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.PaymentStatusService;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests de propriété (jqwik) de la résolution et de la correction d'un rattrapage, en intégration
 * H2 réelle.
 *
 * <p>Trois propriétés du plan sont couvertes ici, car elles portent sur le même objet et le même
 * socle de données :</p>
 * <ul>
 *   <li><b>Property 4</b> — la décision « déjà payée » est respectée telle qu'elle a été prise ;</li>
 *   <li><b>Property 5</b> — une séance manquée n'est rattrapée qu'une fois ;</li>
 *   <li><b>Property 6</b> — toute correction laisse une trace immuable.</li>
 * </ul>
 *
 * <p>jqwik s'exécute sur son propre moteur JUnit Platform : les tranches Spring ne s'appliquent pas
 * aux méthodes {@code @Property}. Un contexte ciblé est donc amorcé une fois par conteneur sur une
 * base H2 en mémoire, vidée à chaque essai.</p>
 */
class CatchUpBillingResolutionPropertyTest {

    private static ConfigurableApplicationContext context;
    private static CatchUpBillingResolutionService resolutionService;
    private static AttendanceRepository attendanceRepository;
    private static CatchUpBillingAuditRepository auditRepository;
    private static SessionRepository sessionRepository;
    private static SessionSeriesRepository seriesRepository;
    private static GroupRepository groupRepository;
    private static StudentRepository studentRepository;
    private static StudentGroupRepository studentGroupRepository;
    private static LevelRepository levelRepository;
    private static SubjectRepository subjectRepository;
    private static SchoolYearRepository schoolYearRepository;
    private static GroupTypeRepository groupTypeRepository;
    private static PricingRepository pricingRepository;
    private static CatchUpRequestRepository catchUpRequestRepository;

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(ResolutionTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:catchup-resolution-pbt;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off");

        resolutionService = context.getBean(CatchUpBillingResolutionService.class);
        attendanceRepository = context.getBean(AttendanceRepository.class);
        auditRepository = context.getBean(CatchUpBillingAuditRepository.class);
        sessionRepository = context.getBean(SessionRepository.class);
        seriesRepository = context.getBean(SessionSeriesRepository.class);
        groupRepository = context.getBean(GroupRepository.class);
        studentRepository = context.getBean(StudentRepository.class);
        studentGroupRepository = context.getBean(StudentGroupRepository.class);
        levelRepository = context.getBean(LevelRepository.class);
        subjectRepository = context.getBean(SubjectRepository.class);
        schoolYearRepository = context.getBean(SchoolYearRepository.class);
        groupTypeRepository = context.getBean(GroupTypeRepository.class);
        pricingRepository = context.getBean(PricingRepository.class);
        catchUpRequestRepository = context.getBean(CatchUpRequestRepository.class);
    }

    @AfterContainer
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    // ------------------------------------------------------------------
    // Property 4 — la décision est respectée telle qu'elle a été prise
    // ------------------------------------------------------------------

    // Feature: catch-up-billing-routing, Property 4: La décision « déjà payée » est respectée telle qu'elle a été prise
    @Property(tries = 100)
    void property4_storedDecisionIsHonouredAsTaken(@ForAll boolean alreadyPaid) {

        Fixture f = freshFixture();

        AttendanceEntity resolved = resolutionService.resolve(
                f.pendingAttendanceId, f.missedSessionId, alreadyPaid, "décision initiale");

        // (1) La décision est stockée telle quelle, et l'état passe à RESOLVED.
        assertThat(resolved.getMissedSessionAlreadyPaid())
                .as("la décision doit être enregistrée sans réinterprétation")
                .isEqualTo(alreadyPaid);
        assertThat(resolved.getCatchUpBillingState()).isEqualTo(CatchUpBillingState.RESOLVED);
        assertThat(resolved.getMissedSession().getId()).isEqualTo(f.missedSessionId);

        // (2) Relire la présence donne le même verdict : la décision est une donnée, pas un calcul.
        //     Recalculée depuis l'état de paiement, elle rendrait le coût d'une série sensible aux
        //     versements faits sur une autre, et dépendrait de l'ordre d'évaluation.
        AttendanceEntity reloaded = attendanceRepository.findById(f.pendingAttendanceId).orElseThrow();
        assertThat(reloaded.getMissedSessionAlreadyPaid()).isEqualTo(alreadyPaid);

        // (3) Le résultat ne dépend pas de l'ordre de lecture : deux relectures consécutives
        //     donnent la même réponse, aucun état intermédiaire n'étant recalculé.
        assertThat(attendanceRepository.findById(f.pendingAttendanceId).orElseThrow()
                .getMissedSessionAlreadyPaid()).isEqualTo(alreadyPaid);
    }

    /**
     * La décision est obligatoire : sans elle, le rattrapage reste à préciser.
     *
     * <p>C'est la traduction exécutable de « aucune valeur par défaut ». Si ce refus disparaissait,
     * un client omettant le champ enregistrerait « non déjà payée », donc une facturation, sans que
     * personne ne l'ait choisi.</p>
     */
    @Property(tries = 100)
    void property4b_decisionIsMandatory(@ForAll boolean withComment) {

        Fixture f = freshFixture();

        assertThatThrownBy(() -> resolutionService.resolve(
                f.pendingAttendanceId, f.missedSessionId, null, withComment ? "motif" : null))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        // La présence reste à préciser : un refus ne laisse aucune décision partielle derrière lui.
        AttendanceEntity untouched = attendanceRepository.findById(f.pendingAttendanceId).orElseThrow();
        assertThat(untouched.getCatchUpBillingState()).isEqualTo(CatchUpBillingState.PENDING);
        assertThat(untouched.getMissedSessionAlreadyPaid()).isNull();
        assertThat(auditRepository.findByAttendanceIdOrderByPerformedAtDescSequenceRankDesc(
                f.pendingAttendanceId)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Property 5 — une séance manquée n'est rattrapée qu'une fois
    // ------------------------------------------------------------------

    // Feature: catch-up-billing-routing, Property 5: Une séance manquée n'est rattrapée qu'une fois
    @Property(tries = 100)
    void property5_aMissedSessionIsCaughtUpOnlyOnce(@ForAll boolean firstDecision) {

        Fixture f = freshFixture();

        resolutionService.resolve(f.pendingAttendanceId, f.missedSessionId, firstDecision, null);

        // Un second rattrapage de la MÊME séance manquée est refusé : lequel des deux compenserait
        // l'absence ? Le décompte des séances suivies, donc le seuil de retard, deviendrait
        // indéterminé.
        assertThatThrownBy(() -> resolutionService.resolve(
                f.secondPendingAttendanceId, f.missedSessionId, true, null))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        // L'état existant est intact : un refus ne modifie rien.
        AttendanceEntity first = attendanceRepository.findById(f.pendingAttendanceId).orElseThrow();
        assertThat(first.getMissedSessionAlreadyPaid()).isEqualTo(firstDecision);
        assertThat(attendanceRepository.findById(f.secondPendingAttendanceId).orElseThrow()
                .getCatchUpBillingState()).isEqualTo(CatchUpBillingState.PENDING);

        // En revanche, se redésigner soi-même reste possible : ce n'est pas un doublon. Sans
        // l'exclusion de la présence courante, une correction se heurterait à elle-même.
        AttendanceEntity corrected = resolutionService.correct(
                f.pendingAttendanceId, f.missedSessionId, !firstDecision, "même séance, autre décision");
        assertThat(corrected.getMissedSessionAlreadyPaid()).isEqualTo(!firstDecision);
    }

    // ------------------------------------------------------------------
    // Property 6 — toute correction laisse une trace immuable
    // ------------------------------------------------------------------

    // Feature: catch-up-billing-routing, Property 6: Toute correction laisse une trace immuable
    @Property(tries = 100)
    void property6_everyCorrectionLeavesAnImmutableTrace(
            @ForAll @Size(min = 1, max = 4) List<Boolean> decisions) {

        Fixture f = freshFixture();

        resolutionService.resolve(f.pendingAttendanceId, f.missedSessionId, decisions.get(0), "initiale");

        long afterResolve = auditRepository
                .findByAttendanceIdOrderByPerformedAtDescSequenceRankDesc(f.pendingAttendanceId).size();
        // La résolution trace ses deux décisions : la séance manquée et « déjà payée ».
        assertThat(afterResolve).isEqualTo(2);

        // Chaque changement EFFECTIF ajoute une entrée ; réécrire la même valeur n'en ajoute aucune,
        // sans quoi le journal se remplirait de lignes sans information et la dernière décision
        // réelle deviendrait difficile à retrouver.
        Boolean current = decisions.get(0);
        long expected = afterResolve;
        for (int i = 1; i < decisions.size(); i++) {
            Boolean next = decisions.get(i);
            resolutionService.correct(f.pendingAttendanceId, null, next, "correction " + i);
            if (!next.equals(current)) {
                expected++;
                current = next;
            }
            assertThat(auditRepository
                    .findByAttendanceIdOrderByPerformedAtDescSequenceRankDesc(f.pendingAttendanceId))
                    .as("seule une correction effective écrit une entrée")
                    .hasSize((int) expected);
        }

        List<CatchUpBillingAuditEntity> trail = auditRepository
                .findByAttendanceIdOrderByPerformedAtDescSequenceRankDesc(f.pendingAttendanceId);

        // Chaque entrée est attribuable et horodatée : c'est ce qui permet de répondre à une
        // contestation.
        assertThat(trail).allSatisfy(entry -> {
            assertThat(entry.getPerformedBy()).isNotBlank();
            assertThat(entry.getPerformedAt()).isNotNull();
            assertThat(entry.getSequenceRank()).isPositive();
            assertThat(entry.getField()).isIn(CatchUpBillingAuditField.MISSED_SESSION,
                    CatchUpBillingAuditField.ALREADY_PAID);
        });

        // Les rangs de séquence sont strictement décroissants : « quelle est la dernière décision »
        // a une réponse, même si deux corrections tombent dans la même milliseconde.
        List<Long> ranks = trail.stream().map(CatchUpBillingAuditEntity::getSequenceRank).toList();
        assertThat(ranks).isSortedAccordingTo((a, b) -> Long.compare(b, a));
        assertThat(ranks).doesNotHaveDuplicates();

        // La trace survit à la suppression de la présence auditée : c'est justement après la
        // disparition d'une donnée qu'on a besoin de savoir qui l'a modifiée.
        attendanceRepository.deleteById(f.pendingAttendanceId);
        assertThat(auditRepository
                .findByAttendanceIdOrderByPerformedAtDescSequenceRankDesc(f.pendingAttendanceId))
                .as("la trace ne disparaît pas avec la présence auditée")
                .hasSize(trail.size());
    }

    /** Une correction sans aucun changement demandé est refusée plutôt que silencieuse. */
    @Property(tries = 100)
    void property6b_correctionWithoutChangeIsRejected(@ForAll boolean decision) {

        Fixture f = freshFixture();
        resolutionService.resolve(f.pendingAttendanceId, f.missedSessionId, decision, null);

        assertThatThrownBy(() -> resolutionService.correct(f.pendingAttendanceId, null, null, "vide"))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        // Corriger un rattrapage qui n'est pas résolu n'a pas de sens : il n'y a rien à corriger.
        assertThatThrownBy(() -> resolutionService.correct(
                f.secondPendingAttendanceId, f.missedSessionId, true, "sur un PENDING"))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        // Et résoudre deux fois non plus : la seconde écraserait une décision sans trace.
        assertThatThrownBy(() -> resolutionService.resolve(
                f.pendingAttendanceId, f.missedSessionId, !decision, "seconde résolution"))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /**
     * Une séance manquée sans série ne peut porter aucune facturation : la résolution est refusée.
     *
     * <p>Même exigence que {@code CatchUpService.complete} — la facturation est indexée par série,
     * une séance hors série serait invisible pour le calcul.</p>
     */
    @Property(tries = 100)
    void property4c_missedSessionWithoutSeriesIsRejected(@ForAll boolean alreadyPaid) {

        Fixture f = freshFixture();

        SessionEntity orphanSession = sessionRepository.save(SessionEntity.builder()
                .title("Séance sans série")
                .group(f.originGroup)
                .sessionTimeStart(date(2000, 10, 5))
                .build());

        assertThatThrownBy(() -> resolutionService.resolve(
                f.pendingAttendanceId, orphanSession.getId(), alreadyPaid, null))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        // Séance manquée inexistante : 404, et non un classement sur hypothèse.
        assertThatThrownBy(() -> resolutionService.resolve(
                f.pendingAttendanceId, 999_999L, alreadyPaid, null))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        // Présence inexistante : 404 également.
        assertThatThrownBy(() -> resolutionService.resolve(
                999_999L, f.missedSessionId, alreadyPaid, null))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** La liste des rattrapages à préciser ne contient que des PENDING actifs. */
    @Property(tries = 100)
    void property4d_pendingListHoldsOnlyUndecidedCatchUps(@ForAll boolean decision) {

        Fixture f = freshFixture();

        assertThat(resolutionService.findPending())
                .as("les deux rattrapages créés sont à préciser")
                .hasSize(2);

        resolutionService.resolve(f.pendingAttendanceId, f.missedSessionId, decision, null);

        List<AttendanceEntity> stillPending = resolutionService.findPending();
        assertThat(stillPending).hasSize(1);
        assertThat(stillPending.get(0).getId()).isEqualTo(f.secondPendingAttendanceId);
        assertThat(stillPending).allSatisfy(a ->
                assertThat(a.getCatchUpBillingState()).isEqualTo(CatchUpBillingState.PENDING));
    }

    // ------------------------------------------------------------------
    // Socle de données
    // ------------------------------------------------------------------

    /** Identifiants du jeu de données d'un essai. */
    private record Fixture(Long pendingAttendanceId,
                           Long secondPendingAttendanceId,
                           Long missedSessionId,
                           GroupEntity originGroup) {
    }

    /**
     * Jeu de données minimal : un étudiant inscrit dans un groupe d'origine (même niveau, même
     * matière que le groupe d'accueil), une séance manquée dans une série, et deux présences de
     * rattrapage à préciser dans le groupe d'accueil.
     */
    private Fixture freshFixture() {
        resetDatabase();

        LevelEntity level = levelRepository.save(LevelEntity.builder().name("Niveau").build());
        SubjectEntity subject = subjectRepository.save(SubjectEntity.builder().name("Matière").build());
        SchoolYearEntity year = schoolYearRepository.save(SchoolYearEntity.builder()
                .label("2000-2001")
                .startDate(date(2000, 9, 1))
                .endDate(date(2001, 6, 30))
                .isCurrent(true)
                .build());
        GroupTypeEntity type = groupTypeRepository.save(GroupTypeEntity.builder().name("Petit").build());
        PricingEntity price = pricingRepository.save(PricingEntity.builder().price(2000.0).build());

        GroupEntity originGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe d'origine")
                .level(level).subject(subject).schoolYear(year).groupType(type).price(price)
                .build());
        GroupEntity hostGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe d'accueil")
                .level(level).subject(subject).schoolYear(year).groupType(type).price(price)
                .build());

        StudentEntity student = studentRepository.save(StudentEntity.builder()
                .firstName("Étudiant").lastName("Test").build());

        SessionSeriesEntity originSeries = seriesRepository.save(SessionSeriesEntity.builder()
                .group(originGroup).name("Série d'origine").totalSessions(4)
                .serieTimeStart(date(2000, 9, 1)).serieTimeEnd(date(2000, 9, 30))
                .build());
        SessionEntity missedSession = sessionRepository.save(SessionEntity.builder()
                .title("Séance manquée").group(originGroup).sessionSeries(originSeries)
                .sessionTimeStart(date(2000, 9, 10))
                .build());

        SessionSeriesEntity hostSeries = seriesRepository.save(SessionSeriesEntity.builder()
                .group(hostGroup).name("Série d'accueil").totalSessions(4)
                .serieTimeStart(date(2000, 9, 1)).serieTimeEnd(date(2000, 9, 30))
                .build());
        SessionEntity hostSession = sessionRepository.save(SessionEntity.builder()
                .title("Séance d'accueil").group(hostGroup).sessionSeries(hostSeries)
                .sessionTimeStart(date(2000, 9, 12))
                .build());
        SessionEntity otherHostSession = sessionRepository.save(SessionEntity.builder()
                .title("Autre séance d'accueil").group(hostGroup).sessionSeries(hostSeries)
                .sessionTimeStart(date(2000, 9, 19))
                .build());

        Long first = savePendingCatchUp(student, hostSession, hostSeries, hostGroup);
        Long second = savePendingCatchUp(student, otherHostSession, hostSeries, hostGroup);

        return new Fixture(first, second, missedSession.getId(), originGroup);
    }

    private Long savePendingCatchUp(StudentEntity student, SessionEntity session,
                                    SessionSeriesEntity series, GroupEntity group) {
        AttendanceEntity attendance = AttendanceEntity.builder()
                .student(student).session(session).sessionSeries(series).group(group)
                .isPresent(true).isCatchUp(true)
                .catchUpBillingState(CatchUpBillingState.PENDING)
                .build();
        attendance.setActive(true);
        return attendanceRepository.save(attendance).getId();
    }

    private void resetDatabase() {
        auditRepository.deleteAll();
        catchUpRequestRepository.deleteAll();
        attendanceRepository.deleteAll();
        sessionRepository.deleteAll();
        seriesRepository.deleteAll();
        studentGroupRepository.deleteAll();
        groupRepository.deleteAll();
        studentRepository.deleteAll();
        levelRepository.deleteAll();
        subjectRepository.deleteAll();
        groupTypeRepository.deleteAll();
        pricingRepository.deleteAll();
        schoolYearRepository.deleteAll();
    }

    private static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @EntityScan("com.school.management.persistance")
    @EnableJpaRepositories("com.school.management.repository")
    static class ResolutionTestContext {

        /** Auteur fixe : la trace doit être attribuable, sa provenance exacte n'est pas le sujet ici. */
        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("admin-test");
        }

        @Bean
        CatchUpService catchUpService(CatchUpRequestRepository catchUpRequestRepository,
                                      AttendanceRepository attendanceRepository,
                                      SessionRepository sessionRepository) {
            // Le statut de paiement n'intervient pas dans les propriétés testées ici : la décision
            // « déjà payée » est prise par l'administrateur, pas déduite d'un état de paiement.
            return new CatchUpService(catchUpRequestRepository, attendanceRepository,
                    sessionRepository, mock(PaymentStatusService.class));
        }

        @Bean
        CatchUpBillingResolutionService catchUpBillingResolutionService(
                AttendanceRepository attendanceRepository,
                SessionRepository sessionRepository,
                GroupRepository groupRepository,
                CatchUpBillingAuditRepository auditRepository,
                AuditorAware<String> auditorAware,
                CatchUpService catchUpService) {
            return new CatchUpBillingResolutionService(attendanceRepository, sessionRepository,
                    groupRepository, auditRepository, auditorAware, catchUpService);
        }
    }
}
