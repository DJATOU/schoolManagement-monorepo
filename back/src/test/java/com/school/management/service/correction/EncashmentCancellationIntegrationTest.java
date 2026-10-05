package com.school.management.service.correction;

import com.school.management.dto.RefundRequestDTO;
import com.school.management.dto.payment.EncashmentDTO;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.service.RefundService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Annulation d'un Encaissement (spec admin-corrections, exigences 2.1 à 2.5 et 2.7).
 *
 * <p>Sur une vraie base H2, par le chemin d'encaissement ordinaire : ce qui est vérifié, c'est ce
 * qu'une annulation neutralise — Imputations, reports, ventilation —, ce qu'elle laisse — les autres
 * versements de la Série, l'Encaissement lui-même, marqué —, et chacun de ses refus, sans aucune
 * écriture. Les états sont relus en SQL.</p>
 */
@DisplayName("Annuler un Encaissement")
class EncashmentCancellationIntegrationTest extends CorrectionIntegrationTestSupport {

    private static final CorrectionReason WRONG_AMOUNT =
            new CorrectionReason(CorrectionReasonType.WRONG_AMOUNT, "20 000 tapé au lieu de 2 000");

    @Autowired private EncashmentCorrectionService corrections;
    @Autowired private RefundService refundService;

    // ------------------------------------------------------------------
    // Effets
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Effets")
    class Effets {

        @Test
        @DisplayName("l'Aperçu annonce l'annulation, la Série touchée, et n'écrit rien")
        void previewAnnouncesAndWritesNothing() {
            attend(s1First);
            Long id = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            CorrectionOutcome<EncashmentDTO> preview = corrections.cancel(id, WRONG_AMOUNT, CorrectionMode.PREVIEW, null);

            assertThat(preview.result()).isNull();
            assertThat(preview.preview().series()).singleElement().satisfies(change -> {
                assertThat(change.seriesId()).isEqualTo(s1.getId());
                assertThat(change.before()).isEqualTo(snapshot("4000", "2000", "3000", "1000", false));
                assertThat(change.after()).isEqualTo(snapshot("4000", "2000", "0", "4000", true));
            });
            assertThat(preview.preview().effects()).extracting(CorrectionEffect::description).containsExactly(
                    "Reçu " + receiptOf(id) + " de 3 000,00 DA annulé",
                    "Imputation de 3 000,00 DA sur « Janvier » neutralisée");
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("la confirmation neutralise tout, garde l'Encaissement marqué, et le trace")
        void confirmationNeutralizesKeepsAndTraces() {
            Long id = pay(s1, 3000).encashment().getId();
            String receipt = receiptOf(id);

            CorrectionOutcome<EncashmentDTO> done = confirm(id, WRONG_AMOUNT);

            // Gardé au registre, marqué avec la date, l'auteur et le Motif (2.3).
            assertThat(done.result().status()).isEqualTo("CANCELLED");
            assertThat(done.result().receiptNumber()).isEqualTo(receipt);
            assertThat(jdbc.queryForMap("SELECT status, cancelled_at, cancelled_by, cancel_reason_type, "
                    + "cancel_reason_text FROM encashment WHERE id = ?", id)).satisfies(row -> {
                assertThat(row.get("STATUS")).isEqualTo("CANCELLED");
                assertThat(row.get("CANCELLED_AT")).isNotNull();
                assertThat(row.get("CANCELLED_BY")).isEqualTo(ADMIN);
                assertThat(row.get("CANCEL_REASON_TYPE")).isEqualTo("WRONG_AMOUNT");
                assertThat(row.get("CANCEL_REASON_TEXT")).isEqualTo("20 000 tapé au lieu de 2 000");
            });
            // Neutralisé (2.2) : plus rien d'actif, cumul recalculé.
            assertThat(count("SELECT COUNT(*) FROM encashment_allocation WHERE active = TRUE")).isZero();
            assertThat(count("SELECT COUNT(*) FROM payment_detail WHERE active = TRUE")).isZero();
            assertThat(cumulOf(s1)).isEqualByComparingTo("0");
            // Tracé, en français.
            assertThat(jdbc.queryForMap("SELECT action, summary, reason_type, reason_text, performed_by "
                    + "FROM correction_audit")).satisfies(row -> {
                assertThat(row.get("ACTION")).isEqualTo("ENCASHMENT_CANCELLED");
                assertThat(row.get("SUMMARY")).isEqualTo("Reçu " + receipt + " de 3 000,00 DA annulé (Janvier, Math 1ère A)");
                assertThat(row.get("REASON_TYPE")).isEqualTo("WRONG_AMOUNT");
                assertThat(row.get("REASON_TEXT")).isEqualTo("20 000 tapé au lieu de 2 000");
                assertThat(row.get("PERFORMED_BY")).isEqualTo(ADMIN);
            });
        }

        @Test
        @DisplayName("un versement reporté est neutralisé sur chaque Série, report compris")
        void aCarriedOverPaymentIsNeutralizedEverywhere() {
            // 5 000 sur Janvier (coût 4 000) : 4 000 imputés, 1 000 reportés sur Février.
            Long id = pay(s1, 5000).encashment().getId();
            assertThat(count("SELECT COUNT(*) FROM payment_carry_over WHERE active = TRUE")).isOne();

            CorrectionOutcome<EncashmentDTO> preview = corrections.cancel(id, WRONG_AMOUNT, CorrectionMode.PREVIEW, null);
            assertThat(preview.preview().series()).extracting(SeriesAmountChange::seriesId)
                    .containsExactly(s1.getId(), s2.getId());
            assertThat(preview.preview().effects()).extracting(CorrectionEffect::description).containsExactly(
                    "Reçu " + receiptOf(id) + " de 5 000,00 DA annulé",
                    "Imputation de 4 000,00 DA sur « Janvier » neutralisée",
                    "Report de 1 000,00 DA sur « Février » neutralisé");

            corrections.cancel(id, WRONG_AMOUNT, CorrectionMode.CONFIRM, preview.previewToken());

            assertThat(cumulOf(s1)).isEqualByComparingTo("0");
            assertThat(cumulOf(s2)).isEqualByComparingTo("0");
            assertThat(count("SELECT COUNT(*) FROM payment_carry_over WHERE active = TRUE")).isZero();
            assertThat((String) jdbc.queryForObject("SELECT amount_effect FROM correction_audit", String.class))
                    .contains("Janvier (Math 1ère A) : versé 4 000,00 → 0,00 DA")
                    .contains("Février (Math 1ère A) : versé 1 000,00 → 0,00 DA");
        }

        @Test
        @DisplayName("les autres versements de la Série restent comptés")
        void otherPaymentsOfTheSeriesStay() {
            Long first = pay(s1, 1000).encashment().getId();
            Long second = pay(s1, 2000).encashment().getId();

            confirm(first, WRONG_AMOUNT);

            assertThat(statusOf(second)).isEqualTo("ACTIVE");
            assertThat(cumulOf(s1)).isEqualByComparingTo("2000");
        }
    }

    // ------------------------------------------------------------------
    // Refus
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("une seconde annulation est refusée, en disant quand et par qui la première a eu lieu")
        void aSecondCancellationIsRefused() {
            Long id = pay(s1, 3000).encashment().getId();
            CorrectionOutcome<EncashmentDTO> first = confirm(id, WRONG_AMOUNT);
            Ledger before = ledger();

            CustomServiceException refused = refusal(() ->
                    corrections.cancel(id, WRONG_AMOUNT, CorrectionMode.CONFIRM, first.previewToken()));

            assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refused.getMessage()).isEqualTo("Le reçu " + receiptOf(id) + " est déjà annulé, le "
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " par " + ADMIN + ".");
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("un Encaissement introuvable : 404")
        void unknownEncashment() {
            assertThat(refusal(() -> corrections.cancel(999_999L, WRONG_AMOUNT, CorrectionMode.PREVIEW, null))
                    .getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("un Encaissement d'une année close ne s'annule plus")
        void aClosedYearIsRefused() {
            Long id = pay(s1, 3000).encashment().getId();
            closeTheYear();
            Ledger before = ledger();

            CustomServiceException refused = refusal(() ->
                    corrections.cancel(id, WRONG_AMOUNT, CorrectionMode.PREVIEW, null));

            assertThat(refused).isInstanceOf(ReadOnlySchoolYearException.class);
            assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refused.getMessage()).contains(receiptOf(id)).contains("année scolaire close");
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("passer sous le remboursé est refusé, en nommant le remboursement")
        void goingBelowTheRefundsIsRefused() {
            Long id = pay(s1, 3000).encashment().getId();
            RefundEntity refund = refund(2000);
            Ledger before = ledger();

            RefundFloorException refused = catchThrowableOfType(
                    () -> corrections.cancel(id, WRONG_AMOUNT, CorrectionMode.PREVIEW, null), RefundFloorException.class);

            assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refused.getMessage()).isEqualTo("Correction refusée : le versé de « Janvier » passerait à "
                    + "0,00 DA, sous les 2 000,00 DA déjà remboursés — remboursement " + refund.getRefundNumber()
                    + " du " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " (2 000,00 DA).");
            assertThat(refused.getBlockingRefunds()).singleElement().satisfies(blocking -> {
                assertThat(blocking.refundNumber()).isEqualTo(refund.getRefundNumber());
                assertThat(blocking.amount()).isEqualByComparingTo("2000");
                assertThat(blocking.seriesName()).isEqualTo("Janvier");
            });
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("plusieurs remboursements en cause sont tous nommés")
        void severalRefundsAreAllNamed() {
            Long id = pay(s1, 3000).encashment().getId();
            RefundEntity firstRefund = refund(500);
            RefundEntity secondRefund = refund(700);

            RefundFloorException refused = catchThrowableOfType(
                    () -> corrections.cancel(id, WRONG_AMOUNT, CorrectionMode.PREVIEW, null), RefundFloorException.class);

            assertThat(refused.getMessage()).contains("remboursements " + firstRefund.getRefundNumber())
                    .contains(", " + secondRefund.getRefundNumber());
            assertThat(refused.getBlockingRefunds()).hasSize(2);
        }

        @Test
        @DisplayName("rester au-dessus du remboursé est permis")
        void stayingAboveTheRefundsIsAllowed() {
            Long small = pay(s1, 1000).encashment().getId();
            pay(s1, 2000);
            refund(1500);

            confirm(small, WRONG_AMOUNT);

            assertThat(statusOf(small)).isEqualTo("CANCELLED");
            assertThat(cumulOf(s1)).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("un jeton obtenu avec un Motif ne confirme pas l'annulation avec un autre")
        void theTokenIsBoundToTheReason() {
            Long id = pay(s1, 3000).encashment().getId();
            String token = corrections.cancel(id, WRONG_AMOUNT, CorrectionMode.PREVIEW, null).previewToken();
            CorrectionReason otherText = new CorrectionReason(CorrectionReasonType.WRONG_AMOUNT, "Autre explication");

            for (CorrectionReason other : new CorrectionReason[] {
                    CorrectionReason.of(CorrectionReasonType.DATA_ENTRY_ERROR), otherText }) {
                assertThat(catchThrowableOfType(() -> corrections.cancel(id, other, CorrectionMode.CONFIRM, token),
                        StalePreviewException.class)).as(other.toString()).isNotNull();
            }
            assertThat(statusOf(id)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("descendre exactement au remboursé est permis : rien n'est rendu deux fois")
        void reachingExactlyTheRefundsIsAllowed() {
            Long small = pay(s1, 1000).encashment().getId();
            pay(s1, 2000);
            refund(2000);

            confirm(small, WRONG_AMOUNT);

            assertThat(cumulOf(s1)).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("un Motif sans rapport avec une annulation est refusé avant tout")
        void anUnrelatedReasonIsRefused() {
            Long id = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            for (CorrectionReasonType type : CorrectionReasonType.values()) {
                if (EncashmentCorrectionService.ENCASHMENT_REASONS.contains(type)) {
                    continue;
                }
                CustomServiceException refused = refusal(() -> corrections.cancel(id,
                        CorrectionReason.of(type), CorrectionMode.PREVIEW, null));
                assertThat(refused.getStatus()).as(type.name()).isEqualTo(HttpStatus.BAD_REQUEST);
            }
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("chaque Motif d'annulation est accepté")
        void everyCancelReasonIsAccepted() {
            for (CorrectionReasonType type : EncashmentCorrectionService.ENCASHMENT_REASONS) {
                Long id = pay(s1, 500).encashment().getId();
                CorrectionReason reason = new CorrectionReason(type, type == CorrectionReasonType.OTHER ? "Doublon" : null);
                corrections.cancel(id, reason, CorrectionMode.CONFIRM,
                        corrections.cancel(id, reason, CorrectionMode.PREVIEW, null).previewToken());
                assertThat(statusOf(id)).as(type.name()).isEqualTo("CANCELLED");
            }
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private CorrectionOutcome<EncashmentDTO> confirm(Long id, CorrectionReason reason) {
        String token = corrections.cancel(id, reason, CorrectionMode.PREVIEW, null).previewToken();
        return corrections.cancel(id, reason, CorrectionMode.CONFIRM, token);
    }

    private RefundEntity refund(double amount) {
        PaymentEntity line = paymentRepository.findAll().stream()
                .filter(p -> p.getSessionSeries().getId().equals(s1.getId())).findFirst().orElseThrow();
        return refundService.create(new RefundRequestDTO(line.getId(), student.getId(),
                BigDecimal.valueOf(amount), null, "Départ anticipé"));
    }

    /** L'année du groupe n'est plus l'année courante : elle est close. */
    private void closeTheYear() {
        year.setIsCurrent(false);
        schoolYearRepository.save(year);
        schoolYearRepository.save(SchoolYearEntity.builder()
                .label("2030-2031").startDate(date(2030, 9, 1)).endDate(date(2031, 6, 30))
                .isCurrent(true).build());
    }

    private static CustomServiceException refusal(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        return catchThrowableOfType(call, CustomServiceException.class);
    }
}
