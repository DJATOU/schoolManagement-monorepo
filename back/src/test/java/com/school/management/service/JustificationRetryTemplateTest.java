package com.school.management.service;

import com.school.management.dto.JustificationUpdateResult;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link JustificationRetryTemplate} (exigences 5.5, 5.6, 5.10).
 *
 * <p>Le rejeu vit dans un composant séparé pour une raison précise : une méthode transactionnelle
 * qui se rappellerait elle-même ne réessaierait rien, sa transaction étant déjà marquée pour
 * annulation. Ces tests vérifient le nombre exact de tentatives, qui est la seule façon de constater
 * que le rejeu a réellement lieu.</p>
 */
class JustificationRetryTemplateTest {

    private static final long ATTENDANCE_ID = 1L;

    private AttendanceJustificationService justificationService;
    private JustificationRetryTemplate template;

    @BeforeEach
    void setUp() {
        justificationService = mock(AttendanceJustificationService.class);
        template = new JustificationRetryTemplate(justificationService);
    }

    @Test
    @DisplayName("succès immédiat : une seule tentative")
    void succesImmediat() {
        JustificationUpdateResult attendu = new JustificationUpdateResult(ATTENDANCE_ID, true, true);
        when(justificationService.updateJustification(anyLong(), anyBoolean(), any()))
                .thenReturn(attendu);

        assertThat(template.updateJustification(ATTENDANCE_ID, true, null)).isSameAs(attendu);
        verify(justificationService, times(1)).updateJustification(anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("échec passager puis succès : l'opération aboutit au second essai")
    void echecPassagerPuisSucces() {
        JustificationUpdateResult attendu = new JustificationUpdateResult(ATTENDANCE_ID, true, true);
        when(justificationService.updateJustification(anyLong(), anyBoolean(), any()))
                .thenThrow(new CannotAcquireLockException("verrou occupé"))
                .thenReturn(attendu);

        assertThat(template.updateJustification(ATTENDANCE_ID, true, null)).isSameAs(attendu);
        verify(justificationService, times(2)).updateJustification(anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("échec passager persistant : trois tentatives puis 409")
    void echecPassagerPersistant() {
        when(justificationService.updateJustification(anyLong(), anyBoolean(), any()))
                .thenThrow(new QueryTimeoutException("délai dépassé"));

        assertThatThrownBy(() -> template.updateJustification(ATTENDANCE_ID, true, null))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT))
                // Le message dit que la modification n'a pas été conservée : c'est l'information
                // utile, pas le détail technique de la panne.
                .hasMessageContaining("trace");

        // Trois tentatives exactement : ni une, ni une boucle sans fin.
        verify(justificationService, times(3)).updateJustification(anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("échec permanent : aucun rejeu, erreur immédiate")
    void echecPermanent() {
        // Rejouer une violation de contrainte ne ferait que reproduire la même erreur trois fois,
        // en laissant l'administrateur attendre plusieurs secondes pour rien.
        when(justificationService.updateJustification(anyLong(), anyBoolean(), any()))
                .thenThrow(new DataIntegrityViolationException("contrainte violée"));

        assertThatThrownBy(() -> template.updateJustification(ATTENDANCE_ID, true, null))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(justificationService, times(1)).updateJustification(anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("fil interrompu pendant l'attente : les tentatives s'arrêtent, 409")
    void filInterrompuPendantAttente() throws Exception {
        // Une interruption est traitée comme la fin des tentatives : le résultat pour
        // l'administrateur est le même — sa correction n'a pas été conservée.
        when(justificationService.updateJustification(anyLong(), anyBoolean(), any()))
                .thenThrow(new CannotAcquireLockException("verrou occupé"));

        Thread worker = new Thread(() -> {
            try {
                template.updateJustification(ATTENDANCE_ID, true, null);
            } catch (RuntimeException attendu) {
                // Attendu : échec d'audit après interruption.
            }
        });
        worker.start();
        // Laisser la première tentative échouer, puis interrompre pendant l'attente.
        Thread.sleep(200);
        worker.interrupt();
        worker.join(3_000);

        assertThat(worker.isAlive()).as("le fil ne s'est pas arrêté après interruption").isFalse();
        // Deux tentatives au plus : la première, puis l'arrêt provoqué par l'interruption.
        verify(justificationService, times(1)).updateJustification(anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("erreur métier : transmise telle quelle, sans rejeu")
    void erreurMetierTransmise() {
        // Une présence introuvable ou une année close ne sont pas des pannes : les rejouer serait
        // absurde, et masquerait la vraie cause derrière un message d'échec d'audit.
        CustomServiceException metier = new CustomServiceException(
                "Présence introuvable pour l'identifiant : 1", HttpStatus.NOT_FOUND);
        when(justificationService.updateJustification(anyLong(), anyBoolean(), any()))
                .thenThrow(metier);

        assertThatThrownBy(() -> template.updateJustification(ATTENDANCE_ID, true, null))
                .isSameAs(metier);
        verify(justificationService, times(1)).updateJustification(anyLong(), anyBoolean(), any());
    }
}
