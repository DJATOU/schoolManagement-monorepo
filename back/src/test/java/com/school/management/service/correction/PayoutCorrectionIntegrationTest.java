package com.school.management.service.correction;

import com.school.management.dto.payroll.PayRequest;
import com.school.management.dto.payroll.PayableSeriesDTO;
import com.school.management.dto.payroll.PayableSeriesDTO.PayableState;
import com.school.management.dto.payroll.PayoutDTO;
import com.school.management.dto.payroll.PayoutPreviewDTO;
import com.school.management.dto.payroll.TeacherPayRateDTO;
import com.school.management.dto.payroll.TeacherPayRateRequest;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PayoutKind;
import com.school.management.persistance.PayoutStatus;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.TeacherPayoutRepository;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payroll.TeacherPayRateService;
import com.school.management.service.payroll.TeacherPayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Corriger une paie d'enseignant : annuler, remplacer (spec teacher-payroll, exigence 7), sur une
 * vraie base H2, par le moteur des corrections.
 *
 * <h2>Le scénario</h2>
 * Nadia Aït Ahmed enseigne au groupe « Math 1ère A ». La série « Janvier » (deux séances, validées) a
 * encaissé 3 000 DA d'Amine. Au taux Standard de 60 %, sa paie est de 1 800 DA, l'école garde 1 200 DA.
 */
@DisplayName("Corriger une paie d'enseignant")
class PayoutCorrectionIntegrationTest extends CorrectionIntegrationTestSupport {

    private static final String YEAR = String.valueOf(LocalDate.now(ZoneId.systemDefault()).getYear());
    private static final String TODAY = LocalDate.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    private static final CorrectionReason ENTRY_ERROR =
            new CorrectionReason(CorrectionReasonType.DATA_ENTRY_ERROR, "Mauvais taux choisi");

    @Autowired private PayoutCorrectionService corrections;
    @Autowired private TeacherPayoutService payouts;
    @Autowired private TeacherPayRateService rates;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private TeacherPayoutRepository payoutRepository;

    private final AtomicInteger refundRank = new AtomicInteger();

    private TeacherEntity nadia;
    private TeacherPayRateDTO standard;
    private TeacherPayRateDTO confirmed;

    @BeforeEach
    void paidTeacherSeries() {
        nadia = teacherRepository.save(TeacherEntity.builder().firstName("Nadia").lastName("Aït Ahmed").build());
        group.setTeacher(nadia);
        group = groupRepository.save(group);
        List<SessionEntity> sessions = sessionRepository.findBySessionSeriesId(s1.getId());
        sessions.forEach(session -> session.setIsFinished(true));
        sessionRepository.saveAll(sessions);
        pay(s1, 3000);
        standard = rates.create(new TeacherPayRateRequest("Standard", new BigDecimal("60")));
        confirmed = rates.create(new TeacherPayRateRequest("Confirmé", new BigDecimal("62.5")));
    }

    private PayoutDTO payJanuary() {
        PayoutPreviewDTO preview = payouts.previewPay(s1.getId(), new PayRequest(standard.id(), "Remis en main propre", null));
        return payouts.confirmPay(s1.getId(), new PayRequest(standard.id(), "Remis en main propre", preview.previewToken()));
    }

    private PayoutDTO regularizeJanuary() {
        PayoutPreviewDTO preview = payouts.previewRegularize(s1.getId());
        return payouts.confirmRegularize(s1.getId(), new PayRequest(null, null, preview.previewToken()));
    }

    private void refund(String amount) {
        PaymentEntity payment = paymentRepository.findAll().stream()
                .filter(p -> p.getSessionSeries() != null && p.getSessionSeries().getId().equals(s1.getId()))
                .findFirst().orElseThrow();
        refundRepository.save(RefundEntity.builder().payment(payment).student(student)
                .amount(new BigDecimal(amount)).refundDate(new Date()).reason("Trop-perçu")
                .refundNumber("REMB-2030-" + String.format("%04d", refundRank.incrementAndGet())).build());
    }

    private CorrectionOutcome<PayoutDTO> cancel(Long payoutId, CorrectionReason reason) {
        CorrectionOutcome<PayoutDTO> preview = corrections.cancel(payoutId, reason, CorrectionMode.PREVIEW, null);
        return corrections.cancel(payoutId, reason, CorrectionMode.CONFIRM, preview.previewToken());
    }

    private CorrectionOutcome<PayoutCorrection> replace(Long payoutId, Long rateId, String note) {
        CorrectionOutcome<PayoutCorrection> preview =
                corrections.replace(payoutId, rateId, note, ENTRY_ERROR, CorrectionMode.PREVIEW, null);
        return corrections.replace(payoutId, rateId, note, ENTRY_ERROR, CorrectionMode.CONFIRM, preview.previewToken());
    }

    private String statusOfPayout(Long payoutId) {
        return jdbc.queryForObject("SELECT status FROM teacher_payout WHERE id = ?", String.class, payoutId);
    }

    private long payoutRank() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(last_rank), 0) FROM payout_counter", Long.class);
    }

    private static String number(int rank) {
        return String.format("PAIE-%s-%04d", YEAR, rank);
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((CustomServiceException) e).getStatus();
    }

    private List<PayableSeriesDTO> payableJanuary() {
        return payouts.payable(null, group.getId()).stream()
                .filter(row -> row.seriesId().equals(s1.getId())).toList();
    }

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Annuler")
    class Annuler {

        @Test
        @DisplayName("l'Aperçu annonce la paie annulée, la série de nouveau à payer, les deux parts, et n'écrit rien")
        void previewAnnouncesAndWritesNothing() {
            PayoutDTO paid = payJanuary();
            long traces = count("SELECT COUNT(*) FROM correction_audit");

            CorrectionOutcome<PayoutDTO> preview = corrections.cancel(paid.id(), ENTRY_ERROR, CorrectionMode.PREVIEW, null);

            assertThat(preview.result()).isNull();
            assertThat(preview.preview().series()).isEmpty();
            assertThat(preview.preview().effects()).extracting(CorrectionEffect::type).containsExactly(
                    CorrectionEffectType.PAYOUT_CANCELLED, CorrectionEffectType.PAYOUT_SHARES_CHANGED);
            assertThat(preview.preview().effects()).extracting(CorrectionEffect::description).containsExactly(
                    "Paie " + paid.payoutNumber() + " de 1 800,00 DA à Nadia Aït Ahmed annulée : la série redevient à payer",
                    "« Janvier » (Math 1ère A) : versé à l'enseignant 1 800,00 → 0,00 DA, part de l'école "
                            + "1 200,00 → 0,00 DA");
            assertThat(preview.previewToken()).matches("[0-9a-f]{64}");
            assertThat(statusOfPayout(paid.id())).isEqualTo("ACTIVE");
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isEqualTo(traces);
        }

        @Test
        @DisplayName("la confirmation garde la paie lisible, marquée, la trace, et rend la série à payer (7.5)")
        void confirmationMarksTracesAndReopensTheSeries() {
            PayoutDTO paid = payJanuary();

            PayoutDTO cancelled = cancel(paid.id(), ENTRY_ERROR).result();

            assertThat(cancelled.status()).isEqualTo(PayoutStatus.CANCELLED);
            assertThat(cancelled.cancelledBy()).isEqualTo(ADMIN);
            assertThat(cancelled.cancelledAt()).isNotNull();
            assertThat(cancelled.cancelReasonType()).isEqualTo(CorrectionReasonType.DATA_ENTRY_ERROR);
            assertThat(cancelled.cancelReasonText()).isEqualTo("Mauvais taux choisi");
            // Les montants de la pièce ne changent pas : elle reste ce qui a été versé (6.1).
            assertThat(cancelled.teacherAmount()).isEqualByComparingTo("1800");

            Map<String, Object> trace = jdbc.queryForMap("SELECT domain, action, entity_id, student_id, group_id, "
                    + "series_id, summary, amount_effect, reason_type, reason_text, performed_by, old_value, new_value "
                    + "FROM correction_audit");
            assertThat(trace.get("DOMAIN")).isEqualTo("TEACHER_PAYOUT");
            assertThat(trace.get("ACTION")).isEqualTo("PAYOUT_CANCELLED");
            assertThat(((Number) trace.get("ENTITY_ID")).longValue()).isEqualTo(paid.id());
            assertThat(trace.get("STUDENT_ID")).isNull();
            assertThat(((Number) trace.get("GROUP_ID")).longValue()).isEqualTo(group.getId());
            assertThat(((Number) trace.get("SERIES_ID")).longValue()).isEqualTo(s1.getId());
            assertThat(trace.get("SUMMARY")).isEqualTo("Paie " + paid.payoutNumber()
                    + " de 1 800,00 DA à Nadia Aït Ahmed annulée (Janvier, Math 1ère A) : enseignant 1 800,00 → "
                    + "0,00 DA, école 1 200,00 → 0,00 DA");
            assertThat(trace.get("REASON_TYPE")).isEqualTo("DATA_ENTRY_ERROR");
            assertThat(trace.get("REASON_TEXT")).isEqualTo("Mauvais taux choisi");
            assertThat(trace.get("PERFORMED_BY")).isEqualTo(ADMIN);
            assertThat((String) trace.get("OLD_VALUE")).contains("\"status\":\"ACTIVE\"")
                    .contains("\"seriesTeacherPaid\":1800.00").contains("\"seriesSchoolKept\":1200.00");
            assertThat((String) trace.get("NEW_VALUE")).contains("\"status\":\"CANCELLED\"")
                    .contains("\"seriesTeacherPaid\":0.00").contains("\"seriesSchoolKept\":0.00");

            assertThat(payableJanuary()).singleElement()
                    .extracting(PayableSeriesDTO::state).isEqualTo(PayableState.PAYABLE);
            // Payer à nouveau prend le numéro suivant : un numéro annulé n'est pas réattribué.
            assertThat(payJanuary().payoutNumber()).isEqualTo(number(2));
        }

        @Test
        @DisplayName("un complément annulé : « annulé », et l'écart réapparaît à régulariser")
        void cancelledComplementReopensTheGap() {
            payJanuary();
            pay(s1, 1000);
            PayoutDTO complement = regularizeJanuary();

            CorrectionOutcome<PayoutDTO> preview =
                    corrections.cancel(complement.id(), ENTRY_ERROR, CorrectionMode.PREVIEW, null);
            assertThat(preview.preview().effects()).extracting(CorrectionEffect::description).containsExactly(
                    "Complément " + complement.payoutNumber() + " de 600,00 DA à Nadia Aït Ahmed annulé",
                    "« Janvier » (Math 1ère A) : versé à l'enseignant 2 400,00 → 1 800,00 DA, part de l'école "
                            + "1 600,00 → 1 200,00 DA");
            corrections.cancel(complement.id(), ENTRY_ERROR, CorrectionMode.CONFIRM, preview.previewToken());

            assertThat(payableJanuary()).singleElement().satisfies(row -> {
                assertThat(row.state()).isEqualTo(PayableState.TO_REGULARIZE);
                assertThat(row.gap()).isEqualByComparingTo("600");
            });
        }

        @Test
        @DisplayName("une retenue annulée : « Retenue … sur … annulée »")
        void cancelledDeductionIsNamedAsSuch() {
            payJanuary();
            refund("1000");
            PayoutDTO deduction = regularizeJanuary();

            assertThat(corrections.cancel(deduction.id(), ENTRY_ERROR, CorrectionMode.PREVIEW, null)
                    .preview().effects().get(0).description())
                    .isEqualTo("Retenue " + deduction.payoutNumber() + " de 600,00 DA sur Nadia Aït Ahmed annulée");
        }

        @Test
        @DisplayName("pas la plus récente de la série : 409 nommant celle à corriger d'abord (D3), rien d'écrit")
        void onlyTheLatestIsCorrectable() {
            PayoutDTO initial = payJanuary();
            pay(s1, 1000);
            PayoutDTO complement = regularizeJanuary();

            assertThatThrownBy(() -> corrections.cancel(initial.id(), ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                    .hasMessage("La paie " + initial.payoutNumber() + " n'est pas la plus récente de la série « Janvier » : "
                            + "corrigez d'abord " + complement.payoutNumber() + ".")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
            assertThat(statusOfPayout(initial.id())).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("déjà annulée : 409 disant quand et par qui ; introuvable : 404")
        void alreadyCancelledOrUnknown() {
            PayoutDTO paid = payJanuary();
            cancel(paid.id(), ENTRY_ERROR);

            assertThatThrownBy(() -> corrections.cancel(paid.id(), ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                    .hasMessage("La paie " + paid.payoutNumber() + " est déjà annulée, le " + TODAY + " par " + ADMIN + ".")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
            assertThatThrownBy(() -> corrections.cancel(99_999L, ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                    .hasMessage("Paie introuvable : 99999")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("motif d'une autre correction : 400 ; confirmation sans aperçu : 400 ; jeton d'un autre motif : périmé")
        void reasonAndTokenRefusals() {
            PayoutDTO paid = payJanuary();
            CorrectionReason wrongStudent = CorrectionReason.of(CorrectionReasonType.WRONG_STUDENT);

            assertThatThrownBy(() -> corrections.cancel(paid.id(), wrongStudent, CorrectionMode.PREVIEW, null))
                    .hasMessageContaining("sans rapport avec la correction d'une paie")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatThrownBy(() -> corrections.cancel(paid.id(), ENTRY_ERROR, CorrectionMode.CONFIRM, null))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));

            String token = corrections.cancel(paid.id(), ENTRY_ERROR, CorrectionMode.PREVIEW, null).previewToken();
            CorrectionReason other = CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT);
            assertThatThrownBy(() -> corrections.cancel(paid.id(), other, CorrectionMode.CONFIRM, token))
                    .isInstanceOf(StalePreviewException.class);
            assertThat(statusOfPayout(paid.id())).isEqualTo("ACTIVE");
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }
    }

    // ------------------------------------------------------------------
    // Remplacement
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Remplacer")
    class Remplacer {

        @Test
        @DisplayName("l'Aperçu annonce l'annulation, la remplaçante et son calcul, les deux parts ; aucun numéro consommé")
        void previewAnnouncesAndConsumesNoNumber() {
            PayoutDTO paid = payJanuary();
            long rank = payoutRank();

            CorrectionOutcome<PayoutCorrection> preview =
                    corrections.replace(paid.id(), confirmed.id(), null, ENTRY_ERROR, CorrectionMode.PREVIEW, null);

            assertThat(preview.result()).isNull();
            assertThat(preview.preview().effects()).extracting(CorrectionEffect::description).containsExactly(
                    "Paie " + paid.payoutNumber() + " de 1 800,00 DA à Nadia Aït Ahmed annulée, remplacée par " + number(2),
                    "Paie " + number(2) + " : 3 000,00 DA × 62,5 % = 1 875,00 DA à Nadia Aït Ahmed, école 1 125,00 DA "
                            + "(taux « Confirmé »)",
                    "« Janvier » (Math 1ère A) : versé à l'enseignant 1 800,00 → 1 875,00 DA, part de l'école "
                            + "1 200,00 → 1 125,00 DA");
            assertThat(preview.preview().effects()).extracting(CorrectionEffect::type).containsExactly(
                    CorrectionEffectType.PAYOUT_CANCELLED, CorrectionEffectType.PAYOUT_CREATED,
                    CorrectionEffectType.PAYOUT_SHARES_CHANGED);
            assertThat(payoutRank()).isEqualTo(rank);
            assertThat(payoutRepository.count()).isEqualTo(1);
            assertThat(statusOfPayout(paid.id())).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("la confirmation annule l'originale, la relie à sa remplaçante, et le trace")
        void confirmationLinksAndTraces() {
            PayoutDTO paid = payJanuary();

            PayoutCorrection done = replace(paid.id(), confirmed.id(), null).result();

            PayoutDTO original = done.original();
            PayoutDTO replacement = done.replacement();
            assertThat(original.status()).isEqualTo(PayoutStatus.CANCELLED);
            assertThat(original.replacedByNumber()).isEqualTo(number(2));
            assertThat(replacement.payoutNumber()).isEqualTo(number(2));
            assertThat(replacement.kind()).isEqualTo(PayoutKind.INITIAL);
            assertThat(replacement.status()).isEqualTo(PayoutStatus.ACTIVE);
            assertThat(replacement.replacesNumber()).isEqualTo(paid.payoutNumber());
            assertThat(replacement.rateLabel()).isEqualTo("Confirmé");
            assertThat(replacement.teacherPercent()).isEqualByComparingTo("62.5");
            assertThat(replacement.collectedNet()).isEqualByComparingTo("3000");
            assertThat(replacement.teacherAmount()).isEqualByComparingTo("1875");
            assertThat(replacement.schoolAmount()).isEqualByComparingTo("1125");
            assertThat(replacement.teacherId()).isEqualTo(nadia.getId());
            assertThat(replacement.note()).isEqualTo("Remis en main propre");
            assertThat(replacement.paidBy()).isEqualTo(ADMIN);

            assertThat(jdbc.queryForMap("SELECT action, entity_id, summary, old_value, new_value FROM correction_audit"))
                    .satisfies(row -> {
                        assertThat(row.get("ACTION")).isEqualTo("PAYOUT_REPLACED");
                        assertThat(((Number) row.get("ENTITY_ID")).longValue()).isEqualTo(paid.id());
                        assertThat(row.get("SUMMARY")).isEqualTo("Paie " + paid.payoutNumber() + " remplacée par "
                                + number(2) + " (Janvier, Math 1ère A) : taux « Standard » 60 % → « Confirmé » 62,5 %, "
                                + "enseignant 1 800,00 → 1 875,00 DA, école 1 200,00 → 1 125,00 DA");
                        assertThat((String) row.get("OLD_VALUE")).contains("\"rateLabel\":\"Standard\"")
                                .contains("\"teacherAmount\":1800.00").contains("\"status\":\"ACTIVE\"");
                        assertThat((String) row.get("NEW_VALUE")).contains("\"rateLabel\":\"Confirmé\"")
                                .contains("\"teacherAmount\":1875.00").contains("\"replacedBy\":\"" + number(2) + "\"");
                    });
            // La série reste payée : aucune ligne « à payer ».
            assertThat(payableJanuary()).isEmpty();
        }

        @Test
        @DisplayName("l'originale remplacée ne se corrige plus : 409 nommant sa remplaçante")
        void replacedOriginalNamesItsReplacement() {
            PayoutDTO paid = payJanuary();
            replace(paid.id(), confirmed.id(), null);

            assertThatThrownBy(() -> corrections.cancel(paid.id(), ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                    .hasMessage("La paie " + paid.payoutNumber() + " est déjà annulée, le " + TODAY + " par " + ADMIN
                            + ", remplacée par " + number(2) + ".");
        }

        @Test
        @DisplayName("paie dont le lien vers le taux a disparu : le remplacement reste possible")
        void replacementWithoutLinkedRate() {
            PayoutDTO paid = payJanuary();
            jdbc.update("UPDATE teacher_payout SET rate_id = NULL WHERE id = ?", paid.id());

            assertThat(replace(paid.id(), standard.id(), null).result().replacement().rateLabel()).isEqualTo("Standard");
        }

        @Test
        @DisplayName("note donnée : elle remplace celle de l'originale")
        void noteIsReplacedWhenGiven() {
            PayoutDTO paid = payJanuary();
            assertThat(replace(paid.id(), confirmed.id(), "Versé par virement").result().replacement().note())
                    .isEqualTo("Versé par virement");
        }

        @Test
        @DisplayName("même enseignant que l'originale, même si le groupe a changé d'enseignant depuis")
        void replacementKeepsTheOriginalTeacher() {
            PayoutDTO paid = payJanuary();
            group.setTeacher(teacherRepository.save(TeacherEntity.builder().firstName("Karim").lastName("Haddad").build()));
            group = groupRepository.save(group);

            assertThat(replace(paid.id(), confirmed.id(), null).result().replacement().teacherId())
                    .isEqualTo(nadia.getId());
        }

        @Test
        @DisplayName("argent rendu depuis l'Aperçu : confirmation périmée, rien d'écrit")
        void refundAfterPreviewIsStale() {
            PayoutDTO paid = payJanuary();
            String token = corrections.replace(paid.id(), confirmed.id(), null, ENTRY_ERROR, CorrectionMode.PREVIEW, null)
                    .previewToken();
            refund("1000");

            assertThatThrownBy(() -> corrections.replace(paid.id(), confirmed.id(), null, ENTRY_ERROR,
                    CorrectionMode.CONFIRM, token))
                    .isInstanceOfSatisfying(StalePreviewException.class, e ->
                            assertThat(e.getPreview().effects().get(1).description()).contains("2 000,00 DA × 62,5 %"));
            assertThat(payoutRepository.count()).isEqualTo(1);
            assertThat(statusOfPayout(paid.id())).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("régularisation, même taux, taux désactivé, taux manquant, rien à partager : refusés, rien d'écrit")
        void refusals() {
            PayoutDTO paid = payJanuary();

            assertThatThrownBy(() -> corrections.replace(paid.id(), standard.id(), null, ENTRY_ERROR,
                    CorrectionMode.PREVIEW, null))
                    .hasMessageContaining("est déjà au taux « Standard » : choisissez un autre taux")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
            assertThatThrownBy(() -> corrections.replace(paid.id(), null, null, ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                    .hasMessage("Choisissez le taux de la paie de remplacement.")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
            rates.disable(confirmed.id());
            assertThatThrownBy(() -> corrections.replace(paid.id(), confirmed.id(), null, ENTRY_ERROR,
                    CorrectionMode.PREVIEW, null))
                    .hasMessageContaining("désactivé")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));

            TeacherPayRateDTO expert = rates.create(new TeacherPayRateRequest("Expert", new BigDecimal("70")));
            refund("3000");
            assertThatThrownBy(() -> corrections.replace(paid.id(), expert.id(), null, ENTRY_ERROR,
                    CorrectionMode.PREVIEW, null))
                    .hasMessageContaining("Rien à partager")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));

            PayoutDTO deduction = regularizeJanuary();
            assertThatThrownBy(() -> corrections.replace(deduction.id(), expert.id(), null, ENTRY_ERROR,
                    CorrectionMode.PREVIEW, null))
                    .hasMessageContaining("est une régularisation : elle ne se remplace pas")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
            assertThat(payoutRepository.count()).isEqualTo(2);
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }
    }
}
