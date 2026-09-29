package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.DiscountService;
import com.school.management.service.StudentPaymentStatus;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La justification d'une absence est documentaire : elle n'a aucun effet financier.
 *
 * <p>Règle tranchée par le propriétaire produit ({@code .kiro/steering/business-rules.md},
 * section « La justification d'absence est documentaire »). Cette classe en est la propriété
 * exécutable annoncée par ce document : faire varier arbitrairement la justification des absences
 * d'une série laisse identiques le coût au prorata, le montant dû à ce jour, le plafond
 * encaissable et le statut de paiement.</p>
 *
 * <p><b>Pourquoi une propriété métamorphique et non un test d'interaction.</b> Le seul garde-fou
 * existant ({@code AttendanceJustificationServiceTest}) vérifie que le service de justification ne
 * reçoit aucun composant de calcul. C'est nécessaire mais pas suffisant : l'effet financier, s'il
 * apparaissait, viendrait du côté du calcul — un filtre {@code isJustified} glissé dans une
 * requête de décompte — et ce test-là ne le verrait pas. On compare donc ici les sorties réelles
 * du calcul, sur une base réelle, pour deux états qui ne diffèrent que par la justification.</p>
 *
 * <p><b>Pourquoi un oracle en plus de la comparaison.</b> Une comparaison seule serait satisfaite
 * par un calcul constamment faux. Chaque essai vérifie donc aussi les deux règles énoncées à
 * l'écran sur la ligne de chaque absence :</p>
 * <ul>
 *   <li>aucune absence n'augmente le montant dû à ce jour, justifiée ou non ;</li>
 *   <li>toute absence postérieure à l'inscription reste comptée dans le coût de la série.</li>
 * </ul>
 *
 * <p>jqwik s'exécute sur son propre moteur JUnit Platform : les tranches Spring ne s'appliquent
 * pas aux méthodes {@code @Property}. Un contexte ciblé est amorcé une fois par conteneur sur une
 * base H2 en mémoire, vidée à chaque essai — même harnais que
 * {@code CatchUpBillingResolutionPropertyTest}.</p>
 */
class JustificationNeutralityPropertyTest {

    private static final double PRICE_PER_SESSION = 2000.0;

    private static ConfigurableApplicationContext context;
    private static PaymentCostResolver costResolver;
    private static PaymentQuoteService quoteService;
    private static PaymentStatusService statusService;
    private static AttendanceRepository attendanceRepository;
    private static PaymentRepository paymentRepository;
    private static SessionRepository sessionRepository;
    private static SessionSeriesRepository seriesRepository;
    private static StudentGroupRepository studentGroupRepository;
    private static GroupRepository groupRepository;
    private static StudentRepository studentRepository;
    private static PricingRepository pricingRepository;

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(NeutralityTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:justification-neutrality;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off");

        costResolver = context.getBean(PaymentCostResolver.class);
        quoteService = context.getBean(PaymentQuoteService.class);
        statusService = context.getBean(PaymentStatusService.class);
        attendanceRepository = context.getBean(AttendanceRepository.class);
        paymentRepository = context.getBean(PaymentRepository.class);
        sessionRepository = context.getBean(SessionRepository.class);
        seriesRepository = context.getBean(SessionSeriesRepository.class);
        studentGroupRepository = context.getBean(StudentGroupRepository.class);
        groupRepository = context.getBean(GroupRepository.class);
        studentRepository = context.getBean(StudentRepository.class);
        pricingRepository = context.getBean(PricingRepository.class);
    }

    @AfterContainer
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    // ------------------------------------------------------------------
    // Scénario généré
    // ------------------------------------------------------------------

    /** Ce que l'étudiant a fait à une séance donnée. */
    enum Mark { PRESENT, ABSENT, NO_RECORD }

    /**
     * Un essai : une série de séances, la séance à partir de laquelle l'étudiant est inscrit, ce
     * qu'il a fait à chaque séance, deux jeux de justification arbitraires et le montant versé.
     *
     * @param marks         une marque par séance, dans l'ordre chronologique
     * @param enrolledFrom  indice de la première séance postérieure ou égale à l'inscription ; les
     *                      séances d'indice inférieur sont antérieures à l'arrivée de l'étudiant
     * @param justifiedA    justification de chaque séance dans le premier état
     * @param justifiedB    justification de chaque séance dans le second état, indépendante de A
     * @param paidSessions  montant versé, exprimé en nombre de séances (peut dépasser le coût)
     */
    record Scenario(List<Mark> marks, int enrolledFrom, List<Boolean> justifiedA,
                    List<Boolean> justifiedB, int paidSessions) {

        int size() {
            return marks.size();
        }

        /**
         * Séance facturable : postérieure ou égale à l'inscription, ou couverte par une
         * Présence_Active.
         *
         * <p>Une Présence_Active est <em>tout</em> enregistrement d'assiduité non supprimé, absence
         * comprise (spec prorata, glossaire et exigence 1.2). Une absence saisie avant
         * l'inscription rend donc la séance facturable — comportement déjà épinglé par
         * {@code BillableSessionsResolverTest.absenceMakesSessionBillableWithoutRaisingAttendedCount}.
         * Seule une séance antérieure SANS aucun enregistrement est écartée (exigence 1.3).</p>
         */
        boolean billable(int index) {
            return index >= enrolledFrom || marks.get(index) != Mark.NO_RECORD;
        }

        long billableCount() {
            return java.util.stream.IntStream.range(0, size()).filter(this::billable).count();
        }

        long presentCount() {
            return marks.stream().filter(mark -> mark == Mark.PRESENT).count();
        }

        long absencesAfterEnrolment() {
            return java.util.stream.IntStream.range(enrolledFrom, size())
                    .filter(i -> marks.get(i) == Mark.ABSENT).count();
        }

        long absencesCount() {
            return marks.stream().filter(mark -> mark == Mark.ABSENT).count();
        }
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        return Arbitraries.integers().between(1, 8).flatMap(size -> {
            Arbitrary<List<Mark>> marks = Arbitraries.of(Mark.class).list().ofSize(size);
            Arbitrary<Integer> enrolledFrom = Arbitraries.integers().between(0, size - 1);
            Arbitrary<List<Boolean>> justifiedA = Arbitraries.of(true, false).list().ofSize(size);
            Arbitrary<List<Boolean>> justifiedB = Arbitraries.of(true, false).list().ofSize(size);
            Arbitrary<Integer> paid = Arbitraries.integers().between(0, size + 2);
            return Combinators.combine(marks, enrolledFrom, justifiedA, justifiedB, paid)
                    .as(Scenario::new);
        });
    }

    // ------------------------------------------------------------------
    // Propriété
    // ------------------------------------------------------------------

    // Feature: absence-justification-and-refund-receipts, Property: la justification d'une absence
    // n'a aucun effet financier (business-rules.md, « La justification d'absence est documentaire »)
    @Property(tries = 100)
    void justificationHasNoFinancialEffect(@ForAll("scenarios") Scenario scenario) {

        Fixture f = persist(scenario);

        applyJustification(f, scenario.justifiedA());
        Snapshot first = snapshot(f);

        applyJustification(f, scenario.justifiedB());
        Snapshot second = snapshot(f);

        // (1) Neutralité : deux états qui ne diffèrent que par la justification des absences
        //     produisent exactement les mêmes montants et le même statut.
        assertThat(second)
                .as("justification A=%s puis B=%s sur les marques %s",
                        scenario.justifiedA(), scenario.justifiedB(), scenario.marks())
                .isEqualTo(first);

        // (2) Oracle : les valeurs communes sont les bonnes, et non seulement identiques.
        BigDecimal price = money(PRICE_PER_SESSION);
        BigDecimal expectedCost = price.multiply(BigDecimal.valueOf(scenario.billableCount()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedDue = price.multiply(BigDecimal.valueOf(scenario.presentCount()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = price.multiply(BigDecimal.valueOf(scenario.paidSessions()))
                .setScale(2, RoundingMode.HALF_UP);

        // « Toute absence postérieure à l'inscription reste comptée dans le coût de la série » :
        // la place était réservée. Le coût vaut séances facturables × prix, absences comprises.
        assertThat(first.monthTotalCost())
                .as("coût au prorata : %d séance(s) facturable(s), dont %d absence(s) après inscription",
                        scenario.billableCount(), scenario.absencesAfterEnrolment())
                .isEqualByComparingTo(expectedCost);

        // « Aucune absence n'augmente le montant dû à ce jour » : seules les présences comptent.
        assertThat(first.amountDueSoFar())
                .as("montant dû à ce jour : %d présence(s)", scenario.presentCount())
                .isEqualByComparingTo(expectedDue);

        // Donc une absence ne met jamais en retard : le retard ne dépend que des présences.
        assertThat(first.late()).isEqualTo(paid.compareTo(expectedDue) < 0);
        assertThat(first.groupListingOverdue()).isEqualTo(first.late());
    }

    // ------------------------------------------------------------------
    // Lecture des quatre sorties
    // ------------------------------------------------------------------

    /**
     * Tout ce que la justification ne doit pas toucher, lu par les trois chemins de calcul
     * indépendants : le résolveur de coût, le devis, et le statut présenté dans la liste du groupe.
     */
    record Snapshot(BigDecimal monthTotalCost,
                    BigDecimal amountDueSoFar,
                    boolean late,
                    boolean monthFullyPaid,
                    BigDecimal maxPayable,
                    BigDecimal remainingToPay,
                    int billableSessions,
                    int excludedSessions,
                    int attendedSessions,
                    boolean groupListingOverdue) {

        /** Égalité monétaire par valeur : 4000 et 4000.00 sont le même montant. */
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Snapshot s)) {
                return false;
            }
            return monthTotalCost.compareTo(s.monthTotalCost) == 0
                    && amountDueSoFar.compareTo(s.amountDueSoFar) == 0
                    && late == s.late
                    && monthFullyPaid == s.monthFullyPaid
                    && maxPayable.compareTo(s.maxPayable) == 0
                    && remainingToPay.compareTo(s.remainingToPay) == 0
                    && billableSessions == s.billableSessions
                    && excludedSessions == s.excludedSessions
                    && attendedSessions == s.attendedSessions
                    && groupListingOverdue == s.groupListingOverdue;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(late, monthFullyPaid, billableSessions, excludedSessions,
                    attendedSessions, groupListingOverdue);
        }
    }

    private Snapshot snapshot(Fixture f) {
        PaymentCostResolver.PaymentStatusResult status = costResolver.resolve(f.studentId, f.seriesId);
        PaymentQuoteDTO quote = quoteService.quote(f.studentId, f.seriesId);
        boolean listingOverdue = statusService.getPaymentStatusForGroup(f.groupId).stream()
                .filter(s -> f.studentId.equals(s.getId()))
                .findFirst()
                .map(StudentPaymentStatus::isPaymentOverdue)
                .orElseThrow(() -> new AssertionError("étudiant absent de la liste du groupe"));

        return new Snapshot(status.monthTotalCost(), status.amountDueSoFar(), status.late(),
                status.monthFullyPaid(), quote.maxPayable(), quote.remainingToPay(),
                quote.billableSessions(), quote.excludedSessions(), quote.attendedSessions(),
                listingOverdue);
    }

    // ------------------------------------------------------------------
    // Socle de données
    // ------------------------------------------------------------------

    private record Fixture(Long studentId, Long groupId, Long seriesId, List<Long> absenceIds,
                           List<Integer> absenceIndexes) {
    }

    /** Écrit la justification de chaque absence ; les présences n'en portent pas. */
    private void applyJustification(Fixture f, List<Boolean> justified) {
        for (int i = 0; i < f.absenceIds.size(); i++) {
            AttendanceEntity absence = attendanceRepository.findById(f.absenceIds.get(i)).orElseThrow();
            absence.setIsJustified(justified.get(f.absenceIndexes.get(i)));
            attendanceRepository.save(absence);
        }
    }

    private Fixture persist(Scenario scenario) {
        resetDatabase();

        PricingEntity pricing = pricingRepository.save(PricingEntity.builder().price(PRICE_PER_SESSION).build());
        GroupEntity group = groupRepository.save(GroupEntity.builder()
                .name("Math 1ère A").price(pricing).sessionNumberPerSerie(scenario.size()).build());
        StudentEntity student = studentRepository.save(StudentEntity.builder()
                .firstName("Amine").lastName("Belkacem").build());
        SessionSeriesEntity series = seriesRepository.save(SessionSeriesEntity.builder()
                .name("Série 1").group(group).totalSessions(scenario.size())
                .serieTimeStart(sessionDate(0)).build());

        List<SessionEntity> sessions = new ArrayList<>();
        for (int i = 0; i < scenario.size(); i++) {
            sessions.add(sessionRepository.save(SessionEntity.builder()
                    .title("S" + (i + 1)).group(group).sessionSeries(series)
                    .sessionTimeStart(sessionDate(i)).build()));
        }

        // StudentGroupEntity.onCreate écrase la date d'inscription par l'heure courante lors de la
        // persistance : la date choisie est donc posée par une mise à jour. Le jour même de la
        // séance « enrolledFrom » rend cette séance facturable (postérieure ou égale) et les
        // précédentes antérieures.
        StudentGroupEntity enrolment = studentGroupRepository.save(StudentGroupEntity.builder()
                .student(student).group(group).build());
        enrolment.setDateAssigned(sessionDate(scenario.enrolledFrom()));
        studentGroupRepository.save(enrolment);

        List<Long> absenceIds = new ArrayList<>();
        List<Integer> absenceIndexes = new ArrayList<>();
        for (int i = 0; i < scenario.size(); i++) {
            Mark mark = scenario.marks().get(i);
            if (mark == Mark.NO_RECORD) {
                continue;
            }
            AttendanceEntity attendance = AttendanceEntity.builder()
                    .student(student).session(sessions.get(i)).sessionSeries(series).group(group)
                    .isPresent(mark == Mark.PRESENT).isJustified(false)
                    .build();
            attendance.setActive(true);
            AttendanceEntity saved = attendanceRepository.save(attendance);
            if (mark == Mark.ABSENT) {
                absenceIds.add(saved.getId());
                absenceIndexes.add(i);
            }
        }

        if (scenario.paidSessions() > 0) {
            PaymentEntity payment = PaymentEntity.builder()
                    .student(student).group(group).sessionSeries(series)
                    .amountPaid(PRICE_PER_SESSION * scenario.paidSessions())
                    .status("IN_PROGRESS").paymentDate(sessionDate(0))
                    .build();
            payment.setActive(true);
            paymentRepository.save(payment);
        }

        return new Fixture(student.getId(), group.getId(), series.getId(), absenceIds, absenceIndexes);
    }

    private void resetDatabase() {
        paymentRepository.deleteAll();
        attendanceRepository.deleteAll();
        sessionRepository.deleteAll();
        seriesRepository.deleteAll();
        studentGroupRepository.deleteAll();
        groupRepository.deleteAll();
        studentRepository.deleteAll();
        pricingRepository.deleteAll();
    }

    /** Séances hebdomadaires en 2030, loin de l'horloge réelle : aucune ne dépend du jour du test. */
    private static Date sessionDate(int index) {
        return Date.from(LocalDate.of(2030, 1, 7).plusWeeks(index)
                .atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static BigDecimal money(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Contexte
    // ------------------------------------------------------------------

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @EntityScan("com.school.management.persistance")
    @EnableJpaRepositories("com.school.management.repository")
    @Import({ BillableSessionsResolverImpl.class, CatchUpBillingQualifierImpl.class, DiscountService.class,
            PaymentCostResolver.class, PaymentQuoteService.class, PaymentStatusService.class })
    static class NeutralityTestContext {

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("admin-test");
        }
    }
}
