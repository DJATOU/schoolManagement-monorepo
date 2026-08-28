package com.school.management.service;

import com.school.management.dto.RefundReceiptDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.RefundReceiptIssuanceEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.RefundReceiptIssuanceRepository;
import com.school.management.repository.RefundRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.springframework.data.domain.AuditorAware;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriété de stabilité du reçu de remboursement (exigences 8.6, 8.10).
 *
 * <p>Un reçu est une pièce comptable : deux impressions du même remboursement doivent porter des
 * valeurs identiques, sinon une famille peut détenir deux documents contradictoires sur la même
 * somme. Ce que cent tirages vérifient et qu'un exemple manquerait : la stabilité sur des données
 * variées — noms accentués, montants divers, séries et groupes absents — et sur un nombre
 * quelconque de réimpressions.</p>
 */
class RefundReceiptStabilityPropertyTest {

    private static final long REFUND_ID = 5L;

    private record Montage(RefundReceiptService service,
                           List<RefundReceiptIssuanceEntity> emissions) { }

    private Montage monter(String prenom, String nom, BigDecimal montant,
                           boolean avecSerie, boolean avecGroupe) {
        RefundRepository refundRepository = mock(RefundRepository.class);
        RefundReceiptIssuanceRepository issuanceRepository =
                mock(RefundReceiptIssuanceRepository.class);
        AuditorAware<String> auditor = () -> Optional.of("mme.martin");

        StudentEntity student = new StudentEntity();
        student.setId(7L);
        student.setFirstName(prenom);
        student.setLastName(nom);

        PaymentEntity payment = PaymentEntity.builder()
                .id(9L).student(student).amountPaid(1000.00)
                .paymentDate(new Date(1_770_000_000_000L)).build();
        if (avecGroupe) {
            GroupEntity group = new GroupEntity();
            group.setId(3L);
            group.setName("Maths");
            payment.setGroup(group);
        }
        if (avecSerie) {
            SessionSeriesEntity series = new SessionSeriesEntity();
            series.setId(4L);
            series.setName("Série janvier");
            payment.setSessionSeries(series);
        }

        RefundEntity refund = RefundEntity.builder()
                .id(REFUND_ID).payment(payment).student(student)
                .amount(montant).refundDate(new Date(1_772_000_000_000L))
                .reason("Trop-perçu").refundNumber("REMB-2026-0007").build();
        refund.setActive(true);
        refund.setCreatedBy("mme.martin");

        List<RefundReceiptIssuanceEntity> emissions = new ArrayList<>();

        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        when(issuanceRepository.save(any(RefundReceiptIssuanceEntity.class))).thenAnswer(i -> {
            RefundReceiptIssuanceEntity e = i.getArgument(0);
            emissions.add(e);
            return e;
        });
        // Le magasin est simulé fidèlement : sans cela, le rang resterait figé à 1 et la propriété
        // ne prouverait rien sur les réimpressions.
        when(issuanceRepository.findMaxRank(anyLong())).thenAnswer(i ->
                emissions.stream().mapToInt(RefundReceiptIssuanceEntity::getRank).max().orElse(0));

        return new Montage(
                new RefundReceiptService(refundRepository, issuanceRepository, auditor), emissions);
    }

    // Feature: absence-justification-and-refund-receipts, Property 10: For any refund, two
    // successive productions of its receipt show the same refund number, amount, reason and refund
    // date, and the second is marked as a duplicate.
    @Property(tries = 100)
    void property10_leRecuEstStableEtLeDuplicataSignale(
            @ForAll("prenoms") String prenom,
            @ForAll("noms") String nom,
            @ForAll("montants") BigDecimal montant,
            @ForAll boolean avecSerie,
            @ForAll boolean avecGroupe,
            @ForAll @IntRange(min = 2, max = 5) int productions) {

        Montage m = monter(prenom, nom, montant, avecSerie, avecGroupe);

        RefundReceiptDTO premier = m.service().issue(REFUND_ID);
        assertThat(premier.issuanceRank()).isEqualTo(1);
        assertThat(premier.isDuplicate()).as("la première production ne doit pas être un duplicata")
                .isFalse();

        for (int i = 2; i <= productions; i++) {
            RefundReceiptDTO suivant = m.service().issue(REFUND_ID);

            // Les données de la pièce ne bougent pas, caractère pour caractère.
            assertThat(suivant.refundNumber()).isEqualTo(premier.refundNumber());
            assertThat(suivant.amount()).isEqualByComparingTo(premier.amount());
            assertThat(suivant.reason()).isEqualTo(premier.reason());
            assertThat(suivant.refundDate()).isEqualTo(premier.refundDate());
            assertThat(suivant.studentFirstName()).isEqualTo(premier.studentFirstName());
            assertThat(suivant.studentLastName()).isEqualTo(premier.studentLastName());
            assertThat(suivant.groupName()).isEqualTo(premier.groupName());
            assertThat(suivant.seriesName()).isEqualTo(premier.seriesName());
            assertThat(suivant.recordedBy()).isEqualTo(premier.recordedBy());
            // Le nom de fichier aussi : un duplicata doit se classer à côté de son original.
            assertThat(suivant.fileName()).isEqualTo(premier.fileName());

            // Seul le rang change, et le duplicata est signalé.
            assertThat(suivant.issuanceRank()).isEqualTo(i);
            assertThat(suivant.isDuplicate()).as("production %d non signalée comme duplicata", i)
                    .isTrue();
        }

        // Chaque production a laissé une trace, avec un rang strictement croissant.
        assertThat(m.emissions()).hasSize(productions);
        List<Integer> rangs = m.emissions().stream()
                .map(RefundReceiptIssuanceEntity::getRank).toList();
        for (int i = 1; i < rangs.size(); i++) {
            assertThat(rangs.get(i)).isGreaterThan(rangs.get(i - 1));
        }

        // Le nom de fichier reste utilisable par un système de fichiers, quels que soient les noms.
        assertThat(premier.fileName()).matches("[a-z0-9_.-]+\\.pdf");
    }

    /** Prénoms couvrant accents, apostrophes et espaces. */
    @Provide
    Arbitrary<String> prenoms() {
        return Arbitraries.of("Batoul", "Amélie", "Jean-Pierre", "O'Neil", "  Léa  ", "Ïñaki");
    }

    /** Noms incluant des cas limites de translittération. */
    @Provide
    Arbitrary<String> noms() {
        return Arbitraries.of("Djatou", "O'Brien-Dupré", "Müller", "Çelik", "de la Fontaine", "Ñuñez");
    }

    @Provide
    Arbitrary<BigDecimal> montants() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("999.99")).ofScale(2);
    }
}
