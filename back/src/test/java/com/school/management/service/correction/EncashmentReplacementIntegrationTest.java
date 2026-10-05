package com.school.management.service.correction;

import com.school.management.dto.RefundRequestDTO;
import com.school.management.dto.payment.EncashmentDTO;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.service.RefundService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import com.school.management.service.payment.EncashmentQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Corriger un Encaissement (spec admin-corrections, exigences 3.1 à 3.6, D4).
 *
 * <p>Un montant, un élève, un groupe ou une Série faux : l'original est annulé et le remplacement
 * encaissé par le chemin ordinaire, dans la même transaction, les deux reliés. Un mode de paiement
 * ou une note faux : corrigés en place, sans nouveau reçu. Les états sont relus en SQL.</p>
 */
@DisplayName("Corriger un Encaissement")
class EncashmentReplacementIntegrationTest extends CorrectionIntegrationTestSupport {

    private static final CorrectionReason WRONG_AMOUNT = CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT);
    private static final CorrectionReason WRONG_STUDENT = CorrectionReason.of(CorrectionReasonType.WRONG_STUDENT);
    private static final CorrectionReason ENTRY_ERROR = CorrectionReason.of(CorrectionReasonType.DATA_ENTRY_ERROR);

    @Autowired private EncashmentCorrectionService corrections;
    @Autowired private EncashmentQueryService queries;
    @Autowired private RefundService refundService;

    private StudentEntity lina;
    private GroupEntity physics;
    private SessionSeriesEntity physicsJanuary;

    /** Une deuxième élève dans le groupe, et un deuxième groupe où Amine est aussi inscrit. */
    @BeforeEach
    void setUpReplacementData() {
        lina = studentRepository.save(StudentEntity.builder().firstName("Lina").lastName("Haddad").build());
        studentGroupRepository.save(StudentGroupEntity.builder().student(lina).group(group).build());
        PricingEntity pricing = pricingRepository.save(PricingEntity.builder().price(PRICE).build());
        physics = groupRepository.save(GroupEntity.builder()
                .name("Physique 1ère A").price(pricing).schoolYear(year).sessionNumberPerSerie(2).build());
        studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(physics).build());
        physicsJanuary = persistSeries(physics, "Physique Janvier", date(2030, 1, 8), date(2030, 1, 15));
    }

    // ------------------------------------------------------------------
    // Remplacement
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Remplacement")
    class Remplacement {

        @Test
        @DisplayName("un montant faux : l'Aperçu annonce l'annulation et le remplacement, sans rien écrire")
        void wrongAmountPreview() {
            // 5 000 sur Janvier (coût 4 000) : 4 000 imputés, 1 000 reportés sur Février.
            Long id = pay(s1, 5000).encashment().getId();
            Ledger before = ledger();

            CorrectionOutcome<EncashmentCorrection> preview = corrections.correct(id, sameAs(id, "2000"),
                    WRONG_AMOUNT, CorrectionMode.PREVIEW, null);

            assertThat(preview.result()).isNull();
            assertThat(preview.preview().series()).extracting(SeriesAmountChange::seriesId, c -> c.after().paid())
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(s1.getId(), new BigDecimal("2000.00")),
                            org.assertj.core.groups.Tuple.tuple(s2.getId(), new BigDecimal("0.00")));
            assertThat(preview.preview().effects()).extracting(CorrectionEffect::description).containsExactly(
                    "Reçu " + receiptOf(id) + " de 5 000,00 DA annulé",
                    "Imputation de 4 000,00 DA sur « Janvier » neutralisée",
                    "Report de 1 000,00 DA sur « Février » neutralisé",
                    "Versement de remplacement de 2 000,00 DA sur « Janvier » (Math 1ère A), au nom de Amine Belkacem",
                    "Imputation de 2 000,00 DA sur « Janvier »");
            assertThat(ledger()).as("rien d'écrit, aucun numéro consommé").isEqualTo(before);
        }

        @Test
        @DisplayName("la confirmation annule l'original, encaisse le remplacement, relie les deux et trace")
        void confirmationReplacesAndLinks() {
            Long id = pay(s1, 5000).encashment().getId();
            String original = receiptOf(id);

            EncashmentCorrection done = confirm(id, sameAs(id, "2000"), WRONG_AMOUNT).result();

            EncashmentDTO replacement = done.replacement();
            assertThat(replacement.status()).isEqualTo("ACTIVE");
            assertThat(replacement.amountReceived()).isEqualByComparingTo("2000");
            assertThat(replacement.receiptNumber()).isNotEqualTo(original);
            assertThat(replacement.replacesReceiptNumber()).isEqualTo(original);
            assertThat(done.original().status()).isEqualTo("CANCELLED");
            assertThat(done.original().replacedByReceiptNumber()).isEqualTo(replacement.receiptNumber());
            // L'historique de l'élève montre les deux, reliés (3.3).
            assertThat(queries.forStudent(student.getId()))
                    .extracting(EncashmentDTO::receiptNumber, EncashmentDTO::replacesReceiptNumber,
                            EncashmentDTO::replacedByReceiptNumber)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(replacement.receiptNumber(), original, null),
                            org.assertj.core.groups.Tuple.tuple(original, null, replacement.receiptNumber()));
            // Montants : exactement ceux d'un versement de 2 000 saisi directement.
            assertThat(cumulOf(s1)).isEqualByComparingTo("2000");
            assertThat(cumulOf(s2)).isEqualByComparingTo("0");
            assertThat(count("SELECT COUNT(*) FROM payment_carry_over WHERE active = TRUE")).isZero();
            assertThat(jdbc.queryForMap("SELECT action, summary FROM correction_audit")).satisfies(row -> {
                assertThat(row.get("ACTION")).isEqualTo("ENCASHMENT_REPLACED");
                assertThat(row.get("SUMMARY")).isEqualTo("Reçu " + original + " de 5 000,00 DA annulé, remplacé par "
                        + replacement.receiptNumber() + " de 2 000,00 DA");
            });
        }

        @Test
        @DisplayName("un remplacement qui dépasse la Série visée se reporte, comme un versement ordinaire")
        void aLargerReplacementCarriesOver() {
            Long id = pay(s1, 3000).encashment().getId();

            CorrectionOutcome<EncashmentCorrection> preview = corrections.correct(id, sameAs(id, "5000"),
                    WRONG_AMOUNT, CorrectionMode.PREVIEW, null);

            assertThat(preview.preview().effects()).extracting(CorrectionEffect::description).endsWith(
                    "Imputation de 4 000,00 DA sur « Janvier »",
                    "Report de 1 000,00 DA sur « Février »");
            corrections.correct(id, sameAs(id, "5000"), WRONG_AMOUNT, CorrectionMode.CONFIRM, preview.previewToken());
            assertThat(cumulOf(s1)).isEqualByComparingTo("4000");
            assertThat(cumulOf(s2)).isEqualByComparingTo("1000");
        }

        @Test
        @DisplayName("un élève faux : l'argent change d'élève, et chacun en garde la trace")
        void wrongStudent() {
            Long id = pay(s1, 3000).encashment().getId();
            String original = receiptOf(id);

            CorrectionOutcome<EncashmentCorrection> preview = corrections.correct(id,
                    changes(id, "3000", lina.getId(), group.getId(), s1.getId()), WRONG_STUDENT,
                    CorrectionMode.PREVIEW, null);
            assertThat(preview.preview().series()).extracting(SeriesAmountChange::studentName,
                            c -> c.before().paid(), c -> c.after().paid())
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("Amine Belkacem", new BigDecimal("3000.00"), new BigDecimal("0.00")),
                            org.assertj.core.groups.Tuple.tuple("Lina Haddad", new BigDecimal("0.00"), new BigDecimal("3000.00")));

            EncashmentCorrection done = corrections.correct(id, changes(id, "3000", lina.getId(), group.getId(), s1.getId()),
                    WRONG_STUDENT, CorrectionMode.CONFIRM, preview.previewToken()).result();

            assertThat(done.replacement().studentName()).isEqualTo("Lina Haddad");
            List<Map<String, Object>> traces = jdbc.queryForList(
                    "SELECT student_id, summary, amount_effect FROM correction_audit ORDER BY id");
            assertThat(traces).hasSize(2);
            assertThat(traces.get(0).get("SUMMARY")).isEqualTo("Reçu " + original + " de 3 000,00 DA annulé, remplacé par "
                    + done.replacement().receiptNumber() + " de 3 000,00 DA (élève Amine Belkacem → Lina Haddad)");
            assertThat((String) traces.get(0).get("AMOUNT_EFFECT")).as("Journal d'Amine : son argent seulement")
                    .contains("versé 3 000,00 → 0,00 DA").doesNotContain("0,00 → 3 000,00");
            assertThat(((Number) traces.get(1).get("STUDENT_ID")).longValue()).isEqualTo(lina.getId());
            assertThat(traces.get(1).get("SUMMARY")).isEqualTo("Reçu " + done.replacement().receiptNumber()
                    + " de 3 000,00 DA, en remplacement du reçu " + original + " saisi au nom de Amine Belkacem");
            assertThat((String) traces.get(1).get("AMOUNT_EFFECT")).contains("versé 0,00 → 3 000,00 DA");
        }

        @Test
        @DisplayName("une Série fausse, puis un groupe faux, sont nommés dans la trace")
        void wrongSeriesThenWrongGroup() {
            Long id = pay(s1, 3000).encashment().getId();

            EncashmentCorrection toFebruary = confirm(id, changes(id, "3000", student.getId(), group.getId(), s2.getId()),
                    ENTRY_ERROR).result();
            assertThat(cumulOf(s1)).isEqualByComparingTo("0");
            assertThat(cumulOf(s2)).isEqualByComparingTo("3000");

            Long febId = toFebruary.replacement().id();
            confirm(febId, changes(febId, "3000", student.getId(), physics.getId(), physicsJanuary.getId()), ENTRY_ERROR);
            assertThat(cumulOf(s2)).isEqualByComparingTo("0");
            assertThat(cumulOf(physicsJanuary)).isEqualByComparingTo("3000");

            assertThat(jdbc.queryForList("SELECT summary FROM correction_audit ORDER BY id", String.class))
                    .satisfiesExactly(
                            first -> assertThat(first).endsWith("(série « Janvier » → « Février »)"),
                            second -> assertThat(second).endsWith(
                                    "(groupe « Math 1ère A » → « Physique 1ère A », série « Février » → « Physique Janvier »)"));
        }

        @Test
        @DisplayName("montant et note faux ensemble : un remplacement, qui porte la note corrigée")
        void amountAndNoteTogether() {
            Long id = pay(s1, 3000).encashment().getId();
            jdbc.update("UPDATE encashment SET notes = 'Ancienne' WHERE id = ?", id);

            EncashmentCorrection done = confirm(id, new EncashmentChanges(new BigDecimal("2000"), student.getId(),
                    group.getId(), s1.getId(), "cheque", "Corrigée"), WRONG_AMOUNT).result();

            assertThat(done.replacement()).isNotNull();
            assertThat(done.replacement().notes()).isEqualTo("Corrigée");
            assertThat(done.replacement().paymentMethod()).isEqualTo("cheque");
            assertThat(done.original().notes()).as("l'original reste tel qu'il a été saisi").isEqualTo("Ancienne");
        }

        @Test
        @DisplayName("un groupe changé sans sa Série est refusé par l'encaissement, pas pris pour « rien »")
        void aGroupWithoutItsSeriesIsRefusedByTheEncashment() {
            Long id = pay(s1, 3000).encashment().getId();

            CustomServiceException refused = refusal(() -> corrections.correct(id,
                    changes(id, "3000", student.getId(), physics.getId(), s1.getId()), ENTRY_ERROR,
                    CorrectionMode.PREVIEW, null));

            assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(refused.getMessage()).doesNotContain("sans changement effectif");
            assertThat(statusOf(id)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("un remplacement refusé par les règles d'encaissement n'annule rien")
        void aRefusedReplacementCancelsNothing() {
            Long id = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            // 20 000 : au-delà de tout ce que les Séries ouvertes peuvent recevoir.
            CustomServiceException refused = refusal(() -> corrections.correct(id, sameAs(id, "20000"),
                    WRONG_AMOUNT, CorrectionMode.PREVIEW, null));

            assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ledger()).isEqualTo(before);
            assertThat(statusOf(id)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("un remplacement vers un élève non inscrit est refusé, sans rien écrire")
        void aReplacementForAnUnenrolledStudentIsRefused() {
            Long id = pay(s1, 3000).encashment().getId();
            StudentEntity stranger = studentRepository.save(StudentEntity.builder().firstName("Sami").lastName("Kaci").build());
            Ledger before = ledger();

            CustomServiceException refused = refusal(() -> corrections.correct(id,
                    changes(id, "3000", stranger.getId(), group.getId(), s1.getId()), WRONG_STUDENT,
                    CorrectionMode.PREVIEW, null));

            assertThat(refused.getStatus()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT);
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("le plancher des remboursements se juge sur l'état final")
        void theRefundFloorIsJudgedOnTheFinalState() {
            Long id = pay(s1, 3000).encashment().getId();
            refund(2000);

            // Vers Février : Janvier tomberait à 0, sous les 2 000 remboursés.
            RefundFloorException refused = catchThrowableOfType(() -> corrections.correct(id,
                    changes(id, "3000", student.getId(), group.getId(), s2.getId()), ENTRY_ERROR,
                    CorrectionMode.PREVIEW, null), RefundFloorException.class);
            assertThat(refused).isNotNull();

            // 2 500 sur Janvier : l'annulation retire 3 000, le remplacement en rend 2 500.
            confirm(id, sameAs(id, "2500"), WRONG_AMOUNT);
            assertThat(cumulOf(s1)).isEqualByComparingTo("2500");
        }

        @Test
        @DisplayName("un versement de rattrapage ne se remplace pas")
        void aCatchUpPaymentIsNotReplaced() {
            Long id = pay(s1, 1000).encashment().getId();
            jdbc.update("UPDATE encashment SET kind = 'CATCH_UP' WHERE id = ?", id);
            Ledger before = ledger();

            CustomServiceException refused = refusal(() -> corrections.correct(id, sameAs(id, "1500"),
                    WRONG_AMOUNT, CorrectionMode.PREVIEW, null));

            assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(refused.getMessage()).contains("rattrapage").contains("Annulez-le");
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("un reçu déjà remplacé ne se corrige plus, et le refus nomme son remplacement")
        void anAlreadyReplacedReceiptIsRefused() {
            Long id = pay(s1, 3000).encashment().getId();
            String replacement = confirm(id, sameAs(id, "2000"), WRONG_AMOUNT).result().replacement().receiptNumber();

            CustomServiceException refused = refusal(() -> corrections.correct(id, sameAs(id, "1000"),
                    WRONG_AMOUNT, CorrectionMode.PREVIEW, null));

            assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refused.getMessage()).endsWith(", remplacé par " + replacement + ".");
        }

        @Test
        @DisplayName("un reçu d'une année close ne se corrige plus")
        void aClosedYearIsRefused() {
            Long id = pay(s1, 3000).encashment().getId();
            year.setIsCurrent(false);
            schoolYearRepository.save(year);
            schoolYearRepository.save(SchoolYearEntity.builder().label("2030-2031")
                    .startDate(date(2030, 9, 1)).endDate(date(2031, 6, 30)).isCurrent(true).build());

            assertThat(refusal(() -> corrections.correct(id, sameAs(id, "2000"), WRONG_AMOUNT,
                    CorrectionMode.PREVIEW, null))).isInstanceOf(ReadOnlySchoolYearException.class);
        }

        @Test
        @DisplayName("un jeton obtenu pour une correction ne confirme pas une autre correction")
        void theTokenIsBoundToTheChanges() {
            Long id = pay(s1, 3000).encashment().getId();
            String token = corrections.correct(id, sameAs(id, "2000"), WRONG_AMOUNT, CorrectionMode.PREVIEW, null)
                    .previewToken();

            assertThat(catchThrowableOfType(() -> corrections.correct(id, sameAs(id, "2500"), WRONG_AMOUNT,
                    CorrectionMode.CONFIRM, token), StalePreviewException.class)).isNotNull();
            assertThat(statusOf(id)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("le mode et la note du remplacement sont liés au jeton, bien qu'aucun montant n'en dépende")
        void theTokenIsBoundToTheMethodAndTheNote() {
            Long id = pay(s1, 3000).encashment().getId();
            EncashmentChanges read = new EncashmentChanges(new BigDecimal("2000"), student.getId(), group.getId(),
                    s1.getId(), "cash", "Lue");
            String token = corrections.correct(id, read, WRONG_AMOUNT, CorrectionMode.PREVIEW, null).previewToken();

            for (EncashmentChanges other : List.of(
                    new EncashmentChanges(new BigDecimal("2000"), student.getId(), group.getId(), s1.getId(), "cheque", "Lue"),
                    new EncashmentChanges(new BigDecimal("2000"), student.getId(), group.getId(), s1.getId(), "cash", "Autre"))) {
                assertThat(catchThrowableOfType(() -> corrections.correct(id, other, WRONG_AMOUNT,
                        CorrectionMode.CONFIRM, token), StalePreviewException.class)).as(other.toString()).isNotNull();
            }
            assertThat(statusOf(id)).isEqualTo("ACTIVE");
        }
    }

    // ------------------------------------------------------------------
    // Mode de paiement et note
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Mode de paiement et note")
    class ModeEtNote {

        @Test
        @DisplayName("corrigés en place : aucun montant, aucun nouveau reçu, une trace")
        void editedInPlace() {
            Long id = pay(s1, 3000).encashment().getId();
            String receipt = receiptOf(id);
            long rank = lastRank();
            EncashmentChanges changes = new EncashmentChanges(new BigDecimal("3000"), student.getId(), group.getId(),
                    s1.getId(), "cheque", "  Versé par le père  ");

            CorrectionOutcome<EncashmentCorrection> preview =
                    corrections.correct(id, changes, ENTRY_ERROR, CorrectionMode.PREVIEW, null);
            assertThat(preview.preview().amountsUnchanged()).isTrue();
            assertThat(preview.preview().effects()).extracting(CorrectionEffect::description).containsExactly(
                    "Reçu " + receipt + " : mode de paiement aucun → chèque, note aucune → « Versé par le père »");

            EncashmentCorrection done = corrections.correct(id, changes, ENTRY_ERROR, CorrectionMode.CONFIRM,
                    preview.previewToken()).result();

            assertThat(done.replacement()).isNull();
            assertThat(done.original().status()).isEqualTo("ACTIVE");
            assertThat(done.original().paymentMethod()).isEqualTo("cheque");
            assertThat(done.original().notes()).isEqualTo("Versé par le père");
            assertThat(lastRank()).as("aucun nouveau reçu").isEqualTo(rank);
            assertThat(cumulOf(s1)).isEqualByComparingTo("3000");
            assertThat(jdbc.queryForMap("SELECT action, summary, amount_effect, old_value, new_value FROM correction_audit"))
                    .satisfies(row -> {
                        assertThat(row.get("ACTION")).isEqualTo("ENCASHMENT_DETAILS_EDITED");
                        assertThat(row.get("SUMMARY")).isEqualTo("Reçu " + receipt
                                + " : mode de paiement aucun → chèque, note aucune → « Versé par le père »");
                        assertThat(row.get("AMOUNT_EFFECT")).isNull();
                        assertThat(row.get("OLD_VALUE")).isEqualTo("{\"notes\":null,\"paymentMethod\":null}");
                        assertThat(row.get("NEW_VALUE")).isEqualTo("{\"notes\":\"Versé par le père\",\"paymentMethod\":\"cheque\"}");
                    });
        }

        @Test
        @DisplayName("un seul des deux peut changer ; un mode inconnu est cité tel quel")
        void oneOfTheTwoAndAnUnknownMethod() {
            Long id = pay(s1, 3000).encashment().getId();
            jdbc.update("UPDATE encashment SET payment_method = 'cash', notes = 'Ancienne' WHERE id = ?", id);

            confirm(id, new EncashmentChanges(new BigDecimal("3000"), student.getId(), group.getId(), s1.getId(),
                    "virement", "Ancienne"), ENTRY_ERROR);
            confirm(id, new EncashmentChanges(new BigDecimal("3000"), student.getId(), group.getId(), s1.getId(),
                    "virement", null), ENTRY_ERROR);

            assertThat(jdbc.queryForList("SELECT summary FROM correction_audit ORDER BY id", String.class))
                    .containsExactly(
                            "Reçu " + receiptOf(id) + " : mode de paiement espèces → virement",
                            "Reçu " + receiptOf(id) + " : note « Ancienne » → aucune");
        }

        @Test
        @DisplayName("rien de changé — espaces compris — est refusé")
        void nothingChangedIsRefused() {
            Long id = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            CustomServiceException refused = refusal(() -> corrections.correct(id,
                    new EncashmentChanges(new BigDecimal("3000.00"), student.getId(), group.getId(), s1.getId(), " ", ""),
                    ENTRY_ERROR, CorrectionMode.PREVIEW, null));

            assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(refused.getMessage()).contains("sans changement effectif");
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("un mode de paiement trop long est refusé")
        void aTooLongMethodIsRefused() {
            Long id = pay(s1, 3000).encashment().getId();

            assertThat(refusal(() -> corrections.correct(id, new EncashmentChanges(new BigDecimal("3000"),
                    student.getId(), group.getId(), s1.getId(), "x".repeat(51), null), ENTRY_ERROR,
                    CorrectionMode.PREVIEW, null)).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ------------------------------------------------------------------
    // Entrées
    // ------------------------------------------------------------------

    @Test
    @DisplayName("une correction incomplète ou au Motif sans rapport est refusée avant tout")
    void incompleteOrUnrelatedIsRefused() {
        Long id = pay(s1, 3000).encashment().getId();

        assertThat(refusal(() -> new EncashmentChanges(null, student.getId(), group.getId(), s1.getId(), null, null))
                .getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        for (Object[] missing : new Object[][] {
                { student.getId(), group.getId(), null }, { student.getId(), null, s1.getId() },
                { null, group.getId(), s1.getId() } }) {
            assertThat(refusal(() -> new EncashmentChanges(BigDecimal.ONE, (Long) missing[0], (Long) missing[1],
                    (Long) missing[2], null, null)).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(refusal(() -> corrections.correct(id, sameAs(id, "2000"),
                CorrectionReason.of(CorrectionReasonType.ARRIVAL_DATE_CORRECTED), CorrectionMode.PREVIEW, null))
                .getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal(() -> corrections.correct(999_999L, sameAs(id, "2000"), WRONG_AMOUNT,
                CorrectionMode.PREVIEW, null)).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private CorrectionOutcome<EncashmentCorrection> confirm(Long id, EncashmentChanges changes, CorrectionReason reason) {
        String token = corrections.correct(id, changes, reason, CorrectionMode.PREVIEW, null).previewToken();
        return corrections.correct(id, changes, reason, CorrectionMode.CONFIRM, token);
    }

    /** Le même Encaissement, à un montant près. */
    private EncashmentChanges sameAs(Long id, String amount) {
        EncashmentDTO current = queries.get(id);
        return new EncashmentChanges(new BigDecimal(amount), current.studentId(), current.groupId(),
                current.targetSeriesId(), current.paymentMethod(), current.notes());
    }

    private EncashmentChanges changes(Long id, String amount, Long studentId, Long groupId, Long seriesId) {
        EncashmentDTO current = queries.get(id);
        return new EncashmentChanges(new BigDecimal(amount), studentId, groupId, seriesId,
                current.paymentMethod(), current.notes());
    }

    private void refund(double amount) {
        PaymentEntity line = paymentRepository.findAll().stream()
                .filter(p -> p.getSessionSeries().getId().equals(s1.getId())).findFirst().orElseThrow();
        refundService.create(new RefundRequestDTO(line.getId(), student.getId(), BigDecimal.valueOf(amount), null,
                "Départ anticipé"));
    }

    private static CustomServiceException refusal(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        return catchThrowableOfType(call, CustomServiceException.class);
    }
}
