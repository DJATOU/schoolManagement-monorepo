package com.school.management.service.correction;

import com.school.management.dto.payroll.PayRequest;
import com.school.management.dto.payroll.PayoutDTO;
import com.school.management.dto.payroll.PayoutPreviewDTO;
import com.school.management.dto.payroll.TeacherPayRateDTO;
import com.school.management.dto.payroll.TeacherPayRateRequest;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.SessionService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payroll.TeacherPayRateService;
import com.school.management.service.payroll.TeacherPayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une série payée ne redevient pas non terminée sans que l'administratrice le sache (spec
 * teacher-payroll, exigence 8) : chaque écriture qui lui retirerait une séance validée, ou lui en
 * rendrait une non validée, est refusée en nommant la paie à annuler d'abord.
 *
 * <p>Série « Janvier » de « Math 1ère A » : deux séances validées, 3 000 DA encaissés, payée à Nadia
 * Aït Ahmed au taux de 60 %.</p>
 */
@DisplayName("Garde des séries payées")
class PaidSeriesGuardIntegrationTest extends CorrectionIntegrationTestSupport {

    private static final CorrectionReason ENTRY_ERROR = CorrectionReason.of(CorrectionReasonType.DATA_ENTRY_ERROR);

    @Autowired private AttendanceCorrectionService attendanceCorrections;
    @Autowired private PayoutCorrectionService payoutCorrections;
    @Autowired private SessionService sessionService;
    @Autowired private TeacherPayoutService payouts;
    @Autowired private TeacherPayRateService rates;
    @Autowired private TeacherRepository teacherRepository;

    private TeacherPayRateDTO standard;
    private SessionEntity withdrawn;

    @BeforeEach
    void finishedSeries() {
        TeacherEntity nadia = teacherRepository.save(TeacherEntity.builder().firstName("Nadia").lastName("Aït Ahmed").build());
        group.setTeacher(nadia);
        group = groupRepository.save(group);
        // Une troisième séance, retirée avant la paie : elle ne compte pas dans l'avancement.
        withdrawn = sessionRepository.save(SessionEntity.builder().title("Janvier — séance annulée").group(group)
                .sessionSeries(s1).sessionTimeStart(date(2030, 1, 21)).build());
        sessionService.deactivateSession(withdrawn.getId());
        List<SessionEntity> sessions = sessionRepository.findBySessionSeriesId(s1.getId()).stream()
                .filter(session -> !session.getId().equals(withdrawn.getId())).toList();
        sessions.forEach(session -> session.setIsFinished(true));
        sessionRepository.saveAll(sessions);
        pay(s1, 3000);
        standard = rates.create(new TeacherPayRateRequest("Standard", new BigDecimal("60")));
    }

    private PayoutDTO payJanuary() {
        PayoutPreviewDTO preview = payouts.previewPay(s1.getId(), new PayRequest(standard.id(), null, null));
        return payouts.confirmPay(s1.getId(), new PayRequest(standard.id(), null, preview.previewToken()));
    }

    private String refusal(PayoutDTO payout, String attempted) {
        return "La série « Janvier » est payée à l'enseignant (" + payout.payoutNumber() + ") : annulez cette paie "
                + "avant de " + attempted + ".";
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((CustomServiceException) e).getStatus();
    }

    private void cancel(PayoutDTO payout) {
        String token = payoutCorrections.cancel(payout.id(), ENTRY_ERROR, CorrectionMode.PREVIEW, null).previewToken();
        payoutCorrections.cancel(payout.id(), ENTRY_ERROR, CorrectionMode.CONFIRM, token);
    }

    private boolean finished(SessionEntity session) {
        return Boolean.TRUE.equals(sessionRepository.findById(session.getId()).orElseThrow().getIsFinished());
    }

    @Test
    @DisplayName("dévalider : refusé dès l'Aperçu, en nommant la paie ; permis une fois la paie annulée")
    void unvalidationIsRefusedUntilThePayoutIsCancelled() {
        PayoutDTO paid = payJanuary();

        assertThatThrownBy(() -> attendanceCorrections.unvalidate(s1First.getId(), ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                .hasMessage(refusal(paid, "dévalider une de ses séances"))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThat(finished(s1First)).isTrue();

        cancel(paid);
        assertThatCode(() -> attendanceCorrections.unvalidate(s1First.getId(), ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("plusieurs paies actives : toutes nommées, la plus récente d'abord à annuler")
    void severalPayoutsAreAllNamed() {
        PayoutDTO initial = payJanuary();
        pay(s1, 1000);
        PayoutPreviewDTO preview = payouts.previewRegularize(s1.getId());
        PayoutDTO complement = payouts.confirmRegularize(s1.getId(), new PayRequest(null, null, preview.previewToken()));

        assertThatThrownBy(() -> sessionService.deleteSession(s1First.getId()))
                .hasMessage("La série « Janvier » est payée à l'enseignant (" + initial.payoutNumber() + ", "
                        + complement.payoutNumber() + ") : annulez ces paies, en commençant par "
                        + complement.payoutNumber() + ", avant de supprimer une de ses séances.");
    }

    @Test
    @DisplayName("supprimer, désactiver une séance : refusé, la séance reste en place")
    void deletionAndDeactivationAreRefused() {
        PayoutDTO paid = payJanuary();

        assertThatThrownBy(() -> sessionService.deleteSession(s1First.getId()))
                .hasMessage(refusal(paid, "supprimer une de ses séances"));
        assertThatThrownBy(() -> sessionService.deactivateSession(s1First.getId()))
                .hasMessage(refusal(paid, "désactiver une de ses séances"));
        assertThat(sessionRepository.findById(s1First.getId())).get()
                .satisfies(session -> assertThat(session.getActive()).isTrue());
    }

    @Test
    @DisplayName("réactiver une séance retirée avant la paie : refusé, elle rendrait la série non terminée")
    void reactivationIsRefused() {
        PayoutDTO paid = payJanuary();

        assertThatThrownBy(() -> sessionService.reactivateSession(withdrawn.getId()))
                .hasMessage(refusal(paid, "réactiver une de ses séances"));
        assertThat(sessionRepository.findById(withdrawn.getId())).get()
                .satisfies(session -> assertThat(session.getActive()).isFalse());
    }

    @Test
    @DisplayName("modifier : dévalider ou changer de groupe refusé ; corriger le titre d'une séance validée permis")
    void updateRefusesOnlyRealTransitions() {
        PayoutDTO paid = payJanuary();

        Map<String, Object> unvalidate = new HashMap<>();
        unvalidate.put("isFinished", false);
        assertThatThrownBy(() -> sessionService.updateSession(s1First.getId(), unvalidate))
                .hasMessage(refusal(paid, "dévalider une de ses séances"));
        Map<String, Object> cleared = new HashMap<>();
        cleared.put("isFinished", null);
        assertThatThrownBy(() -> sessionService.updateSession(s1First.getId(), cleared))
                .hasMessage(refusal(paid, "dévalider une de ses séances"));

        GroupEntity other = groupRepository.save(GroupEntity.builder().name("Math 1ère B").price(group.getPrice())
                .schoolYear(year).sessionNumberPerSerie(2).build());
        Map<String, Object> moved = new HashMap<>();
        moved.put("groupId", other.getId());
        assertThatThrownBy(() -> sessionService.updateSession(s1First.getId(), moved))
                .hasMessage(refusal(paid, "déplacer une de ses séances vers un autre groupe"));

        // Le client renvoie la séance entière : un titre corrigé, validation inchangée, passe.
        Map<String, Object> renamed = new HashMap<>();
        renamed.put("title", "Janvier — séance 1 (salle 3)");
        renamed.put("isFinished", true);
        renamed.put("groupId", group.getId());
        assertThatCode(() -> sessionService.updateSession(s1First.getId(), renamed)).doesNotThrowAnyException();
        assertThat(finished(s1First)).isTrue();
        // Une séance qui n'était pas validée, renvoyée non validée : aucune transition, permis.
        Map<String, Object> untouched = new HashMap<>();
        untouched.put("title", "Janvier — séance annulée (report)");
        untouched.put("isFinished", false);
        assertThatCode(() -> sessionService.updateSession(withdrawn.getId(), untouched)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("série sans paie active : aucune de ces écritures n'est gênée par la garde")
    void unpaidSeriesIsNotGuarded() {
        assertThatCode(() -> sessionService.reactivateSession(withdrawn.getId())).doesNotThrowAnyException();
        assertThatCode(() -> attendanceCorrections.unvalidate(s1First.getId(), ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                .doesNotThrowAnyException();
    }
}
