package com.school.management.service;

import com.school.management.dto.JustificationUpdateResult;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.AttendanceJustificationAuditEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.repository.AttendanceJustificationAuditRepository;
import com.school.management.repository.AttendanceRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.data.domain.AuditorAware;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriétés de la modification de justification et de sa piste d'audit (exigences 4.3, 5.1, 5.3,
 * 5.8).
 *
 * <p>Ce que cent tirages trouvent et que deux exemples manquent : les <strong>suites</strong> de
 * modifications. Une seule modification est facile à vérifier ; c'est l'enchaînement de valeurs
 * identiques et différentes qui révèle un audit qui consigne trop, ou une valeur courante
 * désalignée de la dernière entrée.</p>
 *
 * <p>Le magasin d'audit est simulé fidèlement — les entrées s'accumulent et le rang maximum est
 * relu — sans quoi la propriété testerait un compteur figé et ne prouverait rien.</p>
 */
class JustificationPropertyTest {

    private static final long ATTENDANCE_ID = 1L;

    /** Montage complet avec magasin d'audit en mémoire. */
    private record Montage(AttendanceJustificationService service,
                           AttendanceEntity attendance,
                           List<AttendanceJustificationAuditEntity> entries) { }

    private Montage monter(Boolean valeurInitiale) {
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceJustificationAuditRepository auditRepository =
                mock(AttendanceJustificationAuditRepository.class);
        ReadOnlyYearGuard guard = mock(ReadOnlyYearGuard.class);
        AuditorAware<String> auditor = () -> Optional.of("mme.martin");

        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setId(ATTENDANCE_ID);
        attendance.setIsPresent(false);
        attendance.setActive(true);
        attendance.setIsJustified(valeurInitiale);
        SessionEntity session = new SessionEntity();
        session.setId(50L);
        attendance.setSession(session);

        List<AttendanceJustificationAuditEntity> entries = new ArrayList<>();

        when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(any(AttendanceEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(auditRepository.save(any(AttendanceJustificationAuditEntity.class))).thenAnswer(i -> {
            AttendanceJustificationAuditEntity entry = i.getArgument(0);
            entry.setId((long) (entries.size() + 1));
            entries.add(entry);
            return entry;
        });
        when(auditRepository.findMaxSequenceRank(anyLong())).thenAnswer(i ->
                entries.stream().mapToLong(AttendanceJustificationAuditEntity::getSequenceRank)
                        .max().orElse(0L));

        return new Montage(new AttendanceJustificationService(
                attendanceRepository, auditRepository, guard, auditor), attendance, entries);
    }

    // Feature: absence-justification-and-refund-receipts, Property 4: For any attendance and any
    // justification value, applying the same value twice yields the same state and a single audit
    // entry.
    @Property(tries = 100)
    void property4_reappliquerLaMemeValeurNEcritQuUneFois(
            @ForAll("valeurInitiale") Boolean initiale,
            @ForAll boolean demandee) {

        Montage m = monter(initiale);

        JustificationUpdateResult premier = m.service()
                .updateJustification(ATTENDANCE_ID, demandee, "premier appel");
        int apresPremier = m.entries().size();

        JustificationUpdateResult second = m.service()
                .updateJustification(ATTENDANCE_ID, demandee, "second appel");

        // Le second appel ne change rien : même état, aucune entrée supplémentaire.
        assertThat(second.changed()).as("le second appel a modifié la présence").isFalse();
        assertThat(second.justified()).isEqualTo(premier.justified());
        assertThat(m.attendance().getIsJustified()).isEqualTo(demandee);
        assertThat(m.entries()).as("une entrée d'audit a été créée sans changement de valeur")
                .hasSize(apresPremier);
    }

    // Feature: absence-justification-and-refund-receipts, Property 5: For any sequence of
    // justification changes, the current value equals the value applied by the latest audit entry,
    // and the number of entries equals the number of effective value changes.
    @Property(tries = 100)
    void property5_valeurCouranteAlignéeSurLaDerniereEntree(
            @ForAll("valeurInitiale") Boolean initiale,
            @ForAll("suiteDeValeurs") List<Boolean> suite) {

        Montage m = monter(initiale);

        Boolean attendue = initiale;
        int changementsEffectifs = 0;

        for (Boolean demandee : suite) {
            JustificationUpdateResult result =
                    m.service().updateJustification(ATTENDANCE_ID, demandee, null);

            boolean changementAttendu = attendue == null || attendue != demandee;
            assertThat(result.changed())
                    .as("changement signalé %s alors que %s -> %s", result.changed(), attendue, demandee)
                    .isEqualTo(changementAttendu);

            if (changementAttendu) {
                changementsEffectifs++;
            }
            attendue = demandee;

            // Invariant après CHAQUE étape, pas seulement à la fin.
            assertThat(m.attendance().getIsJustified()).isEqualTo(attendue);
            assertThat(m.entries()).hasSize(changementsEffectifs);
        }

        if (!m.entries().isEmpty()) {
            // Exigence 5.8 : la valeur courante égale celle appliquée par l'entrée la plus récente,
            // départagée par le rang de séquence à horodatage égal.
            AttendanceJustificationAuditEntity derniere = m.entries().stream()
                    .max(Comparator.comparing(AttendanceJustificationAuditEntity::getPerformedAt)
                            .thenComparing(AttendanceJustificationAuditEntity::getSequenceRank))
                    .orElseThrow();
            assertThat(m.attendance().getIsJustified()).isEqualTo(derniere.getNewValue());

            // Les rangs sont strictement croissants : c'est ce qui rend l'ordre déterministe même
            // quand plusieurs modifications tombent dans la même milliseconde.
            List<Long> rangs = m.entries().stream()
                    .map(AttendanceJustificationAuditEntity::getSequenceRank).toList();
            for (int i = 1; i < rangs.size(); i++) {
                assertThat(rangs.get(i)).isGreaterThan(rangs.get(i - 1));
            }
        }
    }

    // Feature: absence-justification-and-refund-receipts, Property 6: For any sequence of
    // justification changes, each audit entry's previous value equals the value left by the
    // preceding change, so the trail reconstructs the full history without gaps.
    @Property(tries = 100)
    void property6_lHistoriqueSeRecomposeSansTrou(
            @ForAll("valeurInitiale") Boolean initiale,
            @ForAll("suiteDeValeurs") List<Boolean> suite) {

        Montage m = monter(initiale);
        for (Boolean demandee : suite) {
            m.service().updateJustification(ATTENDANCE_ID, demandee, null);
        }

        // Chaque entrée reprend la valeur laissée par la précédente : la chaîne est continue, on
        // peut donc remonter l'historique complet depuis l'état initial.
        Boolean precedente = initiale;
        for (AttendanceJustificationAuditEntity entry : m.entries()) {
            assertThat(entry.getOldValue())
                    .as("rupture de chaîne : entrée partant de %s au lieu de %s",
                            entry.getOldValue(), precedente)
                    .isEqualTo(precedente);
            assertThat(entry.getNewValue()).isNotEqualTo(entry.getOldValue());
            precedente = entry.getNewValue();
        }
        assertThat(m.attendance().getIsJustified()).isEqualTo(precedente);
    }

    /** Valeur initiale : non renseignée, justifiée ou injustifiée. Les trois doivent être couvertes. */
    @Provide
    Arbitrary<Boolean> valeurInitiale() {
        return Arbitraries.of(null, Boolean.TRUE, Boolean.FALSE);
    }

    /** Suites mêlant répétitions et alternances : c'est là que se cachent les défauts d'audit. */
    @Provide
    Arbitrary<List<Boolean>> suiteDeValeurs() {
        return Arbitraries.of(Boolean.TRUE, Boolean.FALSE).list().ofMinSize(1).ofMaxSize(6);
    }
}
