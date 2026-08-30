package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.GroupTypeEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.persistance.SubjectEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.GroupTypeRepository;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.SubjectRepository;
import com.school.management.service.CatchUpRoutingService.RoutingVerdict;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) du routage des rattrapages, en intégration H2 réelle.
 *
 * <p>Feature: catch-up-billing-routing, Property 1: Le routage ne dépend que du niveau, de la
 * matière et de l'année scolaire.</p>
 *
 * <p>Ce qui est vérifié n'est pas seulement que le test répond juste, mais qu'il répond
 * <strong>pour la bonne raison</strong> : le verdict doit varier avec le niveau, la matière et
 * l'année scolaire, et rester <strong>insensible</strong> au type de groupe (l'effectif), au prix
 * par séance et à l'état actif ou clôturé de l'inscription. C'est précisément la confusion que ce
 * test doit rendre impossible : {@code group_type} désigne l'effectif et n'a rien à voir avec la
 * paire niveau + matière ; router sur lui facturerait au mauvais groupe.</p>
 *
 * <p>jqwik s'exécute sur son propre moteur JUnit Platform : les tranches Spring
 * ({@code @DataJpaTest}) ne s'appliquent pas aux méthodes {@code @Property}. Un contexte Spring
 * ciblé est donc amorcé une fois par conteneur sur une base H2 en mémoire, et la base est vidée à
 * chaque essai.</p>
 *
 * <p><b>Validates: test déterminant niveau+matière, bornage à l'année scolaire, prise en compte
 * des inscriptions clôturées, indépendance au type de groupe et au prix</b></p>
 */
class CatchUpRoutingPropertyTest {

    private static ConfigurableApplicationContext context;
    private static CatchUpRoutingService routingService;
    private static StudentGroupRepository studentGroupRepository;
    private static GroupRepository groupRepository;
    private static StudentRepository studentRepository;
    private static LevelRepository levelRepository;
    private static SubjectRepository subjectRepository;
    private static SchoolYearRepository schoolYearRepository;
    private static GroupTypeRepository groupTypeRepository;
    private static PricingRepository pricingRepository;

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(CatchUpRoutingTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:catchup-routing-pbt;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off");

        routingService = context.getBean(CatchUpRoutingService.class);
        studentGroupRepository = context.getBean(StudentGroupRepository.class);
        groupRepository = context.getBean(GroupRepository.class);
        studentRepository = context.getBean(StudentRepository.class);
        levelRepository = context.getBean(LevelRepository.class);
        subjectRepository = context.getBean(SubjectRepository.class);
        schoolYearRepository = context.getBean(SchoolYearRepository.class);
        groupTypeRepository = context.getBean(GroupTypeRepository.class);
        pricingRepository = context.getBean(PricingRepository.class);
    }

    @AfterContainer
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    // Feature: catch-up-billing-routing, Property 1: Le routage ne dépend que du niveau, de la matière et de l'année scolaire
    @Property(tries = 100)
    void property1_routingDependsOnlyOnLevelSubjectAndYear(
            @ForAll boolean sameLevel,
            @ForAll boolean sameSubject,
            @ForAll boolean sameYear,
            @ForAll boolean sameGroupType,
            @ForAll boolean samePrice,
            @ForAll boolean enrolmentActive) {

        resetDatabase();

        // --- Arrange : référentiel minimal, deux valeurs pour chaque dimension ---
        LevelEntity levelA = levelRepository.save(LevelEntity.builder().name("Niveau A").build());
        LevelEntity levelB = levelRepository.save(LevelEntity.builder().name("Niveau B").build());
        SubjectEntity subjectA = subjectRepository.save(SubjectEntity.builder().name("Matière A").build());
        SubjectEntity subjectB = subjectRepository.save(SubjectEntity.builder().name("Matière B").build());
        SchoolYearEntity yearA = schoolYearRepository.save(schoolYear("2000-2001", 2000, true));
        SchoolYearEntity yearB = schoolYearRepository.save(schoolYear("2001-2002", 2001, false));
        GroupTypeEntity typeA = groupTypeRepository.save(GroupTypeEntity.builder().name("Petit").build());
        GroupTypeEntity typeB = groupTypeRepository.save(GroupTypeEntity.builder().name("Grand").build());
        PricingEntity priceA = pricingRepository.save(PricingEntity.builder().price(2000.0).build());
        PricingEntity priceB = pricingRepository.save(PricingEntity.builder().price(5000.0).build());

        // Groupe d'accueil : la séance suivie s'y déroule.
        GroupEntity hostGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe d'accueil")
                .level(levelA).subject(subjectA).schoolYear(yearA)
                .groupType(typeA).price(priceA)
                .build());

        // Groupe d'origine : celui où l'étudiant est inscrit. Chaque dimension varie
        // indépendamment, y compris celles qui ne doivent avoir aucun effet.
        GroupEntity originGroup = groupRepository.save(GroupEntity.builder()
                .name("Groupe d'origine")
                .level(sameLevel ? levelA : levelB)
                .subject(sameSubject ? subjectA : subjectB)
                .schoolYear(sameYear ? yearA : yearB)
                .groupType(sameGroupType ? typeA : typeB)
                .price(samePrice ? priceA : priceB)
                .build());

        StudentEntity student = studentRepository.save(StudentEntity.builder()
                .firstName("Étudiant").lastName("Test").build());

        StudentGroupEntity enrolment = StudentGroupEntity.builder()
                .student(student).group(originGroup).build();
        enrolment.setActive(enrolmentActive);
        studentGroupRepository.save(enrolment);

        // --- Act ---
        RoutingVerdict verdict = routingService.route(student.getId(), hostGroup.getId());

        // --- Assert ---
        // (1) Le verdict est TRUE_CATCH_UP exactement quand niveau, matière ET année concordent.
        boolean expectedTrueCatchUp = sameLevel && sameSubject && sameYear;
        assertThat(verdict)
                .as("niveau identique=%s, matière identique=%s, année identique=%s "
                                + "→ le verdict doit être %s (type identique=%s, prix identique=%s, "
                                + "inscription active=%s ne doivent rien changer)",
                        sameLevel, sameSubject, sameYear,
                        expectedTrueCatchUp ? "TRUE_CATCH_UP" : "HOST_BILLED",
                        sameGroupType, samePrice, enrolmentActive)
                .isEqualTo(expectedTrueCatchUp ? RoutingVerdict.TRUE_CATCH_UP : RoutingVerdict.HOST_BILLED);

        // (2) Une inscription clôturée compte autant qu'une inscription active : un étudiant
        //     ayant quitté le groupe reste débiteur, et la lui refuser requalifierait son
        //     rattrapage en séance facturable sur place — donc une double facturation.
        enrolment.setActive(!enrolmentActive);
        studentGroupRepository.save(enrolment);
        assertThat(routingService.route(student.getId(), hostGroup.getId()))
                .as("l'état actif/clôturé de l'inscription ne doit pas changer le verdict")
                .isEqualTo(verdict);

        // (3) Un étudiant sans aucune inscription est toujours facturé sur place : aucune place
        //     ne lui est réservée nulle part.
        StudentEntity orphan = studentRepository.save(StudentEntity.builder()
                .firstName("Sans").lastName("Groupe").build());
        assertThat(routingService.route(orphan.getId(), hostGroup.getId()))
                .as("un étudiant sans inscription est facturé sur place")
                .isEqualTo(RoutingVerdict.HOST_BILLED);

        // (4) Un membre du groupe d'accueil n'est jamais en rattrapage chez lui : le groupe
        //     d'accueil est exclu de la recherche, indépendamment de tout autre contrôle.
        StudentEntity member = studentRepository.save(StudentEntity.builder()
                .firstName("Membre").lastName("Accueil").build());
        studentGroupRepository.save(StudentGroupEntity.builder()
                .student(member).group(hostGroup).build());
        assertThat(routingService.route(member.getId(), hostGroup.getId()))
                .as("le groupe d'accueil ne peut pas fonder son propre rattrapage")
                .isEqualTo(RoutingVerdict.HOST_BILLED);
    }

    private void resetDatabase() {
        studentGroupRepository.deleteAll();
        groupRepository.deleteAll();
        studentRepository.deleteAll();
        levelRepository.deleteAll();
        subjectRepository.deleteAll();
        groupTypeRepository.deleteAll();
        pricingRepository.deleteAll();
        schoolYearRepository.deleteAll();
    }

    private static SchoolYearEntity schoolYear(String label, int startYear, boolean current) {
        return SchoolYearEntity.builder()
                .label(label)
                .startDate(date(startYear, 9, 1))
                .endDate(date(startYear + 1, 6, 30))
                .isCurrent(current)
                .build();
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
    static class CatchUpRoutingTestContext {

        /**
         * Le service sous test est instancié à la main : le contexte n'active pas le balayage de
         * composants du module, pour rester au strict nécessaire (dépôts + JPA).
         */
        @Bean
        CatchUpRoutingService catchUpRoutingService(StudentGroupRepository studentGroupRepository,
                                                   GroupRepository groupRepository) {
            return new CatchUpRoutingService(studentGroupRepository, groupRepository);
        }
    }
}
