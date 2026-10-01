package com.school.management.controller;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.EncashmentKind;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.EncashmentStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.EncashmentAllocationRepository;
import com.school.management.repository.EncashmentRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.ReceiptCounterRepository;
import com.school.management.service.correction.CorrectionReason;
import com.school.management.service.payment.EncashmentService;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentIdempotencyRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP de {@code POST /api/payments/process}, de bout en bout : contrôleur → service →
 * dépôts → H2.
 *
 * <p>Aucun test ne couvrait ce point d'entrée au niveau HTTP. Les règles de ventilation sont
 * éprouvées au niveau service ({@code PaymentAllocationServiceTest}, {@code
 * GroupRevenueCarryOverIntegrationTest}), mais jamais comme contrat d'API : statut, corps et,
 * surtout, <b>absence d'écriture après un refus</b>. C'est ce dernier point qui compte le plus.
 * Un versement refusé qui laisserait une ligne en base ferait diverger l'argent reçu du montant
 * enregistré, et la différence resterait en main sans trace.</p>
 *
 * <p>Chaque refus est donc suivi d'un contrôle du registre : {@code payments}, {@code
 * payment_detail}, {@code payment_carry_over} et {@code payment_idempotency} doivent être
 * inchangés.</p>
 *
 * <p>Jeu de données : un groupe à 2 000 DA par séance, deux séances par série, séances en 2030.
 * L'inscription, datée « maintenant » par {@code StudentGroupEntity.onCreate}, les précède toutes :
 * elles sont donc facturables.</p>
 */
@SpringBootTest
// Filtres de sécurité désactivés : ce test porte sur le contrat métier du point d'entrée,
// l'autorisation est couverte par SecurityAuthorizationMatrixPropertyTest.
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-endpoint;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("POST /api/payments/process")
class PaymentProcessingEndpointIntegrationTest {

    private static final double PRICE = 2000.0;
    private static final String URL = "/api/payments/process";

    @Autowired private MockMvc mockMvc;

    @Autowired private SchoolYearRepository schoolYearRepository;
    @Autowired private PricingRepository pricingRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private SessionSeriesRepository seriesRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentDetailRepository paymentDetailRepository;
    @Autowired private PaymentCarryOverRepository carryOverRepository;
    @Autowired private PaymentIdempotencyRepository idempotencyRepository;
    @Autowired private EncashmentRepository encashmentRepository;
    @Autowired private EncashmentAllocationRepository allocationRepository;
    @Autowired private ReceiptCounterRepository receiptCounterRepository;
    @Autowired private EncashmentService encashmentService;

    private SchoolYearEntity currentYear;
    private GroupEntity group;
    private StudentEntity student;
    private SessionSeriesEntity s1;

    // ------------------------------------------------------------------
    // Socle
    // ------------------------------------------------------------------

    /**
     * Base vidée avant chaque test : {@code @SpringBootTest} n'annule aucune transaction, et
     * {@code SchoolYearMigrationRunner} crée une année au démarrage du contexte.
     */
    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        carryOverRepository.deleteAll();
        paymentDetailRepository.deleteAll();
        allocationRepository.deleteAll();
        encashmentRepository.deleteAll();
        // Compteur remis à zéro : chaque test voit son premier reçu porter le rang 0001.
        receiptCounterRepository.deleteAll();
        paymentRepository.deleteAll();
        attendanceRepository.deleteAll();
        sessionRepository.deleteAll();
        seriesRepository.deleteAll();
        studentGroupRepository.deleteAll();
        groupRepository.deleteAll();
        studentRepository.deleteAll();
        pricingRepository.deleteAll();
        schoolYearRepository.deleteAll();

        currentYear = schoolYearRepository.save(SchoolYearEntity.builder()
                .label("2029-2030").startDate(date(2029, 9, 1)).endDate(date(2030, 6, 30))
                .isCurrent(true).build());
        group = persistGroup("Math 1ère A", currentYear);
        student = studentRepository.save(StudentEntity.builder().firstName("Amine").lastName("Belkacem").build());
        enrol(student, group);
        s1 = persistSeries(group, "Série 1", date(2030, 1, 7), date(2030, 1, 14));
    }

    private GroupEntity persistGroup(String name, SchoolYearEntity year) {
        PricingEntity pricing = pricingRepository.save(PricingEntity.builder().price(PRICE).build());
        return groupRepository.save(GroupEntity.builder()
                .name(name).price(pricing).schoolYear(year).sessionNumberPerSerie(2).build());
    }

    private void enrol(StudentEntity s, GroupEntity g) {
        studentGroupRepository.save(StudentGroupEntity.builder().student(s).group(g).build());
    }

    /** Série et ses séances. Sans date, la série est créée vide : aucune séance planifiée. */
    private SessionSeriesEntity persistSeries(GroupEntity g, String name, Date... sessionDates) {
        SessionSeriesEntity series = seriesRepository.save(SessionSeriesEntity.builder()
                .name(name).group(g).totalSessions(2).serieTimeStart(date(2030, 1, 1)).build());
        for (int i = 0; i < sessionDates.length; i++) {
            sessionRepository.save(SessionEntity.builder()
                    .title(name + " — séance " + (i + 1)).group(g).sessionSeries(series)
                    .sessionTimeStart(sessionDates[i]).build());
        }
        return series;
    }

    private static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static String body(Long studentId, Long groupId, Long seriesId, Object amount) {
        return String.format("{\"studentId\":%s,\"groupId\":%s,\"sessionSeriesId\":%s,\"amountPaid\":%s}",
                studentId, groupId, seriesId, amount);
    }

    private ResultActions pay(Long seriesId, Object amount) throws Exception {
        return pay(seriesId, amount, null);
    }

    private ResultActions pay(Long seriesId, Object amount, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(student.getId(), group.getId(), seriesId, amount));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    /** État du registre, pour vérifier qu'un refus n'a rien écrit. */
    private record Ledger(long payments, long details, long carryOvers, long idempotency,
                          long encashments, long allocations) {
    }

    private Ledger ledger() {
        return new Ledger(paymentRepository.count(), paymentDetailRepository.count(),
                carryOverRepository.count(), idempotencyRepository.count(),
                encashmentRepository.count(), allocationRepository.count());
    }

    /** Numéro de reçu attendu pour le rang donné, dans l'année du serveur. */
    private static String receipt(int rank) {
        return String.format("RECU-%d-%04d", Year.now().getValue(), rank);
    }

    /** Cumul de la série relu en base, comparé à la somme de ses Imputations actives. */
    private double cumulOf(Long seriesId, Long studentId) {
        PaymentEntity line = paymentRepository.findActiveByStudentIdAndSessionSeriesId(studentId, seriesId)
                .orElseThrow();
        assertThat(BigDecimal.valueOf(line.getAmountPaid()))
                .as("cumul = somme des Imputations actives (exigence 1.4)")
                .isEqualByComparingTo(allocationRepository.sumActiveAmountForPayment(line.getId()));
        return line.getAmountPaid();
    }

    // ------------------------------------------------------------------
    // Cas nominaux
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Encaissement accepté")
    class Accepte {

        @Test
        @DisplayName("B.1 montant égal au dû : une imputation, aucun report")
        void montantExact() throws Exception {
            pay(s1.getId(), 4000)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amountReceived").value(4000.00))
                    .andExpect(jsonPath("$.amountAllocated").value(4000.00))
                    .andExpect(jsonPath("$.amountCarriedOver").value(0.00))
                    .andExpect(jsonPath("$.carryOvers", hasSize(0)))
                    .andExpect(jsonPath("$.payment.id").isNumber());

            assertThat(paymentRepository.count()).isEqualTo(1);
            assertThat(carryOverRepository.count()).isZero();
        }

        @Test
        @DisplayName("B.2 surplus avec série suivante ouverte : report nommé dans la réponse")
        void reportSurSerieSuivante() throws Exception {
            SessionSeriesEntity s2 = persistSeries(group, "Série 2", date(2030, 2, 4), date(2030, 2, 11));

            pay(s1.getId(), 6000)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amountAllocated").value(4000.00))
                    .andExpect(jsonPath("$.amountCarriedOver").value(2000.00))
                    .andExpect(jsonPath("$.carryOvers", hasSize(1)))
                    .andExpect(jsonPath("$.carryOvers[0].seriesId").value(s2.getId()))
                    .andExpect(jsonPath("$.carryOvers[0].seriesName").value("Série 2"))
                    .andExpect(jsonPath("$.carryOvers[0].amount").value(2000.00));

            // Le report n'est pas un trop-perçu : il est tracé comme tel.
            assertThat(carryOverRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("B.5 série suivante soldée : sautée, le reste va sur la suivante")
        void serieSoldeeSautee() throws Exception {
            SessionSeriesEntity s2 = persistSeries(group, "Série 2", date(2030, 2, 4), date(2030, 2, 11));
            SessionSeriesEntity s3 = persistSeries(group, "Série 3", date(2030, 3, 4), date(2030, 3, 11));
            pay(s2.getId(), 4000).andExpect(status().isOk());

            pay(s1.getId(), 6000)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amountAllocated").value(4000.00))
                    .andExpect(jsonPath("$.carryOvers", hasSize(1)))
                    .andExpect(jsonPath("$.carryOvers[0].seriesId").value(s3.getId()));
        }

        @Test
        @DisplayName("série visée soldée : la totalité part en report, la série visée reçoit 0")
        void serieViseeSoldee() throws Exception {
            SessionSeriesEntity s2 = persistSeries(group, "Série 2", date(2030, 2, 4), date(2030, 2, 11));
            pay(s1.getId(), 4000).andExpect(status().isOk());

            pay(s1.getId(), 2000)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amountAllocated").value(0.00))
                    .andExpect(jsonPath("$.carryOvers[0].seriesId").value(s2.getId()))
                    .andExpect(jsonPath("$.carryOvers[0].amount").value(2000.00));
        }
    }

    // ------------------------------------------------------------------
    // L'Encaissement (spec admin-corrections, A.4)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Encaissement enregistré")
    class EncaissementEnregistre {

        @Test
        @DisplayName("un versement : un Encaissement numéroté, porteur du montant, du mode et de la note")
        void versementEnregistreCommeEncaissement() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"studentId\":%s,\"groupId\":%s,\"sessionSeriesId\":%s,"
                                            + "\"amountPaid\":4000,\"paymentMethod\":\"cash\","
                                            + "\"paymentDescription\":\"Réglé par la mère\"}",
                                    student.getId(), group.getId(), s1.getId())))
                    .andExpect(status().isOk());

            assertThat(encashmentRepository.findAll()).singleElement().satisfies(encashment -> {
                assertThat(encashment.getReceiptNumber()).isEqualTo(receipt(1));
                assertThat(encashment.getStatus()).isEqualTo(EncashmentStatus.ACTIVE);
                assertThat(encashment.getKind()).isEqualTo(EncashmentKind.REGULAR);
                assertThat(encashment.getAmountReceived()).isEqualByComparingTo("4000.00");
                assertThat(encashment.getPaymentMethod()).isEqualTo("cash");
                assertThat(encashment.getNotes()).isEqualTo("Réglé par la mère");
                assertThat(encashment.getReceivedBy()).isNotBlank();
            });
            assertThat(allocationRepository.findAll()).singleElement().satisfies(imputation -> {
                assertThat(imputation.getAmount()).isEqualByComparingTo("4000.00");
                assertThat(imputation.getCarriedOver()).isFalse();
            });
            assertThat(cumulOf(s1.getId(), student.getId())).isEqualTo(4000.0);
            // Deux séances facturables à 2 000 DA : le coût au prorata est atteint.
            assertThat(paymentRepository.findAll()).singleElement()
                    .satisfies(line -> assertThat(line.getStatus()).isEqualTo("COMPLETED"));
        }

        @Test
        @DisplayName("versement reporté : un seul Encaissement, deux Imputations, le report désigne la sienne")
        void reportRattacheASonImputation() throws Exception {
            SessionSeriesEntity s2 = persistSeries(group, "Série 2", date(2030, 2, 4), date(2030, 2, 11));

            pay(s1.getId(), 6000, "cle-report").andExpect(status().isOk());

            assertThat(encashmentRepository.count()).isEqualTo(1);
            assertThat(allocationRepository.findAll())
                    .extracting(a -> a.getSeries().getId(), a -> a.getAmount().toPlainString(), a -> a.getCarriedOver())
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(s1.getId(), "4000.00", false),
                            org.assertj.core.groups.Tuple.tuple(s2.getId(), "2000.00", true));
            Long carriedImputationId = allocationRepository.findAll().stream()
                    .filter(a -> Boolean.TRUE.equals(a.getCarriedOver()))
                    .findFirst().orElseThrow().getId();
            assertThat(carryOverRepository.findAll()).singleElement()
                    .satisfies(carryOver -> assertThat(carryOver.getEncashmentAllocation().getId())
                            .isEqualTo(carriedImputationId));
            assertThat(idempotencyRepository.findAll()).singleElement()
                    .satisfies(record -> assertThat(record.getEncashment()).isNotNull());
            assertThat(cumulOf(s1.getId(), student.getId())).isEqualTo(4000.0);
            assertThat(cumulOf(s2.getId(), student.getId())).isEqualTo(2000.0);
        }

        @Test
        @DisplayName("deux versements : deux Encaissements aux numéros consécutifs, cumul = leur somme")
        void deuxVersementsDeuxRecus() throws Exception {
            pay(s1.getId(), 1500).andExpect(status().isOk());
            pay(s1.getId(), 2500).andExpect(status().isOk());

            assertThat(encashmentRepository.findAll())
                    .extracting(EncashmentEntity::getReceiptNumber)
                    .containsExactlyInAnyOrder(receipt(1), receipt(2));
            assertThat(cumulOf(s1.getId(), student.getId())).isEqualTo(4000.0);
        }

        @Test
        @DisplayName("versement refusé : aucun Encaissement, aucun numéro consommé")
        void refusNeConsommeAucunNumero() throws Exception {
            pay(s1.getId(), 6000).andExpect(status().isBadRequest());
            assertThat(encashmentRepository.count()).isZero();

            pay(s1.getId(), 2000).andExpect(status().isOk());
            assertThat(encashmentRepository.findAll()).singleElement()
                    .satisfies(encashment -> assertThat(encashment.getReceiptNumber()).isEqualTo(receipt(1)));
        }

        @Test
        @DisplayName("deux Encaissements sur une séance : une ligne chacun, à son Imputation et à sa date ; "
                + "annuler l'un laisse l'autre intact")
        void uneLigneParEncaissementEtParSeance() throws Exception {
            pay(s1.getId(), 1000).andExpect(status().isOk());
            pay(s1.getId(), 3000).andExpect(status().isOk());

            // Séance 1 : 1 000 DA du premier versement, puis 1 000 DA du second, en deux lignes.
            // Avant A.5, la ligne du premier était complétée par le second : elle mêlait les deux.
            List<PaymentDetailEntity> lines = paymentDetailRepository.findAll();
            assertThat(lines).hasSize(3).allSatisfy(line -> assertThat(line.getEncashmentAllocation()).isNotNull());

            for (EncashmentAllocationEntity imputation : allocationRepository.findAll()) {
                Date receivedAt = encashmentRepository.findById(imputation.getEncashment().getId())
                        .orElseThrow().getReceivedAt();
                List<PaymentDetailEntity> own = lines.stream()
                        .filter(line -> line.getEncashmentAllocation().getId().equals(imputation.getId()))
                        .toList();
                assertThat(own.stream().mapToDouble(PaymentDetailEntity::getAmountPaid).sum())
                        .as("la ventilation d'une Imputation égale son montant")
                        .isEqualTo(imputation.getAmount().doubleValue());
                assertThat(own).allSatisfy(line ->
                        assertThat(line.getPaymentDate().getTime()).isEqualTo(receivedAt.getTime()));
            }

            EncashmentEntity first = encashmentRepository.findAll().stream()
                    .filter(encashment -> encashment.getReceiptNumber().equals(receipt(1)))
                    .findFirst().orElseThrow();
            encashmentService.neutralize(first.getId(), CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT));

            assertThat(paymentDetailRepository.findAll())
                    .filteredOn(line -> Boolean.TRUE.equals(line.getActive()))
                    .extracting(PaymentDetailEntity::getAmountPaid)
                    .containsExactlyInAnyOrder(1000.0, 2000.0);
            assertThat(cumulOf(s1.getId(), student.getId())).isEqualTo(3000.0);
        }

        @Test
        @DisplayName("défaut 2 : modifier, supprimer ou réactiver une ligne est refusé (409, reçu nommé), rien ne bouge")
        void uneLigneNeSeCorrigePasALUnite() throws Exception {
            pay(s1.getId(), 4000).andExpect(status().isOk());
            Long lineId = paymentDetailRepository.findAll().get(0).getId();
            Ledger before = ledger();

            // Avant A.6, ramener une ligne à 500 DA et supprimer l'autre faisait tomber le cumul à
            // 500 DA et annulait la série : 3 500 DA encaissés disparaissaient du registre.
            mockMvc.perform(patch("/api/payment-details/" + lineId).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":500,\"reason\":\"saisie corrigée\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", allOf(containsString(receipt(1)),
                            containsString("annulez ou corrigez le reçu"))));
            mockMvc.perform(delete("/api/payment-details/" + lineId).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"ligne en double\"}"))
                    .andExpect(status().isConflict());
            mockMvc.perform(post("/api/payment-details/" + lineId + "/reactivate").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"erreur\"}"))
                    .andExpect(status().isConflict());

            assertThat(ledger()).isEqualTo(before);
            assertThat(paymentDetailRepository.findAll())
                    .allSatisfy(line -> assertThat(line.getActive()).isTrue())
                    .extracting(PaymentDetailEntity::getAmountPaid).containsOnly(2000.0);
            assertThat(cumulOf(s1.getId(), student.getId())).isEqualTo(4000.0);
        }

        @Test
        @DisplayName("Encaissement annulé : le cumul retombe à la somme des Encaissements restants")
        void cumulDeriveDesEncaissements() throws Exception {
            pay(s1.getId(), 1500).andExpect(status().isOk());
            pay(s1.getId(), 2500).andExpect(status().isOk());
            EncashmentEntity first = encashmentRepository.findAll().stream()
                    .filter(encashment -> encashment.getReceiptNumber().equals(receipt(1)))
                    .findFirst().orElseThrow();

            encashmentService.neutralize(first.getId(), CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT));

            // Un cumul incrémenté resterait à 4 000 DA : il serait faux dès la première annulation.
            assertThat(cumulOf(s1.getId(), student.getId())).isEqualTo(2500.0);
            // Et la place libérée peut être encaissée de nouveau.
            pay(s1.getId(), 1500).andExpect(status().isOk());
            assertThat(cumulOf(s1.getId(), student.getId())).isEqualTo(4000.0);
        }
    }

    // ------------------------------------------------------------------
    // Rattrapage : même Encaissement, même clé (exigence 1.7)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/payments/process/catch-up")
    class Rattrapage {

        private static final String CATCH_UP_URL = "/api/payments/process/catch-up";

        private StudentEntity lina;
        private SessionEntity firstSession;
        private SessionEntity secondSession;

        /**
         * Lina n'est pas inscrite au groupe. Elle est venue en rattrapage à la première séance de
         * la série, facturée sur place (aucun groupe de même niveau et même matière ne lui réserve
         * de place) : elle doit cette séance, et elle seule.
         */
        @BeforeEach
        void givenAHostBilledCatchUp() {
            lina = studentRepository.save(StudentEntity.builder().firstName("Lina").lastName("Hamdani").build());
            List<SessionEntity> sessions = sessionRepository.findAll().stream()
                    .filter(session -> session.getSessionSeries().getId().equals(s1.getId()))
                    .sorted(java.util.Comparator.comparing(SessionEntity::getSessionTimeStart))
                    .toList();
            firstSession = sessions.get(0);
            secondSession = sessions.get(1);
            attend(firstSession, CatchUpBillingState.HOST_BILLED);
        }

        private void attend(SessionEntity session, CatchUpBillingState state) {
            attendanceRepository.save(AttendanceEntity.builder()
                    .student(lina).session(session).sessionSeries(s1).group(group)
                    .isPresent(true).isCatchUp(true).catchUpBillingState(state).build());
        }

        private ResultActions payCatchUp(SessionEntity session, Object amount, String key) throws Exception {
            MockHttpServletRequestBuilder request = post(CATCH_UP_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    // groupId est exigé par la validation du PaymentDTO ; le serveur retient
                    // néanmoins le groupe de la séance, seul à faire foi.
                    .content(String.format("{\"studentId\":%s,\"groupId\":%s,\"sessionId\":%s,\"amountPaid\":%s,"
                            + "\"paymentMethod\":\"cash\"}", lina.getId(), group.getId(), session.getId(), amount));
            if (key != null) {
                request = request.header("Idempotency-Key", key);
            }
            return mockMvc.perform(request);
        }

        @Test
        @DisplayName("rattrapage accepté : Encaissement CATCH_UP numéroté, cumul et statut au prorata")
        void rattrapageEnregistreCommeEncaissement() throws Exception {
            payCatchUp(firstSession, 2000, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amountPaid").value(2000.0))
                    // Une séance suivie et facturable à 2 000 DA : la série est soldée pour Lina.
                    .andExpect(jsonPath("$.status").value("COMPLETED"));

            assertThat(encashmentRepository.findAll()).singleElement().satisfies(encashment -> {
                assertThat(encashment.getKind()).isEqualTo(EncashmentKind.CATCH_UP);
                assertThat(encashment.getReceiptNumber()).isEqualTo(receipt(1));
                assertThat(encashment.getTargetSeries().getId()).isEqualTo(s1.getId());
                assertThat(encashment.getPaymentMethod()).isEqualTo("cash");
            });
            assertThat(cumulOf(s1.getId(), lina.getId())).isEqualTo(2000.0);
            assertThat(carryOverRepository.count()).as("un rattrapage ne se reporte pas").isZero();
        }

        @Test
        @DisplayName("rejeu sous la même clé : même réponse, un seul Encaissement")
        void rejeu() throws Exception {
            payCatchUp(firstSession, 2000, "cle-rattrapage").andExpect(status().isOk());
            Ledger afterFirst = ledger();

            payCatchUp(firstSession, 2000, "cle-rattrapage")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amountPaid").value(2000.0));

            assertThat(ledger()).as("le rejeu n'écrit rien").isEqualTo(afterFirst);
            assertThat(idempotencyRepository.findAll()).singleElement()
                    .satisfies(record -> assertThat(record.getSessionId()).isEqualTo(firstSession.getId()));
        }

        @Test
        @DisplayName("même clé pour une autre séance, ou sur /process : 409, rien n'est écrit")
        void cleReutilisee() throws Exception {
            payCatchUp(firstSession, 1000, "cle-r").andExpect(status().isOk());
            Ledger before = ledger();

            payCatchUp(secondSession, 1000, "cle-r")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("déjà servi")));
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "cle-r")
                            .content(body(lina.getId(), group.getId(), s1.getId(), 1000)))
                    .andExpect(status().isConflict());

            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("au-delà du prix de la séance : 400, rien n'est écrit")
        void auDelaDuPrixDeLaSeance() throws Exception {
            Ledger before = ledger();

            payCatchUp(firstSession, 2500, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("dépasse le coût de la séance")));

            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("au-delà de ce qui reste dû : 400 avec le maximum, rien n'est écrit")
        void auDelaDuResteDu() throws Exception {
            payCatchUp(firstSession, 1500, null).andExpect(status().isOk());
            Ledger before = ledger();

            payCatchUp(firstSession, 1000, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", allOf(
                            containsString("au maximum 500.00 DA"),
                            containsString("ne se reporte pas"))));

            assertThat(ledger()).isEqualTo(before);
            assertThat(cumulOf(s1.getId(), lina.getId())).isEqualTo(1500.0);
        }

        @Test
        @DisplayName("rattrapage « à préciser » : rien à encaisser, 400, rien n'est écrit")
        void rattrapageAPreciser() throws Exception {
            // Avant A.4, ce chemin encaissait jusqu'au prix de la séance un rattrapage qui ne
            // facture rien tant qu'il n'est pas tranché : un crédit que rien n'expliquait.
            attendanceRepository.deleteAll();
            attend(firstSession, CatchUpBillingState.PENDING);
            Ledger before = ledger();

            payCatchUp(firstSession, 2000, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Rien à encaisser pour ce rattrapage")));

            assertThat(ledger()).isEqualTo(before);
        }
    }

    // ------------------------------------------------------------------
    // Refus du surplus : l'argent en main
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Refus en totalité, sans aucune écriture")
    class RefusEnTotalite {

        @Test
        @DisplayName("B.3 série suivante sans séance : 400, maximum encaissable et action corrective")
        void serieSuivanteVide() throws Exception {
            persistSeries(group, "Série 2");
            Ledger before = ledger();

            pay(s1.getId(), 6000)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", allOf(
                            containsString("refusé en totalité"),
                            containsString("4000.00 DA"),
                            containsString("aucune séance"),
                            containsString("Série 2"))));

            assertThat(ledger()).as("un refus ne laisse aucune écriture partielle").isEqualTo(before);
        }

        @Test
        @DisplayName("B.4 séances existantes mais aucune facturable : motif distinct de la série vide")
        void serieSuivanteNonFacturable() throws Exception {
            // Séances de 2020, antérieures à l'inscription, sans aucune présence : écartées.
            // Créer une séance de plus ne rouvrirait rien, le message ne doit pas le conseiller.
            persistSeries(group, "Série 2", date(2020, 2, 3), date(2020, 2, 10));
            Ledger before = ledger();

            pay(s1.getId(), 6000)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", allOf(
                            containsString("aucune n'est facturable"),
                            containsString("Créer des séances supplémentaires n'y changerait rien"),
                            not(containsString("créez d'abord les séances")))));

            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("B.6 aucune série au-delà de la série visée : 400, sans série bloquante nommée")
        void aucuneSerieSuivante() throws Exception {
            Ledger before = ledger();

            pay(s1.getId(), 6000)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", allOf(
                            containsString("Aucune série ne peut recevoir le reliquat de 2000.00 DA"),
                            containsString("ramenez le montant à 4000.00 DA"))));

            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("un refus n'empêche pas l'encaissement du maximum annoncé juste après")
        void maximumAnnonceEncaissable() throws Exception {
            persistSeries(group, "Série 2");
            pay(s1.getId(), 6000).andExpect(status().isBadRequest());

            // Le message a annoncé 4000.00 DA : ce montant doit effectivement passer.
            pay(s1.getId(), 4000).andExpect(status().isOk());
            assertThat(paymentRepository.count()).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------
    // Requêtes invalides
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Requête invalide")
    class RequeteInvalide {

        @Test
        @DisplayName("B.E1 montant nul : 400, rien n'est écrit")
        void montantNul() throws Exception {
            Ledger before = ledger();
            pay(s1.getId(), 0).andExpect(status().isBadRequest());
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("B.E1 montant négatif : 400, rien n'est écrit")
        void montantNegatif() throws Exception {
            Ledger before = ledger();
            pay(s1.getId(), -500).andExpect(status().isBadRequest());
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("B.E2 série d'un autre groupe : refus, rien n'est écrit")
        void serieHorsGroupe() throws Exception {
            GroupEntity other = persistGroup("Physique 2ème", currentYear);
            SessionSeriesEntity foreign = persistSeries(other, "Série étrangère", date(2030, 1, 7));
            Ledger before = ledger();

            pay(foreign.getId(), 2000).andExpect(status().is4xxClientError());

            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("B.E5 étudiant inexistant : 404, comme l'annonce le contrat du service")
        void etudiantInexistant() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(body(999_999L, group.getId(), s1.getId(), 2000)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("B.E5 groupe inexistant : 404")
        void groupeInexistant() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(body(student.getId(), 999_999L, s1.getId(), 2000)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("B.E5 série inexistante : 404")
        void serieInexistante() throws Exception {
            pay(999_999L, 2000).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------
    // Année scolaire close
    // ------------------------------------------------------------------

    @Test
    @DisplayName("B.E3 groupe d'une année passée : 409 lecture seule, rien n'est écrit (school-year 9.2)")
    void anneePassee() throws Exception {
        SchoolYearEntity past = schoolYearRepository.save(SchoolYearEntity.builder()
                .label("2028-2029").startDate(date(2028, 9, 1)).endDate(date(2029, 6, 30))
                .isCurrent(false).build());
        GroupEntity pastGroup = persistGroup("Math 1ère A — 2028", past);
        enrol(student, pastGroup);
        SessionSeriesEntity pastSeries = persistSeries(pastGroup, "Série passée", date(2030, 1, 7));
        Ledger before = ledger();

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body(student.getId(), pastGroup.getId(), pastSeries.getId(), 2000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("lecture seule")));

        assertThat(ledger()).isEqualTo(before);
    }

    // ------------------------------------------------------------------
    // Idempotence par l'en-tête HTTP
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("En-tête Idempotency-Key")
    class Idempotence {

        @Test
        @DisplayName("rejeu : même réponse, un seul versement au registre")
        void rejeu() throws Exception {
            pay(s1.getId(), 2000, "cle-1").andExpect(status().isOk());
            Ledger afterFirst = ledger();

            pay(s1.getId(), 2000, "cle-1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amountAllocated").value(2000.00));

            assertThat(ledger()).as("le rejeu n'écrit rien").isEqualTo(afterFirst);
            assertThat(paymentRepository.findAll()).singleElement()
                    .satisfies(p -> assertThat(p.getAmountPaid()).isEqualTo(2000.0));
        }

        @Test
        @DisplayName("rejeu d'un versement reporté : les reports sont restitués à l'identique")
        void rejeuAvecReport() throws Exception {
            SessionSeriesEntity s2 = persistSeries(group, "Série 2", date(2030, 2, 4), date(2030, 2, 11));
            pay(s1.getId(), 6000, "cle-report").andExpect(status().isOk());

            pay(s1.getId(), 6000, "cle-report")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amountCarriedOver").value(2000.00))
                    .andExpect(jsonPath("$.carryOvers[0].seriesId").value(s2.getId()));

            assertThat(carryOverRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("clé neuve : second encaissement réel, accepté (paiement par facilité)")
        void cleNeuve() throws Exception {
            pay(s1.getId(), 2000, "cle-matin").andExpect(status().isOk());
            pay(s1.getId(), 2000, "cle-apres-midi").andExpect(status().isOk());

            assertThat(paymentRepository.findAll()).singleElement()
                    .satisfies(p -> assertThat(p.getAmountPaid()).isEqualTo(4000.0));
        }

        @Test
        @DisplayName("clé réutilisée pour un autre montant : 409, rien n'est écrit")
        void cleReutilisee() throws Exception {
            pay(s1.getId(), 2000, "cle-2").andExpect(status().isOk());
            Ledger before = ledger();

            pay(s1.getId(), 4000, "cle-2")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("déjà servi")));

            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("versement refusé : l'empreinte n'est pas conservée, la reprise reste possible")
        void refusNeBloquePasLaReprise() throws Exception {
            pay(s1.getId(), 6000, "cle-3").andExpect(status().isBadRequest());
            assertThat(idempotencyRepository.count()).isZero();

            // Même clé, montant corrigé : la clé n'a jamais abouti, elle ne doit pas être grillée.
            persistSeries(group, "Série 2", date(2030, 2, 4), date(2030, 2, 11));
            pay(s1.getId(), 6000, "cle-3").andExpect(status().isOk());
        }

        @Test
        @DisplayName("clé vide : 400")
        void cleVide() throws Exception {
            pay(s1.getId(), 2000, "   ").andExpect(status().isBadRequest());
            assertThat(paymentRepository.count()).isZero();
        }
    }
}
