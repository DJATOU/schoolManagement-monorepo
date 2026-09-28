package com.school.management.service;

import com.school.management.dto.JustificationAuditDTO;
import com.school.management.dto.JustificationUpdateResult;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.AttendanceJustificationAuditEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.repository.AttendanceJustificationAuditRepository;
import com.school.management.repository.AttendanceRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link AttendanceJustificationService} (exigences 4 et 5).
 *
 * <p>Ce service répare une situation où la justification n'était modifiable par aucun chemin :
 * l'ancien point d'entrée réussissait sans rien changer. Les tests couvrent donc autant les refus
 * — qui protègent la donnée — que le chemin nominal.</p>
 */
class AttendanceJustificationServiceTest {

    private static final long ATTENDANCE_ID = 1L;

    private AttendanceRepository attendanceRepository;
    private AttendanceJustificationAuditRepository auditRepository;
    private ReadOnlyYearGuard readOnlyYearGuard;
    private AttendanceJustificationService service;

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        auditRepository = mock(AttendanceJustificationAuditRepository.class);
        readOnlyYearGuard = mock(ReadOnlyYearGuard.class);
        AuditorAware<String> auditorAware = () -> Optional.of("mme.martin");

        when(attendanceRepository.save(any(AttendanceEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(auditRepository.save(any(AttendanceJustificationAuditEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(auditRepository.findMaxSequenceRank(anyLong())).thenReturn(0L);

        service = new AttendanceJustificationService(
                attendanceRepository, auditRepository, readOnlyYearGuard, auditorAware);
    }

    /** Absence active, cas normal pour ce service. */
    private AttendanceEntity absence(Boolean justified) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setId(ATTENDANCE_ID);
        attendance.setIsPresent(false);
        attendance.setActive(true);
        attendance.setIsJustified(justified);
        SessionEntity session = new SessionEntity();
        session.setId(50L);
        attendance.setSession(session);
        when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));
        return attendance;
    }

    @Nested
    @DisplayName("Modification appliquée")
    class ModificationAppliquee {

        @Test
        @DisplayName("non justifiée → justifiée : valeur appliquée et trace écrite")
        void deNonJustifieeAJustifiee() {
            AttendanceEntity attendance = absence(false);

            JustificationUpdateResult result =
                    service.updateJustification(ATTENDANCE_ID, true, "Certificat médical remis");

            assertThat(result.changed()).isTrue();
            assertThat(result.justified()).isTrue();
            assertThat(attendance.getIsJustified()).isTrue();

            var captor = org.mockito.ArgumentCaptor.forClass(AttendanceJustificationAuditEntity.class);
            verify(auditRepository).save(captor.capture());
            AttendanceJustificationAuditEntity trace = captor.getValue();
            assertThat(trace.getAttendanceId()).isEqualTo(ATTENDANCE_ID);
            assertThat(trace.getOldValue()).isFalse();
            assertThat(trace.getNewValue()).isTrue();
            assertThat(trace.getPerformedBy()).isEqualTo("mme.martin");
            assertThat(trace.getComment()).isEqualTo("Certificat médical remis");
            assertThat(trace.getSequenceRank()).isEqualTo(1L);
            assertThat(trace.getPerformedAt()).isNotNull();
        }

        @Test
        @DisplayName("justification non renseignée → justifiée : valeur antérieure nulle conservée")
        void deNonRenseigneeAJustifiee() {
            // Nul et « non » sont deux choses différentes : la trace doit le refléter, sinon on ne
            // peut plus distinguer « jamais saisi » de « explicitement injustifié ».
            absence(null);

            JustificationUpdateResult result = service.updateJustification(ATTENDANCE_ID, true, null);

            assertThat(result.changed()).isTrue();
            var captor = org.mockito.ArgumentCaptor.forClass(AttendanceJustificationAuditEntity.class);
            verify(auditRepository).save(captor.capture());
            assertThat(captor.getValue().getOldValue()).isNull();
        }

        @Test
        @DisplayName("le rang de séquence suit le maximum déjà attribué")
        void rangDeSequenceIncremente() {
            absence(false);
            when(auditRepository.findMaxSequenceRank(ATTENDANCE_ID)).thenReturn(7L);

            service.updateJustification(ATTENDANCE_ID, true, null);

            var captor = org.mockito.ArgumentCaptor.forClass(AttendanceJustificationAuditEntity.class);
            verify(auditRepository).save(captor.capture());
            assertThat(captor.getValue().getSequenceRank()).isEqualTo(8L);
        }

        @Test
        @DisplayName("commentaire vide ou blanc : enregistré comme absent")
        void commentaireBlanc() {
            absence(false);

            service.updateJustification(ATTENDANCE_ID, true, "   ");

            var captor = org.mockito.ArgumentCaptor.forClass(AttendanceJustificationAuditEntity.class);
            verify(auditRepository).save(captor.capture());
            assertThat(captor.getValue().getComment()).isNull();
        }

        @Test
        @DisplayName("commentaire de 500 caractères : accepté")
        void commentaireALaLimite() {
            absence(false);

            service.updateJustification(ATTENDANCE_ID, true, "x".repeat(500));

            verify(auditRepository).save(any());
        }
    }

    @Nested
    @DisplayName("Valeur identique")
    class ValeurIdentique {

        @Test
        @DisplayName("succès sans écriture ni trace : la piste ne consigne que les vrais changements")
        void valeurIdentiqueNEcritRien() {
            absence(true);

            JustificationUpdateResult result = service.updateJustification(ATTENDANCE_ID, true, "peu importe");

            assertThat(result.changed()).isFalse();
            assertThat(result.justified()).isTrue();
            verify(attendanceRepository, never()).save(any());
            verify(auditRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("présence introuvable : 404, aucune écriture")
        void presenceIntrouvable() {
            when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateJustification(ATTENDANCE_ID, true, null))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("présence marquée présent : 400, la justification ne concerne qu'une absence")
        void presenceMarqueePresent() {
            AttendanceEntity presente = new AttendanceEntity();
            presente.setId(ATTENDANCE_ID);
            presente.setIsPresent(true);
            presente.setActive(true);
            when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(presente));

            assertThatThrownBy(() -> service.updateJustification(ATTENDANCE_ID, true, null))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("absence");
            verify(attendanceRepository, never()).save(any());
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("présence désactivée : 400")
        void presenceDesactivee() {
            AttendanceEntity desactivee = new AttendanceEntity();
            desactivee.setId(ATTENDANCE_ID);
            desactivee.setIsPresent(false);
            desactivee.setActive(false);
            when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(desactivee));

            assertThatThrownBy(() -> service.updateJustification(ATTENDANCE_ID, true, null))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("désactivée");
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("année scolaire close : refus, aligné sur le garde-fou existant")
        void anneeScolaireClose() {
            absence(false);
            doThrow(new ReadOnlySchoolYearException())
                    .when(readOnlyYearGuard).assertSessionMutable(any());

            assertThatThrownBy(() -> service.updateJustification(ATTENDANCE_ID, true, null))
                    .isInstanceOf(ReadOnlySchoolYearException.class);
            verify(attendanceRepository, never()).save(any());
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("commentaire de plus de 500 caractères : 400, aucune écriture")
        void commentaireTropLong() {
            absence(false);

            assertThatThrownBy(() -> service.updateJustification(ATTENDANCE_ID, true, "x".repeat(501)))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("500");
            verify(attendanceRepository, never()).save(any());
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("le contrôle d'année précède la validation du commentaire")
        void ordreDesControles() {
            // Un commentaire trop long sur une année close doit signaler l'année : c'est le blocage
            // structurant, celui que l'administrateur ne peut pas contourner en raccourcissant son texte.
            absence(false);
            doThrow(new ReadOnlySchoolYearException())
                    .when(readOnlyYearGuard).assertSessionMutable(any());

            assertThatThrownBy(() -> service.updateJustification(ATTENDANCE_ID, true, "x".repeat(501)))
                    .isInstanceOf(ReadOnlySchoolYearException.class);
        }
    }

    @Nested
    @DisplayName("Piste d'audit")
    class PisteDAudit {

        @Test
        @DisplayName("restituée du plus récent au plus ancien")
        void resitutionOrdonnee() {
            when(auditRepository.findTrail(ATTENDANCE_ID)).thenReturn(List.of(
                    AttendanceJustificationAuditEntity.builder()
                            .id(2L).attendanceId(ATTENDANCE_ID).oldValue(true).newValue(false)
                            .performedBy("mme.martin").performedAt(LocalDateTime.now())
                            .sequenceRank(2L).comment("Retrait après vérification").build(),
                    AttendanceJustificationAuditEntity.builder()
                            .id(1L).attendanceId(ATTENDANCE_ID).oldValue(false).newValue(true)
                            .performedBy("m.dupont").performedAt(LocalDateTime.now().minusDays(1))
                            .sequenceRank(1L).comment(null).build()));

            List<JustificationAuditDTO> trail = service.auditTrail(ATTENDANCE_ID);

            assertThat(trail).hasSize(2);
            assertThat(trail.get(0).id()).isEqualTo(2L);
            assertThat(trail.get(0).performedBy()).isEqualTo("mme.martin");
            assertThat(trail.get(1).comment()).isNull();
        }

        @Test
        @DisplayName("présence jamais modifiée : collection vide, pas d'erreur")
        void aucuneEntree() {
            when(auditRepository.findTrail(ATTENDANCE_ID)).thenReturn(List.of());

            assertThat(service.auditTrail(ATTENDANCE_ID)).isEmpty();
        }
    }

    @Test
    @DisplayName("aucun montant n'est consulté : la justification est sans effet financier")
    void aucunEffetFinancier() {
        // Exigences 3.1 à 3.3. L'indépendance est structurelle : le service ne reçoit aucun
        // composant de devis ni de calcul de coût. Aucune évolution ne peut donc y introduire un
        // effet financier sans modifier son constructeur, ce qui se verrait en revue.
        absence(false);

        service.updateJustification(ATTENDANCE_ID, true, null);

        assertThat(AttendanceJustificationService.class.getDeclaredConstructors()[0].getParameterTypes())
                .containsExactly(AttendanceRepository.class,
                        AttendanceJustificationAuditRepository.class,
                        ReadOnlyYearGuard.class,
                        AuditorAware.class);
    }

    @Test
    @DisplayName("auteur inconnu : repli sur system")
    void auteurInconnu() {
        AuditorAware<String> sansAuteur = Optional::empty;
        AttendanceJustificationService sansAuteurService = new AttendanceJustificationService(
                attendanceRepository, auditRepository, readOnlyYearGuard, sansAuteur);
        absence(false);

        sansAuteurService.updateJustification(ATTENDANCE_ID, true, null);

        var captor = org.mockito.ArgumentCaptor.forClass(AttendanceJustificationAuditEntity.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getPerformedBy()).isEqualTo("system");
    }
}
