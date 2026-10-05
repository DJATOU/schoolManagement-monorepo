package com.school.management.service.payroll;

import com.school.management.dto.payroll.PayoutSlipDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PayoutKind;
import com.school.management.persistance.PayoutSlipIssuanceEntity;
import com.school.management.persistance.PayoutStatus;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.persistance.TeacherPayoutEntity;
import com.school.management.repository.PayoutSlipIssuanceRepository;
import com.school.management.repository.TeacherPayoutRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.AuditorAware;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bordereau de paie : nom de fichier et auteur de l'impression (spec teacher-payroll, exigence 5).
 * Le rang du duplicata, sur une vraie base, est éprouvé par {@code TeacherPayoutServiceIntegrationTest}.
 */
@DisplayName("Bordereau de paie : nom de fichier, auteur, rang")
class PayoutSlipServiceTest {

    private static TeacherPayoutEntity payout(String firstName, String lastName) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return TeacherPayoutEntity.builder()
                .id(40L).payoutNumber("PAIE-2030-0001").kind(PayoutKind.INITIAL)
                .teacher(TeacherEntity.builder().id(5L).firstName(firstName).lastName(lastName).build())
                .group(GroupEntity.builder().id(3L).name("Maths 4 AM A").build())
                .series(SessionSeriesEntity.builder().id(12L).name("Octobre").build())
                .rateLabel("Standard").teacherPercent(new BigDecimal("60.00"))
                .collectedGross(zero).refunded(zero).collectedNet(zero).baseDelta(zero)
                .teacherAmount(zero).schoolAmount(zero)
                .paidAt(new Date()).paidBy("admin").status(PayoutStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("nom : numéro et enseignant, sans accents ni ponctuation, en minuscules")
    void fileNameIsAPlainSlug() {
        assertThat(PayoutSlipService.fileName(payout("Nadia", "Aït-Ahmed (fille)")))
                .isEqualTo("paie-2030-0001_nadia_ait-ahmed_fille.pdf");
    }

    @Test
    @DisplayName("enseignant sans nom lisible : « enseignant »")
    void fileNameFallsBackWhenTheNameIsEmpty() {
        assertThat(PayoutSlipService.fileName(payout(null, null))).isEqualTo("paie-2030-0001_enseignant.pdf");
        assertThat(PayoutSlipService.fileName(payout("  ", "!!"))).isEqualTo("paie-2030-0001_enseignant.pdf");
    }

    @Test
    @DisplayName("nom très long : tronqué à 150 caractères avant l'extension")
    void fileNameIsBounded() {
        String name = PayoutSlipService.fileName(payout("A".repeat(200), "B"));

        assertThat(name).hasSize(150 + ".pdf".length()).startsWith("paie-2030-0001_aaaa").endsWith("a.pdf");
    }

    @Test
    @DisplayName("impression : rang suivant le dernier, auteur connecté, ou « system » s'il est vide")
    void issueRecordsRankAndAuthor() {
        TeacherPayoutRepository payouts = mock(TeacherPayoutRepository.class);
        PayoutSlipIssuanceRepository issuances = mock(PayoutSlipIssuanceRepository.class);
        @SuppressWarnings("unchecked")
        AuditorAware<String> auditor = mock(AuditorAware.class);
        TeacherPayoutEntity payout = payout("Nadia", "Aït Ahmed");
        when(payouts.findById(40L)).thenReturn(Optional.of(payout));
        when(issuances.findMaxRank(40L)).thenReturn(2);
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of("directrice"), Optional.of("  "));
        PayoutSlipService service = new PayoutSlipService(payouts, issuances, auditor);

        PayoutSlipDTO first = service.issue(40L);
        PayoutSlipDTO blankAuthor = service.issue(40L);

        assertThat(first.issuanceRank()).isEqualTo(3);
        assertThat(first.issuedBy()).isEqualTo("directrice");
        assertThat(first.payout().payoutNumber()).isEqualTo("PAIE-2030-0001");
        assertThat(blankAuthor.issuedBy()).isEqualTo("system");
        ArgumentCaptor<PayoutSlipIssuanceEntity> saved = ArgumentCaptor.forClass(PayoutSlipIssuanceEntity.class);
        verify(issuances, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getRank()).isEqualTo(3);
        assertThat(saved.getAllValues().get(0).getPayout()).isSameAs(payout);
        assertThat(saved.getAllValues().get(0).getIssuedBy()).isEqualTo("directrice");
    }
}
