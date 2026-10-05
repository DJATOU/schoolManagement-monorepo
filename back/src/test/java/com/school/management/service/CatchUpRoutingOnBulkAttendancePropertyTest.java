package com.school.management.service;

import com.school.management.mapper.AttendanceMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.persistance.SubjectEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.SubjectRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) du classement des rattrapages à la soumission en masse.
 *
 * <p>Feature: catch-up-billing-routing, Property 2: Tout rattrapage enregistré porte un état de
 * facturation.</p>
 *
 * <p>C'est la propriété qui referme le défaut d'origine. L'écran de validation d'une séance créait
 * des présences de rattrapage <strong>sans séance manquée et sans état</strong> : le calcul
 * retombait alors sur « aucune autre série ne facture cette séance » et le groupe d'accueil
 * facturait, y compris quand l'étudiant avait déjà payé la séance dans son propre groupe. L'énoncé
 * vérifié ici interdit cet état de fait : après enregistrement, aucun rattrapage n'est
 * <em>non classé</em>, et l'absence de séance manquée n'est tolérée que là où elle est légitime.</p>
 *
 * <p>Le cœur du test est qu'une séance manquée absente n'est jamais <em>tue</em> : elle est soit
 * assumée ({@code HOST_BILLED} — il n'y a rien à rattraper), soit revendiquée comme manquante
 * ({@code PENDING} — à préciser). C'est cette distinction que l'ancien modèle ne savait pas
 * exprimer, et son absence facturait au groupe d'accueil par défaut. La bi-implication stricte
 * « lien nul ⟺ facturée sur place » ne vaut qu'après résolution, {@code PENDING} étant justement
 * l'état « lien pas encore fourni ».</p>
 *
 * <p><b>Validates: lien obligatoire en Cas 1 uniquement, plus aucun NULL silencieux</b></p>
 */
class CatchUpRoutingOnBulkAttendancePropertyTest {

    private static ConfigurableApplicationContext context;
    private static AttendanceService attendanceService;
    private static AttendanceRepository attendanceRepository;
    private static StudentGroupRepository studentGroupRepository;
    private static GroupRepository groupRepository;
    private static StudentRepository studentRepository;
    private static SessionRepository sessionRepository;
    private static SessionSeriesRepository sessionSeriesRepository;
    private static LevelRepository levelRepository;
    private static SubjectRepository subjectRepository;
    private static SchoolYearRepository schoolYearRepository;

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(BulkRoutingTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:catchup-bulk-pbt;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off");

        attendanceService = context.getBean(AttendanceService.class);
        attendanceRepository = context.getBean(AttendanceRepository.class);
        studentGroupRepository = context.getBean(StudentGroupRepository.class);
        groupRepository = context.getBean(GroupRepository.class);
        studentRepository = context.getBean(StudentRepository.class);
        sessionRepository = context.getBean(SessionRepository.class);
        sessionSeriesRepository = context.getBean(SessionSeriesRepository.class);
        levelRepository = context.getBean(LevelRepository.class);
        subjectRepository = context.getBean(SubjectRepository.class);
        schoolYearRepository = context.getBean(SchoolYearRepository.class);
    }

    @AfterContainer
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    // Feature: catch-up-billing-routing, Property 2: Tout rattrapage enregistré porte un état de facturation
    @Property(tries = 100)
    void property2_everyRecordedCatchUpCarriesABillingState(
            @ForAll("studentProfiles") List<Boolean> hasSameLevelAndSubjectGroup) {

        resetDatabase();

        // --- Arrange : un groupe d'accueil, sa séance, et son groupe jumeau (même niveau+matière) ---
        LevelEntity level = levelRepository.save(LevelEntity.builder().name("Niveau").build());
        SubjectEntity subject = subjectRepository.save(SubjectEntity.builder().name("Matière").build());
        SubjectEntity otherSubject = subjectRepository.save(SubjectEntity.builder().name("Autre matière").build());
        SchoolYearEntity year = schoolYearRepository.save(SchoolYearEntity.builder()
                .label("2000-2001")
                .startDate(date(2000, 9, 1))
                .endDate(date(2001, 6, 30))
                .isCurrent(true)
                .build());

        GroupEntity hostGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe d'accueil").level(level).subject(subject).schoolYear(year).build());
        GroupEntity twinGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe jumeau").level(level).subject(subject).schoolYear(year).build());
        // Groupe leurre : même niveau, autre matière. Ne doit jamais ouvrir de rattrapage.
        GroupEntity decoyGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe leurre").level(level).subject(otherSubject).schoolYear(year).build());

        SessionSeriesEntity series = sessionSeriesRepository.save(SessionSeriesEntity.builder()
                .group(hostGroup).name("Série d'accueil").totalSessions(4).build());
        SessionEntity hostSession = sessionRepository.save(SessionEntity.builder()
                .title("Séance d'accueil").group(hostGroup).sessionSeries(series)
                .sessionTimeStart(date(2000, 10, 1)).active(true).build());

        // --- Arrange : un étudiant par profil généré, inscrit au jumeau ou au leurre ---
        List<AttendanceEntity> toSubmit = new ArrayList<>();
        List<StudentEntity> students = new ArrayList<>();
        for (int i = 0; i < hasSameLevelAndSubjectGroup.size(); i++) {
            boolean sameLevelAndSubject = hasSameLevelAndSubjectGroup.get(i);
            StudentEntity student = studentRepository.save(StudentEntity.builder()
                    .firstName("Étudiant" + i).lastName("Test").build());
            students.add(student);

            studentGroupRepository.save(StudentGroupEntity.builder()
                    .student(student)
                    .group(sameLevelAndSubject ? twinGroup : decoyGroup)
                    .build());

            // Exactement ce que produit l'écran de validation : rattrapage annoncé, aucune séance
            // manquée, aucun état. C'est l'entrée qui provoquait le défaut.
            toSubmit.add(AttendanceEntity.builder()
                    .student(student).session(hostSession).sessionSeries(series).group(hostGroup)
                    .isPresent(true).isCatchUp(true)
                    .build());
        }

        // --- Act ---
        attendanceService.saveAll(toSubmit);

        // --- Assert ---
        for (int i = 0; i < students.size(); i++) {
            StudentEntity student = students.get(i);
            boolean sameLevelAndSubject = hasSameLevelAndSubjectGroup.get(i);

            AttendanceEntity persisted = attendanceRepository
                    .findByStudentIdAndSessionSeriesIdAndActiveTrue(student.getId(), series.getId())
                    .stream().findFirst().orElseThrow();

            // (1) Aucun rattrapage n'est enregistré sans état : c'est l'invariant qui remplace le
            //     classement silencieux d'avant.
            assertThat(persisted.getCatchUpBillingState())
                    .as("tout rattrapage enregistré doit porter un état de facturation")
                    .isNotNull();

            // (2) L'état découle du seul test niveau+matière.
            assertThat(persisted.getCatchUpBillingState())
                    .as("groupe de même niveau et même matière = %s", sameLevelAndSubject)
                    .isEqualTo(sameLevelAndSubject
                            ? CatchUpBillingState.PENDING
                            : CatchUpBillingState.HOST_BILLED);

            // (3) Une séance manquée absente n'est jamais silencieuse : elle est soit annoncée
            //     comme telle (HOST_BILLED — aucune séance à rattraper), soit revendiquée comme
            //     manquante (PENDING — à préciser). Un état RESOLVED sans lien serait la
            //     régression à empêcher, et c'est la tâche 5 qui l'interdira à la résolution.
            //
            //     La bi-implication « lien nul ⟺ HOST_BILLED » ne vaut donc qu'après résolution :
            //     à l'enregistrement, PENDING est précisément l'état « lien pas encore fourni ».
            if (persisted.getMissedSession() == null) {
                assertThat(persisted.getCatchUpBillingState())
                        .as("une séance manquée absente doit être soit assumée (facturée sur place), "
                                + "soit revendiquée comme à préciser — jamais tue")
                        .isIn(CatchUpBillingState.HOST_BILLED, CatchUpBillingState.PENDING);
            }

            // (4) Aucune décision « déjà payée » ne s'installe d'elle-même, quel que soit l'état :
            //     c'est ce qui garantit que le choix restera explicite.
            assertThat(persisted.getMissedSessionAlreadyPaid())
                    .as("la décision « déjà payée » doit rester entière à l'enregistrement")
                    .isNull();
        }
    }

    private void resetDatabase() {
        attendanceRepository.deleteAll();
        studentGroupRepository.deleteAll();
        sessionRepository.deleteAll();
        sessionSeriesRepository.deleteAll();
        groupRepository.deleteAll();
        studentRepository.deleteAll();
        levelRepository.deleteAll();
        subjectRepository.deleteAll();
        schoolYearRepository.deleteAll();
    }

    private static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Profils d'étudiants d'une même soumission : chaque booléen dit si l'étudiant possède un
     * groupe de même niveau et même matière. La liste est non vide et mêle les deux cas, afin que
     * le classement soit vérifié <strong>par étudiant</strong> et non globalement.
     */
    @Provide
    Arbitrary<List<Boolean>> studentProfiles() {
        return Arbitraries.of(true, false).list().ofMinSize(1).ofMaxSize(6);
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
    static class BulkRoutingTestContext {

        @Bean
        CatchUpRoutingService catchUpRoutingService(StudentGroupRepository studentGroupRepository,
                                                   GroupRepository groupRepository) {
            return new CatchUpRoutingService(studentGroupRepository, groupRepository);
        }

        /**
         * Le mapper n'est pas utilisé par le chemin sous test ({@code saveAll} reçoit des entités),
         * mais le constructeur du service l'exige. Un mandataire nul suffit et évite de tirer
         * MapStruct dans ce contexte réduit.
         */
        @Bean
        AttendanceService attendanceService(AttendanceRepository attendanceRepository,
                                            StudentRepository studentRepository,
                                            SessionRepository sessionRepository,
                                            SessionSeriesRepository sessionSeriesRepository,
                                            GroupRepository groupRepository,
                                            StudentGroupRepository studentGroupRepository,
                                            CatchUpRoutingService catchUpRoutingService) {
            // Le garde de fenêtre est le vrai : les lignes soumises sont des présences, qu'il admet.
            // Le garde d'année est simulé : la propriété porte sur le classement des rattrapages.
            return new AttendanceService(attendanceRepository, (AttendanceMapper) null,
                    studentRepository, sessionRepository, sessionSeriesRepository, groupRepository,
                    studentGroupRepository, catchUpRoutingService,
                    new com.school.management.service.session.AbsenceWindowGuard(studentGroupRepository),
                    org.mockito.Mockito.mock(ReadOnlyYearGuard.class),
                    org.mockito.Mockito.mock(SessionStartGuard.class));
        }
    }
}
