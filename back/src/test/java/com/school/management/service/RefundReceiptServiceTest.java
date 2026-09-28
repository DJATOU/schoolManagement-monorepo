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
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link RefundReceiptService} (exigence 8).
 *
 * <p>Un reçu de remboursement atteste qu'une somme est sortie de la caisse et a été remise à une
 * famille. Les tests portent donc autant sur les mentions de repli — une case vide sur une pièce
 * comptable n'est pas acceptable — que sur le signalement des réimpressions.</p>
 */
class RefundReceiptServiceTest {

    private static final long REFUND_ID = 5L;

    private RefundRepository refundRepository;
    private RefundReceiptIssuanceRepository issuanceRepository;
    private RefundReceiptService service;

    @BeforeEach
    void setUp() {
        refundRepository = mock(RefundRepository.class);
        issuanceRepository = mock(RefundReceiptIssuanceRepository.class);
        AuditorAware<String> auditor = () -> Optional.of("mme.martin");

        when(issuanceRepository.save(any(RefundReceiptIssuanceEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(issuanceRepository.findMaxRank(anyLong())).thenReturn(0);

        service = new RefundReceiptService(refundRepository, issuanceRepository, auditor);
    }

    /** Remboursement complet, cas nominal. */
    private RefundEntity refund(boolean withSeries, boolean withGroup, String createdBy) {
        StudentEntity student = new StudentEntity();
        student.setId(7L);
        student.setFirstName("Batoul");
        student.setLastName("Djatou");

        PaymentEntity payment = PaymentEntity.builder()
                .id(9L)
                .student(student)
                .amountPaid(240.00)
                .paymentDate(new Date(1_770_000_000_000L))
                .build();
        if (withGroup) {
            GroupEntity group = new GroupEntity();
            group.setId(3L);
            group.setName("Maths 1ère année");
            payment.setGroup(group);
        }
        if (withSeries) {
            SessionSeriesEntity series = new SessionSeriesEntity();
            series.setId(4L);
            series.setName("Série janvier");
            payment.setSessionSeries(series);
        }

        RefundEntity refund = RefundEntity.builder()
                .id(REFUND_ID)
                .payment(payment)
                .student(student)
                .amount(new BigDecimal("60.00"))
                .refundDate(new Date(1_772_000_000_000L))
                .reason("Trop-perçu sur la série de janvier")
                .refundNumber("REMB-2026-0007")
                .build();
        refund.setActive(true);
        refund.setCreatedBy(createdBy);
        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        return refund;
    }

    @Nested
    @DisplayName("Contenu du reçu")
    class Contenu {

        @Test
        @DisplayName("toutes les données obligatoires sont présentes")
        void donneesObligatoires() {
            refund(true, true, "mme.martin");

            RefundReceiptDTO receipt = service.issue(REFUND_ID);

            assertThat(receipt.refundNumber()).isEqualTo("REMB-2026-0007");
            assertThat(receipt.amount()).isEqualByComparingTo("60.00");
            assertThat(receipt.reason()).isEqualTo("Trop-perçu sur la série de janvier");
            assertThat(receipt.studentFirstName()).isEqualTo("Batoul");
            assertThat(receipt.studentLastName()).isEqualTo("Djatou");
            assertThat(receipt.refundDate()).isNotNull();
            assertThat(receipt.groupName()).isEqualTo("Maths 1ère année");
            assertThat(receipt.seriesName()).isEqualTo("Série janvier");
            assertThat(receipt.recordedBy()).isEqualTo("mme.martin");
            // Référence du versement d'origine : elle rattache le reçu à l'encaissement.
            assertThat(receipt.amountPaid()).isEqualByComparingTo("240.00");
            assertThat(receipt.paymentDate()).isNotNull();
        }

        @Test
        @DisplayName("les montants sont à l'échelle monétaire, décimales nulles comprises")
        void montantsAEchelleMonetaire() {
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.setAmount(new BigDecimal("60"));

            RefundReceiptDTO receipt = service.issue(REFUND_ID);

            assertThat(receipt.amount().scale()).isEqualTo(2);
            assertThat(receipt.amountPaid().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("montant du remboursement absent : imprimé à zéro plutôt qu'omis")
        void montantAbsent() {
            // Une case vide sur une pièce de caisse est pire qu'un zéro : elle laisse penser à un
            // défaut d'impression plutôt qu'à une donnée manquante.
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.setAmount(null);

            assertThat(service.issue(REFUND_ID).amount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("montant du versement d'origine absent : imprimé à zéro")
        void montantVersementAbsent() {
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.getPayment().setAmountPaid(null);

            assertThat(service.issue(REFUND_ID).amountPaid()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Mentions de repli")
    class Replis {

        @Test
        @DisplayName("versement sans série : « Hors série »")
        void sansSerie() {
            refund(false, true, "mme.martin");

            assertThat(service.issue(REFUND_ID).seriesName()).isEqualTo("Hors série");
        }

        @Test
        @DisplayName("versement sans groupe : « Hors groupe »")
        void sansGroupe() {
            refund(true, false, "mme.martin");

            assertThat(service.issue(REFUND_ID).groupName()).isEqualTo("Hors groupe");
        }

        @Test
        @DisplayName("série au nom vide : traitée comme absente")
        void serieSansNom() {
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.getPayment().getSessionSeries().setName("  ");

            assertThat(service.issue(REFUND_ID).seriesName()).isEqualTo("Hors série");
        }

        @Test
        @DisplayName("groupe au nom vide : traité comme absent")
        void groupeSansNom() {
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.getPayment().getGroup().setName(null);

            assertThat(service.issue(REFUND_ID).groupName()).isEqualTo("Hors groupe");
        }

        @Test
        @DisplayName("auteur system : mention lisible, pas l'identifiant technique")
        void auteurSystem() {
            // Imprimer « system » sur un reçu remis à une famille laisserait croire à un nom d'agent.
            refund(true, true, "system");

            assertThat(service.issue(REFUND_ID).recordedBy())
                    .isEqualTo("Administrateur non identifié");
        }

        @Test
        @DisplayName("auteur absent : mention lisible")
        void auteurAbsent() {
            refund(true, true, null);

            assertThat(service.issue(REFUND_ID).recordedBy())
                    .isEqualTo("Administrateur non identifié");
        }

        @Test
        @DisplayName("remboursement sans versement rattaché : montant versé nul, replis appliqués")
        void sansVersement() {
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.setPayment(null);

            RefundReceiptDTO receipt = service.issue(REFUND_ID);

            assertThat(receipt.amountPaid()).isEqualByComparingTo("0.00");
            assertThat(receipt.paymentDate()).isNull();
            assertThat(receipt.groupName()).isEqualTo("Hors groupe");
            assertThat(receipt.seriesName()).isEqualTo("Hors série");
        }

        @Test
        @DisplayName("remboursement sans étudiant : production possible, noms vides")
        void sansEtudiant() {
            // La production ne doit pas échouer : le reçu existe pour attester d'une sortie de
            // caisse, et son absence serait plus gênante qu'un nom manquant.
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.setStudent(null);

            RefundReceiptDTO receipt = service.issue(REFUND_ID);

            assertThat(receipt.studentFirstName()).isEmpty();
            assertThat(receipt.studentLastName()).isEmpty();
            assertThat(receipt.fileName()).contains("etudiant");
        }
    }

    @Nested
    @DisplayName("Duplicata")
    class Duplicata {

        @Test
        @DisplayName("première production : rang 1, pas un duplicata")
        void premiereProduction() {
            refund(true, true, "mme.martin");

            RefundReceiptDTO receipt = service.issue(REFUND_ID);

            assertThat(receipt.issuanceRank()).isEqualTo(1);
            assertThat(receipt.isDuplicate()).isFalse();
            assertThat(receipt.issuedAt()).isNotNull();
        }

        @Test
        @DisplayName("réimpression : rang supérieur et duplicata signalé")
        void reimpression() {
            // Un reçu de caisse réimprimé peut servir deux fois : la mention est le seul garde-fou.
            refund(true, true, "mme.martin");
            when(issuanceRepository.findMaxRank(REFUND_ID)).thenReturn(2);

            RefundReceiptDTO receipt = service.issue(REFUND_ID);

            assertThat(receipt.issuanceRank()).isEqualTo(3);
            assertThat(receipt.isDuplicate()).isTrue();
        }

        @Test
        @DisplayName("chaque production est enregistrée avec son auteur")
        void productionEnregistree() {
            refund(true, true, "mme.martin");

            service.issue(REFUND_ID);

            var captor = org.mockito.ArgumentCaptor.forClass(RefundReceiptIssuanceEntity.class);
            verify(issuanceRepository).save(captor.capture());
            RefundReceiptIssuanceEntity issuance = captor.getValue();
            assertThat(issuance.getRank()).isEqualTo(1);
            assertThat(issuance.getIssuedBy()).isEqualTo("mme.martin");
            assertThat(issuance.getIssuedAt()).isNotNull();
            assertThat(issuance.getRefund().getId()).isEqualTo(REFUND_ID);
        }
    }

    @Nested
    @DisplayName("Nom de fichier")
    class NomDeFichier {

        @Test
        @DisplayName("dérivé du numéro de pièce et du nom de l'étudiant")
        void deriveDuNumeroEtDuNom() {
            refund(true, true, "mme.martin");

            assertThat(service.issue(REFUND_ID).fileName())
                    .isEqualTo("remb-2026-0007_batoul_djatou.pdf");
        }

        @Test
        @DisplayName("accents et ponctuation retirés : certains systèmes refusent ces noms")
        void accentsRetires() {
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.getStudent().setFirstName("Amélie");
            refund.getStudent().setLastName("O'Brien-Dupré");

            assertThat(service.issue(REFUND_ID).fileName())
                    .isEqualTo("remb-2026-0007_amelie_obrien-dupre.pdf")
                    .matches("[a-z0-9_.-]+");
        }

        @Test
        @DisplayName("identique d'une production à l'autre : le duplicata se classe avec l'original")
        void stableEntreProductions() {
            refund(true, true, "mme.martin");
            String premier = service.issue(REFUND_ID).fileName();

            when(issuanceRepository.findMaxRank(REFUND_ID)).thenReturn(1);
            String second = service.issue(REFUND_ID).fileName();

            assertThat(second).isEqualTo(premier);
        }

        @Test
        @DisplayName("nom très long : tronqué à la limite")
        void nomTronque() {
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.getStudent().setLastName("A".repeat(300));

            assertThat(service.issue(REFUND_ID).fileName().length()).isLessThanOrEqualTo(155);
        }

        @Test
        @DisplayName("nom entièrement non translittérable : repli sur « etudiant »")
        void nomNonTransliterable() {
            // Un nom composé de caractères tous retirés par le nettoyage produirait un nom de
            // fichier vide, que le système refuserait.
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.getStudent().setFirstName("陈");
            refund.getStudent().setLastName("");

            assertThat(service.issue(REFUND_ID).fileName())
                    .isEqualTo("remb-2026-0007_etudiant.pdf");
        }

        @Test
        @DisplayName("noms absents : repli sur « etudiant »")
        void nomsAbsents() {
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.getStudent().setFirstName(null);
            refund.getStudent().setLastName(null);

            assertThat(service.issue(REFUND_ID).fileName()).contains("etudiant");
        }

        @Test
        @DisplayName("numéro de pièce absent : le nom de fichier reste exploitable")
        void numeroAbsent() {
            // Ne devrait pas arriver, la colonne étant NOT NULL, mais un nom de fichier vide
            // empêcherait purement et simplement la remise du justificatif.
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.setRefundNumber(null);

            assertThat(service.issue(REFUND_ID).fileName()).endsWith("batoul_djatou.pdf");
        }
    }

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("remboursement introuvable : 404, aucune émission enregistrée")
        void introuvable() {
            when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.issue(REFUND_ID))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
            verify(issuanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("remboursement inactif : 404, aucune donnée partielle")
        void inactif() {
            RefundEntity refund = refund(true, true, "mme.martin");
            refund.setActive(false);

            assertThatThrownBy(() -> service.issue(REFUND_ID))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
            verify(issuanceRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("auteur d'émission inconnu : repli system enregistré")
    void auteurEmissionInconnu() {
        RefundReceiptService sansAuteur = new RefundReceiptService(
                refundRepository, issuanceRepository, Optional::empty);
        refund(true, true, "mme.martin");

        sansAuteur.issue(REFUND_ID);

        var captor = org.mockito.ArgumentCaptor.forClass(RefundReceiptIssuanceEntity.class);
        verify(issuanceRepository).save(captor.capture());
        assertThat(captor.getValue().getIssuedBy()).isEqualTo("system");
    }
}
