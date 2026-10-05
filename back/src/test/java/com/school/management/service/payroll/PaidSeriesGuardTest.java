package com.school.management.service.payroll;

import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.TeacherPayoutEntity;
import com.school.management.repository.TeacherPayoutRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Garde des séries payées, sans base : quand elle se tait, et ce qu'elle dit. Ses points d'appel
 * sont éprouvés par {@code PaidSeriesGuardIntegrationTest}.
 */
@DisplayName("Garde des séries payées : refus nommé")
class PaidSeriesGuardTest {

    private final TeacherPayoutRepository payouts = mock(TeacherPayoutRepository.class);
    private final PaidSeriesGuard guard = new PaidSeriesGuard(payouts);

    private static SessionSeriesEntity series(Long id) {
        return SessionSeriesEntity.builder().id(id).name("Octobre").build();
    }

    private static TeacherPayoutEntity payout(String number) {
        return TeacherPayoutEntity.builder().payoutNumber(number).build();
    }

    @Test
    @DisplayName("séance sans série, ou série pas encore enregistrée : rien à garder, aucune lecture")
    void nothingToGuardWithoutSeries() {
        assertThatCode(() -> guard.assertNoActivePayout(null, "supprimer une de ses séances")).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertNoActivePayout(series(null), "supprimer une de ses séances"))
                .doesNotThrowAnyException();
        verifyNoInteractions(payouts);
    }

    @Test
    @DisplayName("série sans paie active : permis")
    void unpaidSeriesPasses() {
        when(payouts.findActiveForSeries(7L)).thenReturn(List.of());

        assertThatCode(() -> guard.assertNoActivePayout(series(7L), "supprimer une de ses séances"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("une paie : 409 la nommant ; plusieurs : toutes nommées, la plus récente à annuler d'abord")
    void refusalNamesThePayouts() {
        when(payouts.findActiveForSeries(7L)).thenReturn(List.of(payout("PAIE-2030-0001")));
        when(payouts.findActiveForSeries(8L)).thenReturn(List.of(payout("PAIE-2030-0001"), payout("PAIE-2030-0004")));

        assertThatThrownBy(() -> guard.assertNoActivePayout(series(7L), "désactiver une de ses séances"))
                .isInstanceOf(CustomServiceException.class)
                .hasMessage("La série « Octobre » est payée à l'enseignant (PAIE-2030-0001) : annulez cette paie "
                        + "avant de désactiver une de ses séances.")
                .extracting(e -> ((CustomServiceException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> guard.assertNoActivePayout(series(8L), "désactiver une de ses séances"))
                .hasMessage("La série « Octobre » est payée à l'enseignant (PAIE-2030-0001, PAIE-2030-0004) : "
                        + "annulez ces paies, en commençant par PAIE-2030-0004, avant de désactiver une de ses séances.");
    }
}
