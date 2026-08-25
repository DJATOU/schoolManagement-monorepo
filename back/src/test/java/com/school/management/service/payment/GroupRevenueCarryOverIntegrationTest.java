package com.school.management.service.payment;

import com.school.management.dto.payment.StudentPaymentHistoryDTO;
import com.school.management.dto.payment.StudentPaymentHistoryDTO.SeriesPaymentHistoryDTO;
import com.school.management.dto.revenue.GroupRevenueDTO;
import com.school.management.dto.revenue.SeriesRevenueDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentCarryOverEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.service.DiscountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cohérence du relevé de groupe après un report (exigence 8).
 *
 * <p>Le relevé lit l'encaissé <strong>par série</strong> sur le registre des paiements, et non sur
 * la ventilation par séance. Un report crédite le registre de la série destination : il devrait
 * donc tomber du bon côté sans traitement particulier. « Devrait » ne suffit pas — c'est
 * exactement le genre d'hypothèse qui a produit le défaut d'origine, où le devis et la
 * facturation se croyaient d'accord. Ce test le constate sur la chaîne complète, de
 * l'encaissement au relevé.</p>
 *
 * <h2>Le scénario</h2>
 * Un groupe à 2 000 DA la séance, deux séries de deux séances. Ali est inscrit avant toutes les
 * séances, Nadia arrive entre les deux séances de la première série : sa première séance ne lui
 * est donc pas facturable. Ali verse 6 000 DA sur la première série, dont le montant dû n'est que
 * de 4 000 DA : 2 000 DA partent en report sur la seconde.
 *
 * <h2>Pourquoi un test d'intégration et non un test unitaire</h2>
 * Les quatre invariants vérifiés ici portent sur l'accord entre trois sources indépendantes — le
 * registre {@code payments}, la ventilation {@code payment_detail} et le coût au prorata résolu
 * étudiant par étudiant. Les simuler reviendrait à postuler l'accord qu'on cherche à démontrer.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({ BillableSessionsResolverImpl.class, DiscountService.class, PaymentCostResolver.class,
        PaymentQuoteService.class, PaymentAllocationService.class, PaymentDistributionService.class,
        PaymentCarryOverService.class, PaymentProcessingService.class, GroupRevenueService.class,
        PaymentHistoryService.class })
class GroupRevenueCarryOverIntegrationTest {

    private static final double PRICE_PER_SESSION = 2000.0;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    @Autowired
    private GroupRevenueService groupRevenueService;

    @Autowired
    private PaymentCostResolver paymentCostResolver;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentDetailRepository paymentDetailRepository;

    @Autowired
    private PaymentCarryOverRepository paymentCarryOverRepository;

    @Autowired
    private PaymentHistoryService paymentHistoryService;

    private GroupEntity group;
    private SessionSeriesEntity firstSeries;
    private SessionSeriesEntity secondSeries;
    private StudentEntity ali;
    private StudentEntity nadia;

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        PricingEntity pricing = em.persist(PricingEntity.builder()
                .price(PRICE_PER_SESSION)
                .build());

        group = em.persist(GroupEntity.builder()
                .name("Math 1ère B")
                .price(pricing)
                .sessionNumberPerSerie(2)
                .build());

        firstSeries = persistSeries("Novembre 2030 - 1", date(2030, 1, 1));
        secondSeries = persistSeries("Novembre 2030 - 2", date(2030, 2, 1));

        persistSession(firstSeries, "A1", date(2030, 1, 5));
        persistSession(firstSeries, "A2", date(2030, 1, 12));
        persistSession(secondSeries, "B1", date(2030, 2, 2));
        persistSession(secondSeries, "B2", date(2030, 2, 9));

        ali = em.persist(StudentEntity.builder().firstName("Ali").lastName("Bensalem").build());
        nadia = em.persist(StudentEntity.builder().firstName("Nadia").lastName("Merbah").build());

        // Ali est inscrit avant toutes les séances : la date d'inscription forcée par
        // StudentGroupEntity.onCreate (maintenant) précède les séances de 2030.
        enrol(ali, null);
        // Nadia arrive entre les deux séances de la première série : A1 lui est écartée.
        enrol(nadia, date(2030, 1, 10));
    }

    private SessionSeriesEntity persistSeries(String name, Date start) {
        return em.persist(SessionSeriesEntity.builder()
                .name(name)
                .group(group)
                .totalSessions(2)
                .serieTimeStart(start)
                .build());
    }

    private void persistSession(SessionSeriesEntity series, String title, Date start) {
        em.persist(SessionEntity.builder()
                .title(title)
                .group(group)
                .sessionSeries(series)
                .sessionTimeStart(start)
                .build());
    }

    /**
     * Inscrit un étudiant dans le groupe.
     *
     * <p>{@code StudentGroupEntity.onCreate()} écrase la date d'inscription par l'heure courante :
     * la valeur du builder est perdue à la persistance. Une date d'arrivée choisie passe donc par
     * une mise à jour en masse, qui ne déclenche pas les rappels de cycle de vie.</p>
     */
    private void enrol(StudentEntity student, Date dateAssigned) {
        StudentGroupEntity enrolment = em.persist(StudentGroupEntity.builder()
                .student(student)
                .group(group)
                .build());
        em.flush();
        if (dateAssigned != null) {
            em.getEntityManager()
                    .createQuery("UPDATE StudentGroupEntity sg SET sg.dateAssigned = :dateAssigned "
                            + "WHERE sg.id = :id")
                    .setParameter("dateAssigned", dateAssigned)
                    .setParameter("id", enrolment.getId())
                    .executeUpdate();
            em.clear();
        }
    }

    private Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 10, 0, 0);
        return calendar.getTime();
    }

    private SeriesRevenueDTO seriesRevenue(GroupRevenueDTO revenue, Long seriesId) {
        return revenue.series().stream()
                .filter(series -> series.seriesId().equals(seriesId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Série absente du relevé : " + seriesId));
    }

    private BigDecimal prorataCost(StudentEntity student, SessionSeriesEntity series) {
        return paymentCostResolver.resolve(student.getId(), series.getId()).monthTotalCost();
    }

    // ------------------------------------------------------------------
    // Attendu par série = somme des coûts au prorata individuels
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Attendu par série : somme des coûts au prorata individuels, non total_sessions × prix")
    void expectedPerSeriesIsTheSumOfIndividualProrataCosts() {
        GroupRevenueDTO revenue = groupRevenueService.getGroupRevenue(group.getId());

        // Première série : Ali doit deux séances, Nadia une seule (A1 est antérieure à son
        // arrivée). L'attendu vaut donc 6 000 et non 2 × 2 × 2 000 = 8 000.
        assertThat(prorataCost(ali, firstSeries)).isEqualByComparingTo("4000.00");
        assertThat(prorataCost(nadia, firstSeries)).isEqualByComparingTo("2000.00");
        assertThat(seriesRevenue(revenue, firstSeries.getId()).expected())
                .isEqualByComparingTo(prorataCost(ali, firstSeries).add(prorataCost(nadia, firstSeries)))
                .isEqualByComparingTo("6000.00");

        // Seconde série : les deux séances sont postérieures aux deux inscriptions.
        assertThat(seriesRevenue(revenue, secondSeries.getId()).expected())
                .isEqualByComparingTo(prorataCost(ali, secondSeries).add(prorataCost(nadia, secondSeries)))
                .isEqualByComparingTo("8000.00");

        assertThat(revenue.expected()).isEqualByComparingTo("14000.00");
    }

    // ------------------------------------------------------------------
    // Le report tombe du côté de la série destination
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Report : compté dans la série destination, absent de la série source")
    void carriedOverAmountIsCountedInTheTargetSeriesOnly() {
        PaymentAllocationResult result = paymentProcessingService.processPayment(
                ali.getId(), group.getId(), firstSeries.getId(), 6000.0);

        assertThat(result.amountAllocated()).isEqualByComparingTo("4000.00");
        assertThat(result.amountCarriedOver()).isEqualByComparingTo("2000.00");

        List<PaymentCarryOverEntity> carryOvers =
                paymentCarryOverRepository.findByStudentIdAndActiveTrueOrderByIdAsc(ali.getId());
        assertThat(carryOvers).singleElement().satisfies(carryOver -> {
            assertThat(carryOver.getSourceSeries().getId()).isEqualTo(firstSeries.getId());
            assertThat(carryOver.getTargetSeries().getId()).isEqualTo(secondSeries.getId());
            assertThat(carryOver.getAmount()).isEqualByComparingTo("2000.00");
        });

        GroupRevenueDTO revenue = groupRevenueService.getGroupRevenue(group.getId());

        // La série source ne porte que sa part imputée : les 2 000 reportés en sont absents.
        assertThat(seriesRevenue(revenue, firstSeries.getId()).collected())
                .isEqualByComparingTo("4000.00");
        // La série destination porte le montant reporté.
        assertThat(seriesRevenue(revenue, secondSeries.getId()).collected())
                .isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Report : jamais classé en trop-perçu ; reste à payer et trop-perçu restent distincts")
    void carriedOverAmountIsNeverClassifiedAsOverpayment() {
        paymentProcessingService.processPayment(ali.getId(), group.getId(), firstSeries.getId(), 6000.0);

        GroupRevenueDTO revenue = groupRevenueService.getGroupRevenue(group.getId());

        // Aucun trop-perçu : chaque série a été créditée à hauteur de son montant dû au plus.
        assertThat(revenue.overpaid()).isEqualByComparingTo("0.00");
        assertThat(seriesRevenue(revenue, firstSeries.getId()).overpaid()).isEqualByComparingTo("0.00");
        assertThat(seriesRevenue(revenue, secondSeries.getId()).overpaid()).isEqualByComparingTo("0.00");

        // Le reste à recouvrer est exposé séparément, et n'est pas compensé par le report :
        // première série, la part de Nadia (2 000) ; seconde série, le solde d'Ali (2 000) et la
        // part de Nadia (4 000).
        assertThat(seriesRevenue(revenue, firstSeries.getId()).remaining()).isEqualByComparingTo("2000.00");
        assertThat(seriesRevenue(revenue, secondSeries.getId()).remaining()).isEqualByComparingTo("6000.00");
        assertThat(revenue.remaining()).isEqualByComparingTo("8000.00");
    }

    // ------------------------------------------------------------------
    // Conservation : imputations par série = total des versements du groupe
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Somme des imputations par série = total des versements du groupe (exigence 8.4)")
    void seriesAllocationsSumUpToTheGroupTotal() {
        paymentProcessingService.processPayment(ali.getId(), group.getId(), firstSeries.getId(), 6000.0);
        paymentProcessingService.processPayment(nadia.getId(), group.getId(), firstSeries.getId(), 2000.0);

        GroupRevenueDTO revenue = groupRevenueService.getGroupRevenue(group.getId());

        BigDecimal sumOfSeries = revenue.series().stream()
                .map(SeriesRevenueDTO::collected)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sumOfSeries).isEqualByComparingTo(revenue.collected());
        assertThat(sumOfSeries).isEqualByComparingTo(paymentRepository.sumPaidForGroup(group.getId()));
        assertThat(sumOfSeries).isEqualByComparingTo("8000.00");

        // Rien ne reste hors des séries : la ventilation par séance couvre la totalité du
        // registre, donc l'écart exposé par le relevé est nul.
        assertThat(revenue.unassignedToSeries()).isEqualByComparingTo("0.00");
        assertThat(BigDecimal.valueOf(paymentDetailRepository.sumCollectedForGroup(group.getId())))
                .isEqualByComparingTo("8000.00");
    }

    // ------------------------------------------------------------------
    // Restitution du report dans l'historique de l'étudiant (exigences 6.2, 6.4)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Historique : le report est restitué et distingué d'une imputation directe")
    void paymentHistoryDistinguishesCarriedOverFromDirectlyAllocated() {
        paymentProcessingService.processPayment(ali.getId(), group.getId(), firstSeries.getId(), 6000.0);

        StudentPaymentHistoryDTO history = paymentHistoryService.getStudentPaymentHistory(ali.getId());

        assertThat(history.carryOvers()).singleElement().satisfies(carryOver -> {
            assertThat(carryOver.amount()).isEqualByComparingTo("2000.00");
            assertThat(carryOver.sourceSeriesName()).isEqualTo(firstSeries.getName());
            assertThat(carryOver.targetSeriesName()).isEqualTo(secondSeries.getName());
        });

        SeriesPaymentHistoryDTO source = history.series().stream()
                .filter(series -> firstSeries.getId().equals(series.seriesId()))
                .findFirst().orElseThrow();
        SeriesPaymentHistoryDTO target = history.series().stream()
                .filter(series -> secondSeries.getId().equals(series.seriesId()))
                .findFirst().orElseThrow();

        // Série visée : 4 000 saisis directement, rien reçu par report.
        assertThat(source.amountAllocatedDirectly()).isEqualByComparingTo("4000.00");
        assertThat(source.amountReceivedByCarryOver()).isEqualByComparingTo("0.00");
        // Série suivante : la totalité de son cumul provient du report.
        assertThat(target.amountAllocatedDirectly()).isEqualByComparingTo("0.00");
        assertThat(target.amountReceivedByCarryOver()).isEqualByComparingTo("2000.00");
    }
}
