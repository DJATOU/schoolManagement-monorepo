package com.school.management.service.payment;

import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.EncashmentKind;
import com.school.management.persistance.EncashmentStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentCarryOverEntity;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.service.correction.CorrectionReason;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Cycle de vie d'un Encaissement sur une vraie base (H2) : enregistrement, imputation, cumul de
 * série, neutralisation (spec admin-corrections, exigences 1.1, 1.2, 1.4, 2.2, 2.3, 2.5).
 *
 * <p>Seul le coût au prorata est simulé : il décide du statut stocké, et sa propre règle est
 * éprouvée ailleurs. Tout le reste passe par les vrais dépôts, et chaque vérification relit la base
 * après un {@code clear()}.</p>
 */
@DataJpaTest
@Import({ EncashmentService.class, ReceiptNumberService.class })
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("Encaissement : enregistrement, imputation, neutralisation")
class EncashmentServiceIntegrationTest {

    private static final String ADMIN = "mme.benali";

    // Pas de @TestConfiguration imbriquée ici : la classe externe et les classes @Nested
    // obtiendraient deux contextes Spring distincts, et l'EntityManager injecté dans l'instance
    // externe ne verrait pas la transaction ouverte par le contexte de la classe imbriquée.
    @Autowired private EncashmentService service;
    @Autowired private TestEntityManager em;
    @MockBean private PaymentCostResolver paymentCostResolver;
    @MockBean private AuditorAware<String> auditorAware;

    private StudentEntity student;
    private StudentEntity otherStudent;
    private GroupEntity group;
    private SessionSeriesEntity s1;
    private SessionSeriesEntity s2;
    private PaymentEntity p1;
    private PaymentEntity p2;

    @BeforeEach
    void setUp() {
        student = em.persist(StudentEntity.builder().firstName("Amine").lastName("Belkacem").build());
        otherStudent = em.persist(StudentEntity.builder().firstName("Lina").lastName("Hamdani").build());
        group = em.persist(GroupEntity.builder().name("Math 1ère A").build());
        s1 = em.persist(SessionSeriesEntity.builder().name("Série 1").group(group).build());
        s2 = em.persist(SessionSeriesEntity.builder().name("Série 2").group(group).build());
        p1 = em.persist(PaymentEntity.builder().student(student).group(group).sessionSeries(s1).amountPaid(0.0).build());
        p2 = em.persist(PaymentEntity.builder().student(student).group(group).sessionSeries(s2).amountPaid(0.0).build());
        em.flush();
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(ADMIN));
        seriesCost("8000.00");
    }

    private void seriesCost(String cost) {
        BigDecimal value = new BigDecimal(cost);
        when(paymentCostResolver.resolve(anyLong(), anyLong()))
                .thenReturn(new PaymentCostResolver.PaymentStatusResult(value, BigDecimal.ZERO, BigDecimal.ZERO,
                        false, false));
    }

    private EncashmentEntity open(String amount) {
        return service.open(new EncashmentService.NewEncashment(
                student, group, s1, new BigDecimal(amount), EncashmentKind.REGULAR, "Espèces", null));
    }

    private PaymentEntity reload(PaymentEntity payment) {
        em.flush();
        em.clear();
        return em.find(PaymentEntity.class, payment.getId());
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((CustomServiceException) e).getStatus();
    }

    // ------------------------------------------------------------------
    // Enregistrement
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Enregistrement")
    class Enregistrement {

        @Test
        @DisplayName("numéro de reçu, auteur, date et statut fixés par le serveur")
        void serverFixesNumberAuthorDateAndStatus() {
            Date before = new Date();
            EncashmentEntity encashment = open("2000");
            Date after = new Date();
            em.flush();
            em.clear();

            EncashmentEntity reloaded = em.find(EncashmentEntity.class, encashment.getId());
            assertThat(reloaded.getReceiptNumber()).isEqualTo("RECU-" + Year.now() + "-0001");
            assertThat(reloaded.getReceivedBy()).isEqualTo(ADMIN);
            // Relu depuis la base, c'est un java.sql.Timestamp : on compare des millisecondes,
            // Timestamp et Date ne se comparent pas symétriquement entre eux.
            assertThat(reloaded.getReceivedAt().getTime()).isBetween(before.getTime(), after.getTime());
            assertThat(reloaded.getStatus()).isEqualTo(EncashmentStatus.ACTIVE);
            assertThat(reloaded.getKind()).isEqualTo(EncashmentKind.REGULAR);
            assertThat(reloaded.getAmountReceived()).isEqualByComparingTo("2000.00");
            assertThat(reloaded.getAmountReceived().scale()).isEqualTo(2);
            assertThat(reloaded.getPaymentMethod()).isEqualTo("Espèces");
        }

        @Test
        @DisplayName("deux encaissements : deux numéros consécutifs")
        void consecutiveNumbers() {
            String first = open("2000").getReceiptNumber();
            String second = open("1500").getReceiptNumber();
            assertThat(first).endsWith("-0001");
            assertThat(second).endsWith("-0002");
        }

        @Test
        @DisplayName("montant nul, négatif ou absent refusé")
        void nonPositiveAmountIsRejected() {
            for (String amount : new String[] { "0", "-500", "0.004" }) {
                assertThatThrownBy(() -> open(amount))
                        .as("montant %s", amount)
                        .isInstanceOf(CustomServiceException.class)
                        .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
            }
            assertThatThrownBy(() -> service.open(new EncashmentService.NewEncashment(
                    student, group, s1, null, EncashmentKind.REGULAR, null, null)))
                    .isInstanceOf(CustomServiceException.class);
        }

        @Test
        @DisplayName("série d'un autre groupe refusée")
        void seriesOfAnotherGroupIsRejected() {
            GroupEntity otherGroup = em.persist(GroupEntity.builder().name("Physique 2ème").build());
            SessionSeriesEntity foreign = em.persist(SessionSeriesEntity.builder().name("Série P").group(otherGroup).build());

            assertThatThrownBy(() -> service.open(new EncashmentService.NewEncashment(
                    student, group, foreign, new BigDecimal("2000"), EncashmentKind.REGULAR, null, null)))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("n'appartient pas au groupe");
        }

        @Test
        @DisplayName("refus : aucun numéro de reçu consommé")
        void refusalConsumesNoNumber() {
            assertThatThrownBy(() -> open("0")).isInstanceOf(CustomServiceException.class);
            assertThat(open("2000").getReceiptNumber()).endsWith("-0001");
        }
    }

    // ------------------------------------------------------------------
    // Imputation et cumul
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Imputation et cumul de série")
    class Imputation {

        @Test
        @DisplayName("le cumul est la somme des imputations actives, encaissement après encaissement")
        void cumulIsTheSumOfActiveAllocations() {
            service.allocate(open("4000"), p1, new BigDecimal("4000"), false);
            assertThat(reload(p1).getAmountPaid()).isEqualTo(4000.0);

            service.allocate(open("2000"), p1, new BigDecimal("2000"), false);
            PaymentEntity reloaded = reload(p1);
            assertThat(reloaded.getAmountPaid()).isEqualTo(6000.0);
            assertThat(reloaded.getStatus()).isEqualTo(PaymentLineStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("cumul égal au coût : statut soldé")
        void cumulReachingTheCostIsCompleted() {
            seriesCost("4000.00");
            service.allocate(open("4000"), p1, new BigDecimal("4000"), false);
            assertThat(reload(p1).getStatus()).isEqualTo(PaymentLineStatus.COMPLETED);
        }

        @Test
        @DisplayName("coût introuvable : jamais annoncé soldé")
        void unknownCostIsNeverCompleted() {
            when(paymentCostResolver.resolve(anyLong(), anyLong())).thenThrow(new IllegalStateException("série"));
            service.allocate(open("8000"), p1, new BigDecimal("8000"), false);
            assertThat(reload(p1).getStatus()).isEqualTo(PaymentLineStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("un encaissement réparti sur deux séries, report compris")
        void encashmentSplitOverTwoSeries() {
            EncashmentEntity encashment = open("6000");
            service.allocate(encashment, p1, new BigDecimal("4000"), false);
            service.allocate(encashment, p2, new BigDecimal("2000"), true);

            assertThat(reload(p1).getAmountPaid()).isEqualTo(4000.0);
            assertThat(reload(p2).getAmountPaid()).isEqualTo(2000.0);
        }

        @Test
        @DisplayName("parts au-delà du montant reçu refusées : aucune imputation ne fabrique d'argent")
        void allocationsCannotExceedTheAmountReceived() {
            EncashmentEntity encashment = open("6000");
            service.allocate(encashment, p1, new BigDecimal("4000"), false);

            assertThatThrownBy(() -> service.allocate(encashment, p2, new BigDecimal("2000.01"), true))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("2000.00");
            assertThat(reload(p2).getAmountPaid()).isZero();
        }

        @Test
        @DisplayName("ligne de paiement d'un autre étudiant refusée")
        void paymentOfAnotherStudentIsRejected() {
            PaymentEntity foreign = em.persist(PaymentEntity.builder()
                    .student(otherStudent).group(group).sessionSeries(s1).amountPaid(0.0).build());

            assertThatThrownBy(() -> service.allocate(open("2000"), foreign, new BigDecimal("2000"), false))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("n'est pas celle de l'étudiant");
        }

        @Test
        @DisplayName("indicateur de report contradictoire avec la série refusé")
        void carriedOverFlagMustMatchTheSeries() {
            EncashmentEntity encashment = open("6000");
            assertThatThrownBy(() -> service.allocate(encashment, p1, new BigDecimal("1000"), true))
                    .hasMessageContaining("Un report ne peut pas créditer la série visée");
            assertThatThrownBy(() -> service.allocate(encashment, p2, new BigDecimal("1000"), false))
                    .hasMessageContaining("imputation directe");
        }

        @Test
        @DisplayName("part nulle refusée")
        void zeroPartIsRejected() {
            assertThatThrownBy(() -> service.allocate(open("2000"), p1, BigDecimal.ZERO, false))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    // ------------------------------------------------------------------
    // Neutralisation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Neutralisation")
    class Neutralisation {

        @Test
        @DisplayName("imputations, ventilation et reports désactivés ; cumuls recalculés ; trace d'annulation")
        void neutralizationRemovesTheEncashmentFromEveryAmount() {
            EncashmentEntity encashment = open("6000");
            EncashmentAllocationEntity direct = service.allocate(encashment, p1, new BigDecimal("4000"), false);
            EncashmentAllocationEntity carried = service.allocate(encashment, p2, new BigDecimal("2000"), true);
            PaymentDetailEntity detail = em.persist(PaymentDetailEntity.builder()
                    .payment(p1).amountPaid(2000.0).encashmentAllocation(direct).build());
            PaymentCarryOverEntity carryOver = em.persist(PaymentCarryOverEntity.builder()
                    .student(student).sourceSeries(s1).targetSeries(s2).targetPayment(p2)
                    .amount(new BigDecimal("2000.00")).originPaymentDate(new Date())
                    .encashmentAllocation(carried).build());

            service.neutralize(encashment.getId(), new CorrectionReason(CorrectionReasonType.WRONG_AMOUNT, null));
            em.flush();
            em.clear();

            EncashmentEntity cancelled = em.find(EncashmentEntity.class, encashment.getId());
            assertThat(cancelled.getStatus()).isEqualTo(EncashmentStatus.CANCELLED);
            assertThat(cancelled.getCancelledAt()).isNotNull();
            assertThat(cancelled.getCancelledBy()).isEqualTo(ADMIN);
            assertThat(cancelled.getCancelReasonType()).isEqualTo(CorrectionReasonType.WRONG_AMOUNT);
            // L'argent reçu reste écrit tel quel : on neutralise, on n'efface pas.
            assertThat(cancelled.getAmountReceived()).isEqualByComparingTo("6000.00");

            assertThat(em.find(EncashmentAllocationEntity.class, direct.getId()).getActive()).isFalse();
            assertThat(em.find(EncashmentAllocationEntity.class, carried.getId()).getActive()).isFalse();
            assertThat(em.find(PaymentDetailEntity.class, detail.getId()).getActive()).isFalse();
            assertThat(em.find(PaymentCarryOverEntity.class, carryOver.getId()).getActive()).isFalse();

            PaymentEntity reloadedP1 = em.find(PaymentEntity.class, p1.getId());
            assertThat(reloadedP1.getAmountPaid()).isZero();
            assertThat(reloadedP1.getStatus()).isEqualTo(PaymentLineStatus.PENDING);
            assertThat(em.find(PaymentEntity.class, p2.getId()).getAmountPaid()).isZero();
        }

        @Test
        @DisplayName("les autres encaissements de la série ne sont pas touchés")
        void otherEncashmentsAreUntouched() {
            EncashmentEntity wrong = open("20000");
            service.allocate(wrong, p1, new BigDecimal("8000"), false);
            EncashmentEntity right = open("2000");
            EncashmentAllocationEntity kept = service.allocate(right, p1, new BigDecimal("2000"), false);

            seriesCost("20000.00");
            service.neutralize(wrong.getId(), CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT));

            PaymentEntity reloaded = reload(p1);
            assertThat(reloaded.getAmountPaid()).isEqualTo(2000.0);
            assertThat(reloaded.getStatus()).isEqualTo(PaymentLineStatus.IN_PROGRESS);
            assertThat(em.find(EncashmentAllocationEntity.class, kept.getId()).getActive()).isTrue();
            assertThat(em.find(EncashmentEntity.class, right.getId()).getStatus()).isEqualTo(EncashmentStatus.ACTIVE);
        }

        @Test
        @DisplayName("motif « Autre » enregistré avec son texte")
        void otherReasonKeepsItsText() {
            EncashmentEntity encashment = open("2000");
            service.neutralize(encashment.getId(),
                    new CorrectionReason(CorrectionReasonType.OTHER, "Parent venu deux fois"));
            em.flush();
            em.clear();
            assertThat(em.find(EncashmentEntity.class, encashment.getId()).getCancelReasonText())
                    .isEqualTo("Parent venu deux fois");
        }

        @Test
        @DisplayName("seconde annulation refusée, 409")
        void secondNeutralizationIsRejected() {
            EncashmentEntity encashment = open("2000");
            service.neutralize(encashment.getId(), CorrectionReason.of(CorrectionReasonType.DATA_ENTRY_ERROR));

            assertThatThrownBy(() -> service.neutralize(encashment.getId(),
                    CorrectionReason.of(CorrectionReasonType.DATA_ENTRY_ERROR)))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT))
                    .hasMessageContaining("déjà annulé");
        }

        @Test
        @DisplayName("encaissement annulé : il ne peut plus rien créditer")
        void cancelledEncashmentCannotAllocate() {
            EncashmentEntity encashment = open("2000");
            service.neutralize(encashment.getId(), CorrectionReason.of(CorrectionReasonType.DATA_ENTRY_ERROR));

            assertThatThrownBy(() -> service.allocate(encashment, p1, new BigDecimal("2000"), false))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("encaissement introuvable : 404")
        void unknownEncashmentIsNotFound() {
            assertThatThrownBy(() -> service.neutralize(999_999L,
                    CorrectionReason.of(CorrectionReasonType.DATA_ENTRY_ERROR)))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}
