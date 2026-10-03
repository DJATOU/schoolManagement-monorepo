package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.dto.revenue.GroupRevenueDTO;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.service.correction.CorrectionIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que doit un étudiant parti, de bout en bout (spec admin-corrections, C.5 ; décision D5).
 *
 * <p>Amine, inscrit à « Math 1ère A » à 2 000 DA la séance. Série « Janvier » : 07/01 et 14/01/2030 ;
 * « Février » : 04/02 et 11/02/2030. Aucune séance n'est validée : c'est précisément le cas que
 * l'ancien calcul ratait — à la clôture, l'inscription n'était plus lue, et une séance de sa
 * période sans fiche de présence cessait d'être due.</p>
 */
@DisplayName("Facturation d'un étudiant parti")
class DepartedStudentBillingIntegrationTest extends CorrectionIntegrationTestSupport {

    @Autowired private PaymentQuoteService quoteService;
    @Autowired private GroupRevenueService groupRevenueService;

    private void leaves(LocalDate departure) {
        jdbc.update("UPDATE student_groups SET active = FALSE, date_left = ? WHERE student_id = ? AND group_id = ?",
                Timestamp.valueOf(departure.atStartOfDay()), student.getId(), group.getId());
    }

    @Test
    @DisplayName("parti le 14/01, rien de validé : janvier coûte toujours 4 000 DA, et le versement le solde")
    void windowSessionsStayDueWithoutAttendance() {
        pay(s1, 4000);
        leaves(LocalDate.of(2030, 1, 14));

        PaymentQuoteDTO january = quoteService.quote(student.getId(), s1.getId());

        // Avant : 0 séance facturable, 4 000 DA annoncés en trop-perçu.
        assertThat(january.billableSessions()).isEqualTo(2);
        assertThat(january.monthTotalCost()).isEqualByComparingTo("4000.00");
        assertThat(january.remainingToPay()).isEqualByComparingTo("0.00");
        assertThat(january.existingExcess()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("février, après son départ : rien n'est dû")
    void seriesAfterDepartureCostsNothing() {
        leaves(LocalDate.of(2030, 1, 14));

        PaymentQuoteDTO february = quoteService.quote(student.getId(), s2.getId());

        assertThat(february.billableSessions()).isZero();
        assertThat(february.monthTotalCost()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("parti le 07/01 : la séance du 14/01 n'est plus due")
    void sessionsAfterDepartureAreNotDue() {
        leaves(LocalDate.of(2030, 1, 7));

        assertThat(quoteService.quote(student.getId(), s1.getId()).monthTotalCost()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("venu le 04/02, après son départ : la séance est due, consommée")
    void attendanceAfterDepartureIsDue() {
        leaves(LocalDate.of(2030, 1, 14));
        attend(firstSessionOf(s2.getId()));

        assertThat(quoteService.quote(student.getId(), s2.getId()).monthTotalCost()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("revenu le 11/02 : février ne lui facture que la séance du 11/02")
    void returnedStudentOwesFromHisReturn() {
        leaves(LocalDate.of(2030, 1, 14));
        studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(group)
                .dateAssigned(Date.from(LocalDate.of(2030, 2, 11).atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .build());

        PaymentQuoteDTO february = quoteService.quote(student.getId(), s2.getId());

        assertThat(february.billableSessions()).isEqualTo(1);
        assertThat(february.monthTotalCost()).isEqualByComparingTo("2000.00");
        assertThat(quoteService.quote(student.getId(), s1.getId()).billableSessions()).isEqualTo(2);
    }

    @Test
    @DisplayName("relevé du groupe : le dû et le trop-perçu de l'étudiant parti y figurent encore")
    void groupRevenueStillCountsTheDepartedStudent() {
        pay(s1, 4000);
        pay(s2, 1000);
        leaves(LocalDate.of(2030, 1, 14));

        GroupRevenueDTO revenue = groupRevenueService.getGroupRevenue(group.getId());

        // Janvier 4 000 dus et versés ; février 0 dû, 1 000 versés avant le départ : trop-perçu.
        assertThat(revenue.expected()).isEqualByComparingTo("4000.00");
        assertThat(revenue.remaining()).isEqualByComparingTo("0.00");
        assertThat(revenue.overpaid()).isEqualByComparingTo("1000.00");
    }

    private SessionEntity firstSessionOf(Long seriesId) {
        return sessionRepository.findAll().stream()
                .filter(s -> s.getSessionSeries().getId().equals(seriesId))
                .min((a, b) -> a.getSessionTimeStart().compareTo(b.getSessionTimeStart()))
                .orElseThrow();
    }
}
