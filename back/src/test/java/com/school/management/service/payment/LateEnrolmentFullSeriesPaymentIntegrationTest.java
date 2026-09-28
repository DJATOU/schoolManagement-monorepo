package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.dto.revenue.GroupRevenueDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.service.DiscountService;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Non-régression du défaut d'origine : l'étudiant inscrit alors que toutes les séances de la
 * série sont déjà passées, et le versement d'une série entière (exigences 1.3, 3.1, 4.3).
 *
 * <h2>Le cas réel observé en base</h2>
 * Un étudiant inscrit le 24/08/2026 dans un groupe dont les quatre séances de la série étaient
 * datées du 05/09/2025 et des 11, 12 et 13/07/2026 — toutes antérieures à son inscription, aucune
 * assistée. {@code resolveBillableSessions} renvoyait donc <strong>zéro</strong> séance facturable,
 * le coût de la série tombait à 0, et les 8 000 DA versés partaient
 * <strong>intégralement en trop-perçu</strong> : de l'argent encaissé sans contrepartie, sur une
 * série que la facturation ne reconnaissait pas.
 *
 * <p>C'est ce défaut qui a motivé la fonctionnalité entière. Le plafond du devis se calculait sur
 * {@code series.totalSessions} tandis que la facturation appliquait déjà le prorata : les deux se
 * croyaient d'accord, et personne ne les avait fait converger sur un même jeu de données.</p>
 *
 * <h2>Le comportement attendu</h2>
 * Le versement est <strong>refusé en totalité</strong>, avec le maximum encaissable annoncé
 * (exigence 5.12), et <strong>aucune écriture</strong> n'a lieu : ni ligne de paiement, ni
 * affectation par séance, ni trace de report. Le refus total est un choix comptable : un
 * encaissement partiel ferait diverger l'argent physiquement reçu du montant enregistré.
 *
 * <h2>Pourquoi un test d'intégration et non un test unitaire</h2>
 * Le défaut ne vivait dans aucun composant isolé : il naissait du <em>désaccord</em> entre le
 * résolveur de séances facturables, le devis, le plan de répartition et la ventilation. Simuler
 * l'un de ces maillons reviendrait à postuler l'accord que ce test doit démontrer. La chaîne
 * complète tourne donc ici sur H2, avec les vrais services.
 *
 * <h2>Datation du scénario</h2>
 * Les dates réelles sont reproduites en <strong>écarts</strong> par rapport à l'inscription plutôt
 * qu'en valeurs absolues : {@code StudentGroupEntity.onCreate()} force la date d'inscription à
 * l'instant courant — l'étudiant est donc bien inscrit « ce jour » — et un scénario figé en 2026
 * cesserait de décrire des séances passées le jour où l'horloge le dépasserait.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({ BillableSessionsResolverImpl.class, CatchUpBillingQualifierImpl.class, DiscountService.class, PaymentCostResolver.class,
        PaymentQuoteService.class, PaymentAllocationService.class, PaymentDistributionService.class,
        PaymentCarryOverService.class, PaymentProcessingService.class, GroupRevenueService.class })
class LateEnrolmentFullSeriesPaymentIntegrationTest {

    /** Prix de la séance : 4 séances × 2 000 = les 8 000 DA du cas réel. */
    private static final double PRICE_PER_SESSION = 2000.0;

    /** Montant du cas réel : le prix d'une série entière. */
    private static final double FULL_SERIES_AMOUNT = 8000.0;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    @Autowired
    private PaymentQuoteService paymentQuoteService;

    @Autowired
    private GroupRevenueService groupRevenueService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentDetailRepository paymentDetailRepository;

    @Autowired
    private PaymentCarryOverRepository paymentCarryOverRepository;

    private GroupEntity group;
    private SessionSeriesEntity series;
    private StudentEntity student;

    // ------------------------------------------------------------------
    // Fixture : le cas réel, daté en écarts par rapport à l'inscription
    // ------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        PricingEntity pricing = em.persist(PricingEntity.builder()
                .price(PRICE_PER_SESSION)
                .build());

        group = em.persist(GroupEntity.builder()
                .name("Math 1ère B")
                .price(pricing)
                .sessionNumberPerSerie(4)
                .build());

        LocalDate today = LocalDate.now();

        series = em.persist(SessionSeriesEntity.builder()
                .name("Série 1")
                .group(group)
                .totalSessions(4)
                .serieTimeStart(toDate(today.minusDays(354)))
                .build());

        // Les quatre séances du cas réel : une isolée près d'un an plus tôt (05/09/2025 pour une
        // inscription au 24/08/2026), puis trois consécutives six semaines plus tôt (11–13/07/2026).
        persistSession("S1", today.minusDays(354));
        persistSession("S2", today.minusDays(44));
        persistSession("S3", today.minusDays(43));
        persistSession("S4", today.minusDays(42));

        student = em.persist(StudentEntity.builder()
                .firstName("Yacine").lastName("Haddad").build());

        // Inscription « ce jour » : onCreate() horodate dateAssigned à l'instant courant, donc
        // postérieure aux quatre séances. Aucune présence n'est enregistrée : les quatre séances
        // sont antérieures à l'inscription et non assistées, donc toutes exclues (exigence 1.3).
        em.persist(StudentGroupEntity.builder()
                .student(student)
                .group(group)
                .build());
        em.flush();
    }

    private void persistSession(String title, LocalDate day) {
        em.persist(SessionEntity.builder()
                .title(title)
                .group(group)
                .sessionSeries(series)
                .sessionTimeStart(toDate(day))
                .build());
    }

    private static Date toDate(LocalDate day) {
        return Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    // ------------------------------------------------------------------
    // Le point de départ : plus aucune séance facturable
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Toutes les séances antérieures à l'inscription et non assistées : "
            + "zéro facturable, coût nul, plafond nul (exigences 1.3, 3.1)")
    void everySessionBeforeEnrolmentIsExcludedFromBilling() {
        PaymentQuoteDTO quote = paymentQuoteService.quote(student.getId(), series.getId());

        assertThat(quote.billableSessions())
                .as("aucune séance postérieure à l'inscription, aucune assistée")
                .isZero();
        assertThat(quote.excludedSessions())
                .as("les quatre séances restent visibles, écartées et non dues (exigence 1.3)")
                .isEqualTo(4);

        // Le coût au prorata est nul : c'est exact, et c'est précisément ce qui rendait les
        // 8 000 DA inimputables. Le défaut n'était pas ce zéro, mais le fait de l'encaisser
        // quand même.
        assertThat(quote.monthTotalCost()).isEqualByComparingTo("0.00");
        assertThat(quote.amountDueSoFar()).isEqualByComparingTo("0.00");
        assertThat(quote.maxPayable())
                .as("plafond aligné sur le coût au prorata (exigence 3.1)")
                .isEqualByComparingTo("0.00");
        assertThat(quote.existingExcess())
                .as("rien n'a encore été encaissé : aucun excédent préexistant")
                .isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------
    // Le défaut d'origine : refus explicite, et non un trop-perçu intégral
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Versement d'une série entière : refus 400 annonçant le maximum encaissable, "
            + "au lieu des 8 000 DA classés en trop-perçu")
    void fullSeriesPaymentIsRefusedWithTheMaximumPayableInsteadOfBecomingAnOverpayment() {
        assertThatThrownBy(() -> paymentProcessingService.processPayment(
                student.getId(), group.getId(), series.getId(), FULL_SERIES_AMOUNT))
                .isInstanceOf(CustomServiceException.class)
                // Le maximum réellement encaissable sur la chaîne : ici zéro, aucune série ne
                // pouvant rien recevoir (exigence 5.12).
                .hasMessageContaining("au maximum 0.00 DA")
                // Le montant refusé est rappelé, pour que l'administrateur sache ce qu'il tient
                // encore en main.
                .hasMessageContaining("8000.00 DA")
                .hasMessageContaining("refusé en totalité")
                .extracting(exception -> ((CustomServiceException) exception).getStatus())
                .as("refus de saisie, non erreur serveur")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Le message ne conseille pas de créer des séances : la série en compte quatre, "
            + "aucune n'est facturable à cet étudiant")
    void refusalMessageDoesNotAdviseCreatingSessionsForASeriesThatAlreadyHasThem() {
        // Non-régression de la formulation. Le message annonçait « la série « Série 1 » ne
        // comporte aucune séance : créez d'abord les séances… » alors que les quatre séances du
        // cas réel existent bel et bien. Le conseil était faux, et le suivre n'aurait rien
        // débloqué : une cinquième séance datée du passé resterait écartée. Un conseil inexact
        // est pire qu'un conseil absent.
        assertThatThrownBy(() -> paymentProcessingService.processPayment(
                student.getId(), group.getId(), series.getId(), FULL_SERIES_AMOUNT))
                .isInstanceOf(CustomServiceException.class)
                // Le fait exact, et la série nommée.
                .hasMessageContaining("La série « Série 1 » comporte des séances, mais aucune "
                        + "n'est facturable à cet étudiant")
                // L'action réellement corrective (exigence 5.12).
                .hasMessageContaining("il faut une séance postérieure à son inscription")
                // Les deux formulations qui seraient fausses ici.
                .hasMessageNotContaining("ne comporte aucune séance")
                .hasMessageNotContaining("créez d'abord les séances")
                .hasMessageNotContaining("déjà soldées");
    }

    @Test
    @DisplayName("Après le refus, la base est intacte : aucune ligne de paiement, "
            + "aucune affectation par séance, aucune trace de report")
    void refusedPaymentLeavesNoTraceInTheDatabase() {
        assertThatThrownBy(() -> paymentProcessingService.processPayment(
                student.getId(), group.getId(), series.getId(), FULL_SERIES_AMOUNT))
                .isInstanceOf(CustomServiceException.class);

        // Le plan est calculé avant toute écriture : il n'y a donc rien à annuler, et rien à
        // trouver. La base est interrogée après le refus plutôt que déduite du message.
        assertThat(paymentRepository.findAll())
                .as("aucune ligne de paiement créée, pas même une ligne à 0 (exigence 4.3)")
                .isEmpty();
        assertThat(paymentDetailRepository.findAll())
                .as("aucune affectation sur une séance non facturable (exigences 1.3, 4.5)")
                .isEmpty();
        assertThat(paymentCarryOverRepository.findByStudentIdAndActiveTrueOrderByIdAsc(student.getId()))
                .as("aucun report : il n'existe aucune série apte à recevoir le surplus")
                .isEmpty();
    }

    @Test
    @DisplayName("Le relevé du groupe reste à zéro : ni encaissé, ni trop-perçu — "
            + "c'est exactement ce que le défaut produisait à 8 000 DA")
    void groupRevenueShowsNeitherCollectedNorOverpaidAmount() {
        assertThatThrownBy(() -> paymentProcessingService.processPayment(
                student.getId(), group.getId(), series.getId(), FULL_SERIES_AMOUNT))
                .isInstanceOf(CustomServiceException.class);

        GroupRevenueDTO revenue = groupRevenueService.getGroupRevenue(group.getId());

        assertThat(revenue.collected()).isEqualByComparingTo("0.00");
        assertThat(revenue.overpaid())
                .as("le défaut d'origine classait ici les 8 000 DA versés")
                .isEqualByComparingTo("0.00");
        // L'attendu est nul lui aussi : aucune séance n'est facturable à cet étudiant, donc le
        // groupe ne lui réclame rien. Une dette apparente serait le symptôme inverse.
        assertThat(revenue.expected()).isEqualByComparingTo("0.00");
    }
}
