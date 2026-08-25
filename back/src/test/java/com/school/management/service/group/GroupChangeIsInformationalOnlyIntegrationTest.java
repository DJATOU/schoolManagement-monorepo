package com.school.management.service.group;

import com.school.management.dto.group.GroupChangeDTO;
import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.service.DiscountService;
import com.school.management.service.group.GroupChangeDetector.GroupChange;
import com.school.management.service.payment.BillableSessionsResolverImpl;
import com.school.management.service.payment.PaymentAllocationResult;
import com.school.management.service.payment.PaymentAllocationService;
import com.school.management.service.payment.PaymentCarryOverService;
import com.school.management.service.payment.PaymentCostResolver;
import com.school.management.service.payment.PaymentDistributionService;
import com.school.management.service.payment.PaymentProcessingService;
import com.school.management.service.payment.PaymentQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le signalement de changement de groupe est <strong>purement informatif</strong> : il n'altère
 * aucun montant et ne bloque aucun encaissement (exigences 10.6, 10.7).
 *
 * <h2>Ce que ce test cherche à empêcher</h2>
 * Le signalement existe parce que l'agrégation automatique entre groupes a été abandonnée. La
 * tentation, une fois le cas détecté, serait d'en tirer une conséquence : ajuster un coût,
 * subordonner un versement à la lecture de l'alerte. Ce serait réintroduire par la porte de service
 * la seconde granularité que la décision d'unité de facturation a écartée. La facturation reste
 * calculée <strong>série par série</strong>, indépendamment du signalement.
 *
 * <h2>La démonstration</h2>
 * Deux étudiants du même groupe, arrivés le même jour, dus au même montant. L'un a quitté un autre
 * groupe ce mois-ci — il est signalé — l'autre non. Leurs devis doivent être identiques au dinar
 * près, et le versement de l'étudiant signalé doit passer normalement.
 *
 * <p>Un contrôle structurel complète la démonstration : aucun champ de
 * {@link PaymentProcessingService} n'est de type {@link GroupChangeDetector}. Le détecteur reste
 * hors du chemin d'encaissement, où il n'ajouterait que de la latence et un risque de blocage.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({ GroupChangeDetector.class, BillableSessionsResolverImpl.class, DiscountService.class,
        PaymentCostResolver.class, PaymentQuoteService.class, PaymentAllocationService.class,
        PaymentDistributionService.class, PaymentCarryOverService.class,
        PaymentProcessingService.class })
class GroupChangeIsInformationalOnlyIntegrationTest {

    private static final double PRICE_PER_SESSION = 2000.0;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private GroupChangeDetector groupChangeDetector;

    @Autowired
    private PaymentQuoteService paymentQuoteService;

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    /** Mois courant : les deux événements d'inscription sont horodatés par JPA. */
    private final YearMonth currentMonth = YearMonth.now();

    private GroupEntity maths;
    private GroupEntity physique;
    private SessionSeriesEntity physiqueSeries;

    /** Étudiant ayant quitté les maths pour la physique ce mois-ci : signalé. */
    private StudentEntity nour;

    /** Étudiant arrivé en physique le même jour, sans rien quitter : non signalé. */
    private StudentEntity karim;

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        PricingEntity pricing = em.persist(PricingEntity.builder().price(PRICE_PER_SESSION).build());

        maths = em.persist(GroupEntity.builder()
                .name("Maths 1B").price(pricing).sessionNumberPerSerie(2).build());
        physique = em.persist(GroupEntity.builder()
                .name("Physique 1B").price(pricing).sessionNumberPerSerie(2).build());

        LocalDate today = LocalDate.now();
        physiqueSeries = em.persist(SessionSeriesEntity.builder()
                .name("Physique - série 1")
                .group(physique)
                .totalSessions(2)
                .serieTimeStart(toDate(today.plusDays(7)))
                .build());
        // Séances postérieures à l'inscription : les deux étudiants doivent 2 × 2 000 = 4 000 DA.
        persistSession(physiqueSeries, "P1", today.plusDays(7));
        persistSession(physiqueSeries, "P2", today.plusDays(14));

        nour = em.persist(StudentEntity.builder().firstName("Nour").lastName("Belkacem").build());
        karim = em.persist(StudentEntity.builder().firstName("Karim").lastName("Saïdi").build());

        // Nour : inscription en maths clôturée ce mois-ci, inscription en physique ouverte le même
        // mois. C'est exactement le changement de groupe que l'exigence 10.1 décrit.
        StudentGroupEntity mathsEnrolment = enrol(nour, maths);
        mathsEnrolment.setActive(false);
        em.flush();
        enrol(nour, physique);

        // Karim : une seule inscription, aucune clôture. Cas normal, aucun signalement.
        enrol(karim, physique);

        // Deux séances suivies en maths sur le mois, pour que le signalement porte un décompte.
        persistPresence(maths, currentMonth.atDay(1));
        persistPresence(maths, currentMonth.atDay(1));
        em.flush();
    }

    private StudentGroupEntity enrol(StudentEntity student, GroupEntity group) {
        StudentGroupEntity enrolment = em.persist(StudentGroupEntity.builder()
                .student(student)
                .group(group)
                .build());
        em.flush();
        return enrolment;
    }

    private void persistSession(SessionSeriesEntity series, String title, LocalDate day) {
        em.persist(SessionEntity.builder()
                .title(title)
                .group(series.getGroup())
                .sessionSeries(series)
                .sessionTimeStart(toDate(day))
                .build());
    }

    /** Une présence de Nour dans un groupe, sur une séance datée du mois courant. */
    private void persistPresence(GroupEntity group, LocalDate day) {
        SessionSeriesEntity series = em.persist(SessionSeriesEntity.builder()
                .name("Série " + group.getName())
                .group(group)
                .totalSessions(2)
                .serieTimeStart(toDate(day))
                .build());
        SessionEntity session = em.persist(SessionEntity.builder()
                .title("Séance " + day)
                .group(group)
                .sessionSeries(series)
                .sessionTimeStart(toDate(day))
                .build());
        em.persist(AttendanceEntity.builder()
                .student(nour)
                .session(session)
                .sessionSeries(series)
                .group(group)
                .isPresent(true)
                .build());
    }

    private static Date toDate(LocalDate day) {
        return Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    // ------------------------------------------------------------------
    // Le signalement est bien émis
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Le changement est signalé pour Nour, et pas pour Karim qui n'a rien quitté")
    void theChangeIsFlaggedForTheStudentWhoActuallyChangedGroup() {
        List<GroupChange> changes = groupChangeDetector.detect(nour.getId());

        assertThat(changes).hasSize(1);
        GroupChangeDTO flagged = GroupChangeDTO.from(changes.get(0));
        assertThat(flagged.year()).isEqualTo(currentMonth.getYear());
        assertThat(flagged.month()).isEqualTo(currentMonth.getMonthValue());
        assertThat(flagged.leftGroup().groupName()).isEqualTo("Maths 1B");
        assertThat(flagged.leftGroup().attendedCount()).isEqualTo(2);
        assertThat(flagged.joinedGroup().groupName()).isEqualTo("Physique 1B");
        assertThat(flagged.joinedGroup().attendedCount()).isZero();

        assertThat(groupChangeDetector.detect(karim.getId()))
                .as("inscription unique, aucune clôture : le cas normal ne s'annonce pas")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // Exigence 10.6 : aucun montant altéré
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Aucun montant altéré : le devis de l'étudiant signalé est identique à celui de "
            + "l'étudiant non signalé (exigence 10.6)")
    void theFlagLeavesEveryAmountUntouched() {
        PaymentQuoteDTO flagged = paymentQuoteService.quote(nour.getId(), physiqueSeries.getId());
        PaymentQuoteDTO control = paymentQuoteService.quote(karim.getId(), physiqueSeries.getId());

        // Le coût reste calculé série par série, sans agrégation avec le groupe quitté.
        assertThat(flagged.billableSessions()).isEqualTo(control.billableSessions()).isEqualTo(2);
        assertThat(flagged.excludedSessions()).isEqualTo(control.excludedSessions()).isZero();
        assertThat(flagged.monthTotalCost())
                .isEqualByComparingTo(control.monthTotalCost())
                .isEqualByComparingTo("4000.00");
        assertThat(flagged.amountDueSoFar()).isEqualByComparingTo(control.amountDueSoFar());
        assertThat(flagged.maxPayable())
                .isEqualByComparingTo(control.maxPayable())
                .isEqualByComparingTo("4000.00");
        assertThat(flagged.existingExcess()).isEqualByComparingTo(control.existingExcess());
        assertThat(flagged.discountRate()).isEqualByComparingTo(control.discountRate());

        // Détecter ne change rien : le devis relu après lecture du signalement est le même.
        groupChangeDetector.detect(nour.getId());
        assertThat(paymentQuoteService.quote(nour.getId(), physiqueSeries.getId()).monthTotalCost())
                .isEqualByComparingTo("4000.00");
    }

    // ------------------------------------------------------------------
    // Exigence 10.7 : aucun versement bloqué
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Aucun versement bloqué : l'étudiant signalé encaisse normalement, et le "
            + "signalement subsiste après le versement (exigence 10.7)")
    void theFlagNeverBlocksAPayment() {
        PaymentAllocationResult result = paymentProcessingService.processPayment(
                nour.getId(), physique.getId(), physiqueSeries.getId(), 4000.0);

        assertThat(result.amountAllocated()).isEqualByComparingTo("4000.00");
        assertThat(result.amountCarriedOver()).isEqualByComparingTo("0.00");
        assertThat(result.carryOvers()).isEmpty();
        assertThat(result.payment().getStatus())
                .as("série soldée contre son coût au prorata")
                .isEqualTo("COMPLETED");

        // Le signalement n'est ni consommé ni éteint par l'encaissement : l'ajustement
        // administratif reste à faire, et l'alerte doit rester visible pour le rappeler.
        assertThat(groupChangeDetector.detect(nour.getId())).hasSize(1);
        assertThat(paymentQuoteService.quote(nour.getId(), physiqueSeries.getId()).maxPayable())
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Le détecteur reste hors du chemin d'encaissement : "
            + "PaymentProcessingService n'en dépend pas")
    void theDetectorStaysOutOfThePaymentPath() {
        assertThat(Arrays.stream(PaymentProcessingService.class.getDeclaredFields())
                .map(Field::getType))
                .as("un détecteur appelé pendant l'encaissement pourrait le ralentir ou le bloquer")
                .doesNotContain(GroupChangeDetector.class);
    }
}
