package com.school.management.service;

import com.school.management.dto.StudentAbsenceDTO;
import com.school.management.dto.catchup.CatchUpBillingAuditDTO;
import com.school.management.dto.catchup.PendingCatchUpDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.GroupTypeEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
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
 * Vues de lecture de la facturation des rattrapages : liste à préciser, sélecteur de séance
 * manquée, piste d'audit aplatie.
 *
 * <p>Ces chemins ne calculent aucun montant, mais ils <strong>fondent la décision</strong> :
 * proposer une séance manquée qui ne devrait pas l'être, ou afficher un niveau et une matière
 * faux, conduit l'administrateur à trancher sur une information erronée. C'est pourquoi ils sont
 * soumis au même seuil de couverture que les services de calcul.</p>
 *
 * <p>Les cas dégénérés (présence sans étudiant, sans séance, sans groupe) sont testés
 * explicitement : ils existent en base — une présence peut perdre sa séance après une
 * suppression — et le repli doit être un affichage lacunaire, jamais une exception qui ferait
 * disparaître toute la liste.</p>
 */
class CatchUpBillingDisplayPropertyTest {

    private static ConfigurableApplicationContext context;
    private static CatchUpBillingResolutionService resolutionService;
    private static CatchUpRoutingService routingService;
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
        context = new SpringApplicationBuilder(DisplayTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:catchup-display-pbt;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off");

        resolutionService = context.getBean(CatchUpBillingResolutionService.class);
        routingService = context.getBean(CatchUpRoutingService.class);
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
    // Liste à préciser : contenu lisible et vérifiable
    // ------------------------------------------------------------------

    /**
     * La liste affiche niveau et matière du groupe d'accueil.
     *
     * <p>Ce ne sont pas des colonnes décoratives : ce sont les deux dimensions du test qui a
     * classé la présence en vrai rattrapage. Les afficher rend le routage vérifiable par le
     * lecteur au lieu d'opaque.</p>
     */
    @Property(tries = 100)
    void pendingListCarriesLevelAndSubject(@ForAll boolean withSeries) {

        Fixture f = freshFixture(withSeries);

        List<PendingCatchUpDTO> pending = resolutionService.findPendingForDisplay();

        assertThat(pending).hasSize(1);
        PendingCatchUpDTO row = pending.get(0);
        assertThat(row.attendanceId()).isEqualTo(f.pendingAttendanceId);
        assertThat(row.studentName()).isEqualTo("Étudiant Test");
        assertThat(row.hostGroupName()).isEqualTo("Groupe d'accueil");
        assertThat(row.levelName()).isEqualTo("Niveau");
        assertThat(row.subjectName()).isEqualTo("Matière");
        assertThat(row.sessionTitle()).isEqualTo("Séance d'accueil");
        assertThat(row.sessionDate()).isNotNull();

        // La série n'est renseignée que si la présence en porte une : l'inventer masquerait une
        // donnée manquante que l'administrateur doit voir.
        if (withSeries) {
            assertThat(row.seriesId()).isEqualTo(f.hostSeriesId);
            assertThat(row.seriesName()).isEqualTo("Série d'accueil");
        } else {
            assertThat(row.seriesId()).isNull();
            assertThat(row.seriesName()).isNull();
        }
    }

    /**
     * Une présence lacunaire s'affiche en lacunaire, elle ne fait pas tomber la liste.
     *
     * <p>Une présence peut perdre sa séance ou son étudiant (suppression, reprise de données).
     * Lever une exception ferait disparaître <em>toute</em> la liste des décisions en attente,
     * donc tous les autres rattrapages, à cause d'une seule ligne abîmée.</p>
     */
    @Property(tries = 100)
    void degenerateAttendanceStillRendersARow(@ForAll boolean withStudent) {

        resetDatabase();

        AttendanceEntity orphan = AttendanceEntity.builder()
                .student(withStudent ? studentRepository.save(nameless()) : null)
                .isPresent(true).isCatchUp(true)
                .catchUpBillingState(CatchUpBillingState.PENDING)
                .build();
        orphan.setActive(true);
        Long id = attendanceRepository.save(orphan).getId();

        List<PendingCatchUpDTO> pending = resolutionService.findPendingForDisplay();

        assertThat(pending).hasSize(1);
        PendingCatchUpDTO row = pending.get(0);
        assertThat(row.attendanceId()).isEqualTo(id);
        assertThat(row.sessionId()).isNull();
        assertThat(row.hostGroupId()).isNull();
        assertThat(row.levelName()).isNull();
        assertThat(row.subjectName()).isNull();
        // Un nom vide est rendu nul plutôt qu'en espace isolé : « — » à l'écran est lisible,
        // une chaîne blanche ressemble à un nom perdu.
        assertThat(row.studentName()).isNull();

        // La vue unitaire répond de la même façon, et 404 sur un identifiant inconnu.
        assertThat(resolutionService.pendingViewOf(id).attendanceId()).isEqualTo(id);
        assertThatThrownBy(() -> resolutionService.pendingViewOf(999_999L))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** Le groupe d'accueil est déduit de la séance lorsque la présence n'en porte pas. */
    @Property(tries = 100)
    void hostGroupFallsBackToTheSessionGroup(@ForAll boolean groupOnAttendance) {

        Fixture f = freshFixture(true);

        AttendanceEntity attendance = attendanceRepository.findById(f.pendingAttendanceId).orElseThrow();
        if (!groupOnAttendance) {
            // Présence sans groupe : celui de la séance doit prendre le relais, sinon la ligne
            // s'afficherait sans groupe alors que l'information existe.
            attendance.setGroup(null);
            attendanceRepository.save(attendance);
        }

        PendingCatchUpDTO row = resolutionService.pendingViewOf(f.pendingAttendanceId);
        assertThat(row.hostGroupId()).isEqualTo(f.hostGroupId);
        assertThat(row.hostGroupName()).isEqualTo("Groupe d'accueil");
    }

    // ------------------------------------------------------------------
    // Sélecteur de séance manquée
    // ------------------------------------------------------------------

    /**
     * Le sélecteur applique exactement le test qui a décidé du routage.
     *
     * <p>Proposer une absence d'un autre niveau, d'une autre matière ou d'une autre année
     * laisserait désigner une séance que le modèle n'autorise pas à rattraper : cette présence
     * aurait été classée « facturée sur place », et lui rattacher une séance manquée produirait un
     * rattrapage que rien ne justifie.</p>
     */
    @Property(tries = 100)
    void selectorAppliesTheRoutingTest(
            @ForAll boolean sameLevel,
            @ForAll boolean sameSubject,
            @ForAll boolean sameYear) {

        Fixture f = freshFixture(true);

        // Le socle inscrit l'étudiant dans un groupe d'origine concordant : le retirer isole la
        // dimension testée. Sans cela, ce groupe suffirait à lui seul à rendre le verdict positif,
        // et le test passerait quelles que soient les variations ci-dessous — il ne vérifierait
        // plus rien.
        studentGroupRepository.deleteAll();

        // Groupe d'origine, dont chaque dimension varie indépendamment.
        LevelEntity otherLevel = levelRepository.save(LevelEntity.builder().name("Autre niveau").build());
        SubjectEntity otherSubject = subjectRepository.save(SubjectEntity.builder().name("Autre matière").build());
        SchoolYearEntity otherYear = schoolYearRepository.save(SchoolYearEntity.builder()
                .label("2001-2002").startDate(date(2001, 9, 1)).endDate(date(2002, 6, 30))
                .isCurrent(false).build());

        GroupEntity originGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe d'origine")
                .level(sameLevel ? f.level : otherLevel)
                .subject(sameSubject ? f.subject : otherSubject)
                .schoolYear(sameYear ? f.year : otherYear)
                .groupType(f.groupType).price(f.price)
                .build());
        studentGroupRepository.save(StudentGroupEntity.builder()
                .student(f.student).group(originGroup).build());

        SessionSeriesEntity originSeries = seriesRepository.save(SessionSeriesEntity.builder()
                .group(originGroup).name("Série d'origine").totalSessions(4)
                .serieTimeStart(date(2000, 9, 1)).serieTimeEnd(date(2000, 9, 30))
                .build());
        SessionEntity missed = sessionRepository.save(SessionEntity.builder()
                .title("Séance manquée").group(originGroup).sessionSeries(originSeries)
                .sessionTimeStart(date(2000, 9, 10))
                .build());
        // Absence sur cette séance : c'est ce qui la rend éligible au rattrapage.
        AttendanceEntity absence = AttendanceEntity.builder()
                .student(f.student).session(missed).sessionSeries(originSeries).group(originGroup)
                .isPresent(false).isCatchUp(false)
                .build();
        absence.setActive(true);
        attendanceRepository.save(absence);

        List<StudentAbsenceDTO> proposed =
                resolutionService.eligibleMissedSessions(f.studentId, f.hostGroupId);

        boolean expected = sameLevel && sameSubject && sameYear;
        assertThat(proposed.stream().anyMatch(a -> missed.getId().equals(a.sessionId())))
                .as("niveau identique=%s, matière identique=%s, année identique=%s "
                                + "→ la séance manquée doit %sêtre proposée",
                        sameLevel, sameSubject, sameYear, expected ? "" : "ne pas ")
                .isEqualTo(expected);

        // Le verdict du routage et celui du sélecteur reposent sur le même test : ils ne peuvent
        // pas se contredire, sinon un rattrapage serait classé Cas 1 sans qu'aucune séance
        // manquée ne lui soit proposable.
        assertThat(routingService.route(f.studentId, f.hostGroupId)
                        == CatchUpRoutingService.RoutingVerdict.TRUE_CATCH_UP)
                .isEqualTo(expected);

        // Groupe d'accueil inconnu : 404 plutôt qu'une liste vide, qui se lirait « aucune absence ».
        assertThatThrownBy(() -> resolutionService.eligibleMissedSessions(f.studentId, 999_999L))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** Le groupe d'accueil ne se propose jamais lui-même : on ne rattrape pas chez soi. */
    @Property(tries = 100)
    void hostGroupNeverProposesItsOwnSessions(@ForAll boolean enrolled) {

        Fixture f = freshFixture(true);

        if (enrolled) {
            studentGroupRepository.save(StudentGroupEntity.builder()
                    .student(f.student).group(groupRepository.findById(f.hostGroupId).orElseThrow())
                    .build());
        }

        // Absence sur une séance DU groupe d'accueil.
        SessionEntity hostMissed = sessionRepository.save(SessionEntity.builder()
                .title("Séance d'accueil manquée")
                .group(groupRepository.findById(f.hostGroupId).orElseThrow())
                .sessionSeries(seriesRepository.findById(f.hostSeriesId).orElseThrow())
                .sessionTimeStart(date(2000, 9, 26))
                .build());
        AttendanceEntity absence = AttendanceEntity.builder()
                .student(f.student).session(hostMissed)
                .sessionSeries(seriesRepository.findById(f.hostSeriesId).orElseThrow())
                .group(groupRepository.findById(f.hostGroupId).orElseThrow())
                .isPresent(false).isCatchUp(false)
                .build();
        absence.setActive(true);
        attendanceRepository.save(absence);

        assertThat(resolutionService.eligibleMissedSessions(f.studentId, f.hostGroupId))
                .as("une séance du groupe d'accueil n'est jamais une séance à y rattraper")
                .noneSatisfy(a -> assertThat(a.sessionId()).isEqualTo(hostMissed.getId()));
    }

    // ------------------------------------------------------------------
    // Piste d'audit aplatie
    // ------------------------------------------------------------------

    /** La piste d'audit aplatie restitue chaque champ de l'entrée, sans en perdre aucun. */
    @Property(tries = 100)
    void auditTrailForDisplayKeepsEveryField(@ForAll boolean alreadyPaid) {

        Fixture f = freshFixture(true);
        prepareOriginAbsence(f);

        resolutionService.resolve(f.pendingAttendanceId, f.missedSessionId, alreadyPaid, "motif initial");

        List<CatchUpBillingAuditDTO> trail =
                resolutionService.auditTrailForDisplay(f.pendingAttendanceId);

        assertThat(trail).hasSize(2);
        assertThat(trail).allSatisfy(entry -> {
            assertThat(entry.id()).isNotNull();
            assertThat(entry.field()).isIn("MISSED_SESSION", "ALREADY_PAID");
            assertThat(entry.performedBy()).isEqualTo("admin-test");
            assertThat(entry.performedAt()).isNotNull();
            assertThat(entry.sequenceRank()).isPositive();
            assertThat(entry.comment()).isEqualTo("motif initial");
        });

        // La décision est restituée telle qu'elle a été prise, en texte.
        assertThat(trail).anySatisfy(entry -> {
            assertThat(entry.field()).isEqualTo("ALREADY_PAID");
            assertThat(entry.newValue()).isEqualTo(String.valueOf(alreadyPaid));
        });

        // Un rattrapage sans aucune correction a une piste vide, pas nulle.
        assertThat(resolutionService.auditTrailForDisplay(999_999L)).isEmpty();
        assertThat(resolutionService.auditTrail(f.pendingAttendanceId)).hasSize(2);
    }

    /**
     * Corriger la seule séance manquée laisse la décision de facturation intacte.
     *
     * <p>Le cas est courant, et c'est la raison pour laquelle les deux champs de la correction sont
     * facultatifs : réexiger la décision ferait ressaisir une valeur inchangée, donc écrire une
     * trace qui n'apprend rien.</p>
     */
    @Property(tries = 100)
    void correctingOnlyTheMissedSessionKeepsTheDecision(@ForAll boolean alreadyPaid) {

        Fixture f = freshFixture(true);
        prepareOriginAbsence(f);
        resolutionService.resolve(f.pendingAttendanceId, f.missedSessionId, alreadyPaid, null);

        // Seconde séance manquée dans la même série d'origine.
        SessionEntity otherMissed = sessionRepository.save(SessionEntity.builder()
                .title("Autre séance manquée")
                .group(groupRepository.findById(f.originGroupId).orElseThrow())
                .sessionSeries(seriesRepository.findById(f.originSeriesId).orElseThrow())
                .sessionTimeStart(date(2000, 9, 17))
                .build());

        AttendanceEntity corrected = resolutionService.correct(
                f.pendingAttendanceId, otherMissed.getId(), null, "erreur de séance");

        assertThat(corrected.getMissedSession().getId()).isEqualTo(otherMissed.getId());
        assertThat(corrected.getMissedSessionAlreadyPaid())
                .as("la décision de facturation n'était pas visée par la correction")
                .isEqualTo(alreadyPaid);

        // Une seule trace de plus : le champ inchangé n'en produit aucune.
        assertThat(resolutionService.auditTrailForDisplay(f.pendingAttendanceId)).hasSize(3);
    }

    /** Une présence sans étudiant ne peut pas être résolue : le contrôle d'unicité est impossible. */
    @Property(tries = 100)
    void attendanceWithoutStudentCannotBeResolved(@ForAll boolean alreadyPaid) {

        Fixture f = freshFixture(true);

        AttendanceEntity attendance = attendanceRepository.findById(f.pendingAttendanceId).orElseThrow();
        attendance.setStudent(null);
        attendanceRepository.save(attendance);

        assertThatThrownBy(() -> resolutionService.resolve(
                f.pendingAttendanceId, f.missedSessionId, alreadyPaid, null))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /** La séance manquée est obligatoire : un identifiant nul est refusé, pas ignoré. */
    @Property(tries = 100)
    void missedSessionIdIsMandatory(@ForAll boolean alreadyPaid) {

        Fixture f = freshFixture(true);

        assertThatThrownBy(() -> resolutionService.resolve(
                f.pendingAttendanceId, null, alreadyPaid, null))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /**
     * Un groupe d'accueil sans niveau, sans matière ou sans année ne propose aucune séance manquée.
     *
     * <p>Le sélecteur applique le test de routage : sans l'une de ses trois dimensions, aucune
     * correspondance ne peut être établie. Proposer malgré tout une absence laisserait désigner une
     * séance manquée sur une hypothèse, et donc facturer sur une hypothèse. Les trois champs sont
     * exercés séparément parce que chacun ouvre sa propre branche.</p>
     */
    @Property(tries = 100)
    void incompleteHostGroupProposesNothing(
            @ForAll boolean withLevel,
            @ForAll boolean withSubject,
            @ForAll boolean withYear) {

        Fixture f = freshFixture(true);
        // Sans absence marquée, la séance manquée n'est pas éligible : le cas « groupe complet »
        // ne prouverait alors rien, la liste étant vide pour une autre raison.
        prepareOriginAbsence(f);

        // Groupe d'accueil privé d'une ou plusieurs de ses caractéristiques déterminantes.
        GroupEntity incompleteHost = groupRepository.save(GroupEntity.builder()
                .name("Accueil incomplet")
                .level(withLevel ? f.level : null)
                .subject(withSubject ? f.subject : null)
                .schoolYear(withYear ? f.year : null)
                .groupType(f.groupType).price(f.price)
                .build());

        List<StudentAbsenceDTO> proposed =
                resolutionService.eligibleMissedSessions(f.studentId, incompleteHost.getId());

        if (withLevel && withSubject && withYear) {
            // Groupe complet : le groupe d'origine du socle correspond, l'absence est proposée.
            assertThat(proposed)
                    .as("groupe d'accueil complet : la correspondance est possible")
                    .isNotEmpty();
        } else {
            assertThat(proposed)
                    .as("niveau=%s, matière=%s, année=%s : aucune correspondance possible",
                            withLevel, withSubject, withYear)
                    .isEmpty();
        }
    }

    /**
     * Un groupe d'origine incomplet n'est jamais proposé, même si le groupe d'accueil est complet.
     *
     * <p>La correspondance se juge des deux côtés : un {@code null} d'un côté ne correspond pas à un
     * identifiant de l'autre, ni même à un {@code null}. Deux groupes sans niveau ne sont pas « de
     * même niveau » — ils sont tous deux sans niveau, ce qui n'établit rien.</p>
     */
    @Property(tries = 100)
    void incompleteOriginGroupIsNeverProposed(
            @ForAll boolean withLevel,
            @ForAll boolean withSubject,
            @ForAll boolean withYear) {

        Fixture f = freshFixture(true);
        prepareOriginAbsence(f);

        // Groupe d'origine incomplet, avec une absence non résolue de l'étudiant.
        GroupEntity incompleteOrigin = groupRepository.save(GroupEntity.builder()
                .name("Origine incomplète")
                .level(withLevel ? f.level : null)
                .subject(withSubject ? f.subject : null)
                .schoolYear(withYear ? f.year : null)
                .groupType(f.groupType).price(f.price)
                .build());
        studentGroupRepository.save(StudentGroupEntity.builder()
                .student(f.student).group(incompleteOrigin).build());

        SessionSeriesEntity series = seriesRepository.save(SessionSeriesEntity.builder()
                .group(incompleteOrigin).name("Série incomplète").totalSessions(4)
                .serieTimeStart(date(2000, 9, 1)).serieTimeEnd(date(2000, 9, 30))
                .build());
        SessionEntity missed = sessionRepository.save(SessionEntity.builder()
                .title("Séance du groupe incomplet").group(incompleteOrigin).sessionSeries(series)
                .sessionTimeStart(date(2000, 9, 8))
                .build());
        AttendanceEntity absence = AttendanceEntity.builder()
                .student(f.student).session(missed).sessionSeries(series).group(incompleteOrigin)
                .isPresent(false).isCatchUp(false)
                .build();
        absence.setActive(true);
        attendanceRepository.save(absence);

        List<Long> proposedSessionIds = resolutionService
                .eligibleMissedSessions(f.studentId, f.hostGroupId).stream()
                .map(StudentAbsenceDTO::sessionId)
                .toList();

        if (withLevel && withSubject && withYear) {
            // Groupe d'origine complet et correspondant : sa séance est proposée comme les autres.
            assertThat(proposedSessionIds)
                    .as("groupe d'origine complet et correspondant : proposé")
                    .contains(missed.getId());
        } else {
            // Une caractéristique manquante ne « correspond » à rien, pas même à une autre
            // caractéristique manquante : deux groupes sans niveau ne sont pas de même niveau.
            assertThat(proposedSessionIds)
                    .as("niveau=%s, matière=%s, année=%s côté origine : jamais proposé",
                            withLevel, withSubject, withYear)
                    .doesNotContain(missed.getId());
        }
    }

    /**
     * La trace d'une première désignation part d'une valeur nulle, et la correction d'une décision
     * jamais tranchée aussi.
     *
     * <p>{@code null} n'est pas un défaut mais une valeur porteuse de sens — « aucune séance
     * désignée », « décision non tranchée ». La piste d'audit doit la restituer telle quelle, sinon
     * la première décision paraît sortie de nulle part.</p>
     */
    @Property(tries = 100)
    void auditTrailRendersNullPreviousValues(@ForAll boolean alreadyPaid) {

        Fixture f = freshFixture(true);

        resolutionService.resolve(f.pendingAttendanceId, f.missedSessionId, alreadyPaid, "première");

        List<CatchUpBillingAuditDTO> trail =
                resolutionService.auditTrailForDisplay(f.pendingAttendanceId);

        // La désignation initiale de la séance manquée n'a pas d'antécédent : oldValue est nulle.
        assertThat(trail)
                .filteredOn(e -> "MISSED_SESSION".equals(e.field()))
                .allSatisfy(e -> {
                    assertThat(e.oldValue()).isNull();
                    assertThat(e.newValue()).isEqualTo(String.valueOf(f.missedSessionId));
                });

        // La décision initiale non plus : elle passe de « non tranchée » à une valeur explicite.
        assertThat(trail)
                .filteredOn(e -> "ALREADY_PAID".equals(e.field()))
                .allSatisfy(e -> {
                    assertThat(e.oldValue()).isNull();
                    assertThat(e.newValue()).isEqualTo(String.valueOf(alreadyPaid));
                });
    }

    /**
     * Une absence sans groupe n'est jamais proposée comme séance manquée.
     *
     * <p>Le groupe est la seule information qui permette d'appliquer le test niveau + matière.
     * Sans lui, proposer l'absence reviendrait à laisser désigner une séance manquée sans avoir
     * vérifié qu'une place était réservée quelque part — donc à facturer sur une hypothèse.</p>
     */
    @Property(tries = 100)
    void absenceWithoutGroupIsNeverProposed(@ForAll boolean withHostGroupMatch) {

        Fixture f = freshFixture(true);
        prepareOriginAbsence(f);

        // Séance d'origine délibérément privée de groupe : l'absence qui la couvre remonte donc
        // avec un groupId nul.
        SessionEntity orphanSession = sessionRepository.save(SessionEntity.builder()
                .title("Séance sans groupe")
                .sessionSeries(seriesRepository.findById(f.originSeriesId).orElseThrow())
                .sessionTimeStart(date(2000, 9, 6))
                .build());
        AttendanceEntity orphanAbsence = AttendanceEntity.builder()
                .student(f.student).session(orphanSession)
                .sessionSeries(seriesRepository.findById(f.originSeriesId).orElseThrow())
                .isPresent(false).isCatchUp(false)
                .build();
        orphanAbsence.setActive(true);
        attendanceRepository.save(orphanAbsence);

        Long hostGroupId = withHostGroupMatch ? f.hostGroupId : f.originGroupId;
        List<Long> proposed = resolutionService.eligibleMissedSessions(f.studentId, hostGroupId).stream()
                .map(StudentAbsenceDTO::sessionId)
                .toList();

        assertThat(proposed)
                .as("une absence sans groupe ne peut pas être confrontée au test niveau + matière")
                .doesNotContain(orphanSession.getId());
    }

    /**
     * Corriger la seule séance manquée d'un rattrapage dont la décision n'a jamais été tranchée
     * enregistre {@code null} comme valeur antérieure.
     *
     * <p>Cas atteignable : la résolution pose les deux décisions, mais une correction ultérieure de
     * la seule séance manquée doit tracer l'état antérieur de l'autre champ tel qu'il était. La
     * trace doit distinguer « était faux » de « n'était pas tranché » — les deux se liraient
     * autrement comme une décision prise.</p>
     */
    @Property(tries = 100)
    void auditRendersAnUntouchedDecisionAsNull(@ForAll boolean alreadyPaid) {

        Fixture f = freshFixture(true);

        // Présence à préciser dont la décision reste nulle : on force la correction à lire un
        // `missedSessionAlreadyPaid` non tranché.
        AttendanceEntity attendance = attendanceRepository.findById(f.pendingAttendanceId).orElseThrow();
        attendance.setMissedSession(sessionRepository.findById(f.missedSessionId).orElseThrow());
        attendance.setCatchUpBillingState(CatchUpBillingState.RESOLVED);
        attendance.setMissedSessionAlreadyPaid(null);
        attendanceRepository.save(attendance);

        resolutionService.correct(f.pendingAttendanceId, null, alreadyPaid, "première décision");

        assertThat(resolutionService.auditTrailForDisplay(f.pendingAttendanceId))
                .filteredOn(e -> "ALREADY_PAID".equals(e.field()))
                .as("une décision jamais tranchée est tracée comme nulle, non comme « faux »")
                .anySatisfy(e -> {
                    assertThat(e.oldValue()).isNull();
                    assertThat(e.newValue()).isEqualTo(String.valueOf(alreadyPaid));
                });
    }

    /**
     * Une ligne dont le groupe d'accueil n'a ni niveau ni matière s'affiche quand même, colonnes
     * vides.
     *
     * <p>Le référentiel peut être incomplet — un groupe créé sans niveau, par import ou à la main.
     * La liste des rattrapages à préciser doit alors rester lisible : la faire échouer masquerait
     * tous les autres rattrapages à cause d'une seule ligne, et ce sont eux qui portent l'argent
     * à décider.</p>
     */
    @Property(tries = 100)
    void pendingRowSurvivesAnIncompleteHostGroup(
            @ForAll boolean withLevel,
            @ForAll boolean withSubject) {

        Fixture f = freshFixture(true);

        // Le groupe d'accueil de la présence est remplacé par un groupe au référentiel incomplet.
        GroupEntity incompleteHost = groupRepository.save(GroupEntity.builder()
                .name("Accueil sans référentiel complet")
                .level(withLevel ? f.level : null)
                .subject(withSubject ? f.subject : null)
                .schoolYear(f.year).groupType(f.groupType).price(f.price)
                .build());

        AttendanceEntity attendance = attendanceRepository.findById(f.pendingAttendanceId).orElseThrow();
        attendance.setGroup(incompleteHost);
        attendanceRepository.save(attendance);

        List<PendingCatchUpDTO> pending = resolutionService.findPendingForDisplay();

        assertThat(pending)
                .as("la ligne reste affichée malgré un référentiel incomplet")
                .hasSize(1);
        PendingCatchUpDTO row = pending.get(0);
        assertThat(row.hostGroupName()).isEqualTo("Accueil sans référentiel complet");
        assertThat(row.levelName()).isEqualTo(withLevel ? "Niveau" : null);
        assertThat(row.subjectName()).isEqualTo(withSubject ? "Matière" : null);
    }

    // ------------------------------------------------------------------
    // Socle de données
    // ------------------------------------------------------------------

    private record Fixture(Long pendingAttendanceId,
                           Long missedSessionId,
                           Long hostGroupId,
                           Long hostSeriesId,
                           Long originGroupId,
                           Long originSeriesId,
                           Long studentId,
                           StudentEntity student,
                           LevelEntity level,
                           SubjectEntity subject,
                           SchoolYearEntity year,
                           GroupTypeEntity groupType,
                           PricingEntity price) {
    }

    /**
     * Un étudiant, un groupe d'accueil et un groupe d'origine de même niveau et même matière, une
     * séance manquée dans une série d'origine, et une présence de rattrapage à préciser.
     *
     * @param withSeries rattacher ou non la présence d'accueil à une série, afin d'exercer les deux
     *                   côtés du repli d'affichage
     */
    private Fixture freshFixture(boolean withSeries) {
        resetDatabase();

        LevelEntity level = levelRepository.save(LevelEntity.builder().name("Niveau").build());
        SubjectEntity subject = subjectRepository.save(SubjectEntity.builder().name("Matière").build());
        SchoolYearEntity year = schoolYearRepository.save(SchoolYearEntity.builder()
                .label("2000-2001").startDate(date(2000, 9, 1)).endDate(date(2001, 6, 30))
                .isCurrent(true).build());
        GroupTypeEntity type = groupTypeRepository.save(GroupTypeEntity.builder().name("Petit").build());
        PricingEntity price = pricingRepository.save(PricingEntity.builder().price(2000.0).build());

        GroupEntity hostGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe d'accueil")
                .level(level).subject(subject).schoolYear(year).groupType(type).price(price)
                .build());
        GroupEntity originGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe d'origine")
                .level(level).subject(subject).schoolYear(year).groupType(type).price(price)
                .build());

        StudentEntity student = studentRepository.save(StudentEntity.builder()
                .firstName("Étudiant").lastName("Test").build());
        studentGroupRepository.save(StudentGroupEntity.builder()
                .student(student).group(originGroup).build());

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

        AttendanceEntity pending = AttendanceEntity.builder()
                .student(student).session(hostSession)
                .sessionSeries(withSeries ? hostSeries : null)
                .group(hostGroup)
                .isPresent(true).isCatchUp(true)
                .catchUpBillingState(CatchUpBillingState.PENDING)
                .build();
        pending.setActive(true);
        Long pendingId = attendanceRepository.save(pending).getId();

        return new Fixture(pendingId, missedSession.getId(), hostGroup.getId(), hostSeries.getId(),
                originGroup.getId(), originSeries.getId(), student.getId(),
                student, level, subject, year, type, price);
    }

    /** Marque l'absence de l'étudiant sur la séance manquée, ce qui la rend éligible. */
    private void prepareOriginAbsence(Fixture f) {
        AttendanceEntity absence = AttendanceEntity.builder()
                .student(f.student)
                .session(sessionRepository.findById(f.missedSessionId).orElseThrow())
                .sessionSeries(seriesRepository.findById(f.originSeriesId).orElseThrow())
                .group(groupRepository.findById(f.originGroupId).orElseThrow())
                .isPresent(false).isCatchUp(false)
                .build();
        absence.setActive(true);
        attendanceRepository.save(absence);
    }

    /** Étudiant sans nom : exerce le repli du nom complet vide. */
    private static StudentEntity nameless() {
        return StudentEntity.builder().firstName(null).lastName(null).build();
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
    static class DisplayTestContext {

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("admin-test");
        }

        @Bean
        CatchUpRoutingService catchUpRoutingService(StudentGroupRepository studentGroupRepository,
                                                   GroupRepository groupRepository) {
            return new CatchUpRoutingService(studentGroupRepository, groupRepository);
        }

        @Bean
        CatchUpService catchUpService(CatchUpRequestRepository catchUpRequestRepository,
                                      AttendanceRepository attendanceRepository,
                                      SessionRepository sessionRepository) {
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
