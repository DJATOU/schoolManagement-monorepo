package com.school.management.service;

import com.school.management.dto.RefundCapDTO;
import com.school.management.dto.RefundRequestDTO;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link RefundService}.
 *
 * <p>Couvre le plafond cumulé par paiement (exigence 7), le motif obligatoire (6.1 à 6.3), l'ordre
 * des contrôles (7.5), le paiement introuvable ou sans étudiant (7.6, 7.14), et les champs
 * persistés.</p>
 *
 * <p><strong>Deux comportements ont changé</strong> par rapport à la version antérieure de ce
 * service, et les tests correspondants ont été inversés en conséquence : un montant nul est
 * désormais refusé (il produisait une pièce sans objet), et un motif est exigé (une sortie de caisse
 * sans raison enregistrée n'est pas justifiable lors d'un contrôle).</p>
 */
class RefundServiceTest {

    private static final long PAYMENT_ID = 1L;
    private static final long STUDENT_ID = 2L;
    private static final String MOTIF = "Trop-perçu sur la série de janvier";

    private RefundRepository refundRepository;
    private PaymentRepository paymentRepository;
    private RefundService service;

    private StudentEntity student;

    @BeforeEach
    void setUp() {
        refundRepository = mock(RefundRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        when(refundRepository.saveAndFlush(any(RefundEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(refundRepository.findMaxRankForPrefix(any())).thenReturn(0);
        when(refundRepository.sumActiveRefundsForPayment(anyLong())).thenReturn(BigDecimal.ZERO);

        service = new RefundService(refundRepository, paymentRepository,
                new RefundNumberService(refundRepository));

        student = StudentEntity.builder().id(STUDENT_ID).build();
    }

    private PaymentEntity paymentWithPaid(Double paid) {
        return PaymentEntity.builder()
                .id(PAYMENT_ID)
                .student(student)
                .amountPaid(paid)
                .build();
    }

    private void stubPayment(Double paid) {
        PaymentEntity payment = paymentWithPaid(paid);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
    }

    private static RefundRequestDTO request(BigDecimal amount) {
        return new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, amount, null, MOTIF);
    }

    @Nested
    @DisplayName("Plafond cumulé")
    class PlafondCumule {

        @Test
        @DisplayName("montant égal au plafond restant : accepté")
        void montantEgalAuPlafond() {
            stubPayment(100.0);

            assertThat(service.create(request(new BigDecimal("100.00"))).getAmount())
                    .isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("montant inférieur au plafond : accepté")
        void montantInferieurAuPlafond() {
            stubPayment(100.0);

            assertThat(service.create(request(new BigDecimal("40.00"))).getAmount())
                    .isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("le plafond déduit les remboursements déjà accordés")
        void plafondDeduitLExistant() {
            // C'est le cœur du défaut corrigé : 60 € déjà rendus sur 100 € versés laissent 40 €.
            stubPayment(100.0);
            when(refundRepository.sumActiveRefundsForPayment(PAYMENT_ID))
                    .thenReturn(new BigDecimal("60.00"));

            assertThat(service.create(request(new BigDecimal("40.00"))).getAmount())
                    .isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("dépassement du plafond cumulé : refusé, et le message nomme les trois montants")
        void depassementDuPlafondCumule() {
            stubPayment(100.0);
            when(refundRepository.sumActiveRefundsForPayment(PAYMENT_ID))
                    .thenReturn(new BigDecimal("60.00"));

            assertThatThrownBy(() -> service.create(request(new BigDecimal("40.01"))))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST))
                    // L'administrateur a une famille devant lui : le message doit suffire.
                    .hasMessageContaining("100.00")
                    .hasMessageContaining("60.00")
                    .hasMessageContaining("40.00");
            verify(refundRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("versement entièrement remboursé : plus rien n'est acceptable")
        void versementEntierementRembourse() {
            stubPayment(100.0);
            when(refundRepository.sumActiveRefundsForPayment(PAYMENT_ID))
                    .thenReturn(new BigDecimal("100.00"));

            assertThatThrownBy(() -> service.create(request(new BigDecimal("0.01"))))
                    .isInstanceOf(CustomServiceException.class);
        }
    }

    @Nested
    @DisplayName("Montant")
    class Montant {

        @Test
        @DisplayName("montant nul : refusé (une pièce sans objet)")
        void montantNul() {
            // Comportement INVERSÉ : l'ancienne version acceptait 0 €.
            stubPayment(100.0);

            assertThatThrownBy(() -> service.create(request(new BigDecimal("0.00"))))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        @DisplayName("montant qui devient nul par arrondi : refusé")
        void montantNulApresArrondi() {
            stubPayment(100.0);

            assertThatThrownBy(() -> service.create(request(new BigDecimal("0.004"))))
                    .isInstanceOf(CustomServiceException.class);
        }

        @Test
        @DisplayName("montant négatif : refusé")
        void montantNegatif() {
            stubPayment(100.0);

            assertThatThrownBy(() -> service.create(request(new BigDecimal("-0.01"))))
                    .isInstanceOf(CustomServiceException.class);
        }

        @Test
        @DisplayName("montant absent : refusé")
        void montantAbsent() {
            stubPayment(100.0);

            assertThatThrownBy(() -> service.create(request(null)))
                    .isInstanceOf(CustomServiceException.class);
        }

        @Test
        @DisplayName("montant au-delà de la borne haute : refusé")
        void montantAuDelaDeLaBorneHaute() {
            // La borne haute correspond à la précision de la colonne numeric(12,2) : au-delà,
            // l'enregistrement échouerait en base avec un message incompréhensible.
            stubPayment(100.0);

            assertThatThrownBy(() -> service.create(request(new BigDecimal("1000000000.00"))))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("compris entre");
        }

        @Test
        @DisplayName("montant arrondi à l'échelle monétaire")
        void montantArrondi() {
            stubPayment(100.0);

            RefundEntity refund = service.create(request(new BigDecimal("10.1")));

            assertThat(refund.getAmount().scale()).isEqualTo(2);
            assertThat(refund.getAmount()).isEqualByComparingTo("10.10");
        }

        @Test
        @DisplayName("le montant est validé AVANT le plafond : un montant nul ne parle pas du plafond")
        void ordreDeValidation() {
            // Exigence 7.5. Un message de plafond sur une saisie à 0 € enverrait l'administrateur
            // vérifier des montants alors que le problème est sa saisie.
            stubPayment(0.0);

            assertThatThrownBy(() -> service.create(request(new BigDecimal("0.00"))))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("compris entre");
            // Le plafond n'a même pas été consulté.
            verify(refundRepository, never()).sumActiveRefundsForPayment(anyLong());
        }
    }

    @Nested
    @DisplayName("Motif")
    class Motif {

        @Test
        @DisplayName("motif absent : refusé")
        void motifAbsent() {
            stubPayment(100.0);
            RefundRequestDTO dto =
                    new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), null, null);

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("motif");
        }

        @Test
        @DisplayName("motif composé d'espaces : refusé")
        void motifBlanc() {
            stubPayment(100.0);
            RefundRequestDTO dto =
                    new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), null, "   ");

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(CustomServiceException.class);
        }

        @Test
        @DisplayName("motif de plus de 500 caractères : refusé")
        void motifTropLong() {
            stubPayment(100.0);
            RefundRequestDTO dto = new RefundRequestDTO(
                    PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), null, "x".repeat(501));

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("500");
        }

        @Test
        @DisplayName("motif de 500 caractères exactement : accepté")
        void motifALaLimite() {
            stubPayment(100.0);
            RefundRequestDTO dto = new RefundRequestDTO(
                    PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), null, "x".repeat(500));

            assertThat(service.create(dto).getReason()).hasSize(500);
        }

        @Test
        @DisplayName("le motif est enregistré débarrassé de ses espaces de bord")
        void motifNettoye() {
            stubPayment(100.0);
            RefundRequestDTO dto = new RefundRequestDTO(
                    PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), null, "  Erreur de caisse  ");

            assertThat(service.create(dto).getReason()).isEqualTo("Erreur de caisse");
        }
    }

    @Nested
    @DisplayName("Paiement rattaché")
    class PaiementRattache {

        @Test
        @DisplayName("paiement introuvable : 404")
        void paiementIntrouvable() {
            when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(request(new BigDecimal("10.00"))))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("paiement sans étudiant : refusé, le reçu ne pourrait nommer personne")
        void paiementSansEtudiant() {
            PaymentEntity sansEtudiant = PaymentEntity.builder()
                    .id(PAYMENT_ID).student(null).amountPaid(100.0).build();
            when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(sansEtudiant));

            assertThatThrownBy(() -> service.create(request(new BigDecimal("10.00"))))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        @DisplayName("versement absent traité comme zéro : aucun remboursement possible")
        void versementAbsent() {
            stubPayment(null);

            assertThatThrownBy(() -> service.create(request(new BigDecimal("0.01"))))
                    .isInstanceOf(CustomServiceException.class);
        }

        @Test
        @DisplayName("l'étudiant enregistré vient du paiement, pas de la demande")
        void etudiantIssuDuPaiement() {
            stubPayment(100.0);
            // Un identifiant d'étudiant différent est transmis : il doit être ignoré (exigence 7.11).
            RefundRequestDTO dto =
                    new RefundRequestDTO(PAYMENT_ID, 999L, new BigDecimal("10.00"), null, MOTIF);

            RefundEntity refund = service.create(dto);

            assertThat(refund.getStudent()).isSameAs(student);
            assertThat(refund.getStudent().getId()).isEqualTo(STUDENT_ID);
            assertThat(refund.getPayment().getId()).isEqualTo(PAYMENT_ID);
        }

        @Test
        @DisplayName("requête nulle : NPE")
        void requeteNulle() {
            assertThatThrownBy(() -> service.create(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Date de remboursement")
    class DateDeRemboursement {

        @Test
        @DisplayName("date absente : la date courante est retenue")
        void dateAbsente() {
            stubPayment(100.0);
            Date avant = new Date();

            RefundEntity refund = service.create(request(new BigDecimal("10.00")));
            Date apres = new Date();

            assertThat(refund.getRefundDate()).isNotNull();
            assertThat(refund.getRefundDate()).isBetween(avant, apres, true, true);
        }

        @Test
        @DisplayName("date fournie : elle est conservée")
        void dateFournie() {
            stubPayment(100.0);
            Date fournie = new Date(1_000_000_000_000L);
            RefundRequestDTO dto =
                    new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), fournie, MOTIF);

            assertThat(service.create(dto).getRefundDate()).isEqualTo(fournie);
        }
    }

    @Nested
    @DisplayName("Lecture du plafond")
    class LectureDuPlafond {

        @Test
        @DisplayName("les trois montants sont restitués")
        void troisMontants() {
            stubPayment(100.0);
            when(refundRepository.sumActiveRefundsForPayment(PAYMENT_ID))
                    .thenReturn(new BigDecimal("60.00"));

            RefundCapDTO cap = service.cap(PAYMENT_ID);

            assertThat(cap.amountPaid()).isEqualByComparingTo("100.00");
            assertThat(cap.alreadyRefunded()).isEqualByComparingTo("60.00");
            assertThat(cap.refundableCap()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("paiement introuvable : 404, aucun plafond restitué")
        void paiementIntrouvable() {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cap(PAYMENT_ID))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("Réactivation")
    class Reactivation {

        @Test
        @DisplayName("réactivation qui dépasserait le versement : refusée")
        void reactivationRefusee() {
            // Non atteignable par l'interface actuelle (aucun code ne désactive un remboursement),
            // mais la garde doit exister avant qu'une fonctionnalité d'annulation n'arrive.
            PaymentEntity payment = paymentWithPaid(100.0);
            RefundEntity desactive = RefundEntity.builder()
                    .id(10L).payment(payment).student(student)
                    .amount(new BigDecimal("60.00")).refundNumber("REMB-2026-0001").build();
            desactive.setActive(false);

            when(refundRepository.findById(10L)).thenReturn(Optional.of(desactive));
            when(refundRepository.sumActiveRefundsForPayment(PAYMENT_ID))
                    .thenReturn(new BigDecimal("50.00"));

            assertThatThrownBy(() -> service.reactivate(10L))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
            assertThat(desactive.isActive()).isFalse();
        }

        @Test
        @DisplayName("réactivation qui tient dans le plafond : acceptée")
        void reactivationAcceptee() {
            PaymentEntity payment = paymentWithPaid(100.0);
            RefundEntity desactive = RefundEntity.builder()
                    .id(11L).payment(payment).student(student)
                    .amount(new BigDecimal("40.00")).refundNumber("REMB-2026-0002").build();
            desactive.setActive(false);

            when(refundRepository.findById(11L)).thenReturn(Optional.of(desactive));
            when(refundRepository.sumActiveRefundsForPayment(PAYMENT_ID))
                    .thenReturn(new BigDecimal("50.00"));
            when(refundRepository.save(any(RefundEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.reactivate(11L).isActive()).isTrue();
        }

        @Test
        @DisplayName("remboursement déjà actif : sans effet")
        void dejaActif() {
            RefundEntity actif = RefundEntity.builder()
                    .id(12L).payment(paymentWithPaid(100.0)).student(student)
                    .amount(new BigDecimal("10.00")).refundNumber("REMB-2026-0003").build();
            actif.setActive(true);
            when(refundRepository.findById(12L)).thenReturn(Optional.of(actif));

            assertThat(service.reactivate(12L)).isSameAs(actif);
            verify(refundRepository, never()).save(any());
        }

        @Test
        @DisplayName("remboursement introuvable : 404")
        void introuvable() {
            when(refundRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reactivate(99L))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("remboursement orphelin de paiement : plafond nul, réactivation refusée")
        void sansPaiement() {
            // Cas dégradé : sans paiement rattaché, aucun montant versé ne peut couvrir le
            // remboursement. Le refus est le seul comportement sûr.
            RefundEntity orphelin = RefundEntity.builder()
                    .id(13L).payment(null).student(student)
                    .amount(new BigDecimal("10.00")).refundNumber("REMB-2026-0004").build();
            orphelin.setActive(false);
            when(refundRepository.findById(13L)).thenReturn(Optional.of(orphelin));

            assertThatThrownBy(() -> service.reactivate(13L))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
            assertThat(orphelin.isActive()).isFalse();
        }

        @Test
        @DisplayName("somme des remboursements actifs nulle en base : traitée comme zéro")
        void sommeNulle() {
            // COALESCE protège en principe, mais le service ne doit pas dépendre de cette
            // garantie : une somme nulle doit valoir zéro, pas provoquer un NPE.
            PaymentEntity payment = paymentWithPaid(100.0);
            RefundEntity desactive = RefundEntity.builder()
                    .id(14L).payment(payment).student(student)
                    .amount(new BigDecimal("10.00")).refundNumber("REMB-2026-0005").build();
            desactive.setActive(false);

            when(refundRepository.findById(14L)).thenReturn(Optional.of(desactive));
            when(refundRepository.sumActiveRefundsForPayment(PAYMENT_ID)).thenReturn(null);
            when(refundRepository.save(any(RefundEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.reactivate(14L).isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Numéro de pièce")
    class NumeroDePiece {

        @Test
        @DisplayName("un numéro est attribué à la création")
        void numeroAttribue() {
            stubPayment(100.0);
            when(refundRepository.findMaxRankForPrefix(any())).thenReturn(3);

            assertThat(service.create(request(new BigDecimal("10.00"))).getRefundNumber())
                    .matches("REMB-\\d{4}-0004");
        }

        @Test
        @DisplayName("l'année du numéro suit la date du remboursement")
        void anneeDuNumero() {
            stubPayment(100.0);
            Date en2025 = new Date(1_760_000_000_000L); // octobre 2025
            RefundRequestDTO dto =
                    new RefundRequestDTO(PAYMENT_ID, STUDENT_ID, new BigDecimal("10.00"), en2025, MOTIF);

            assertThat(service.create(dto).getRefundNumber()).startsWith("REMB-2025-");
        }

        @Test
        @DisplayName("collision de numéro : l'enregistrement est rejoué et finit par aboutir")
        void collisionPuisSucces() {
            // Une collision se produit lorsqu'un autre enregistrement a pris le rang entre le
            // calcul et l'écriture. Le rejeu recalcule le rang.
            stubPayment(100.0);
            when(refundRepository.saveAndFlush(any(RefundEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_refund_number"))
                    .thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.create(request(new BigDecimal("10.00")))).isNotNull();
            verify(refundRepository, times(2)).saveAndFlush(any());
        }

        @Test
        @DisplayName("collision persistante : échec explicite après trois tentatives, sans création")
        void collisionPersistante() {
            stubPayment(100.0);
            when(refundRepository.saveAndFlush(any(RefundEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_refund_number"));

            assertThatThrownBy(() -> service.create(request(new BigDecimal("10.00"))))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.CONFLICT))
                    .hasMessageContaining("numéro de pièce");
            // Trois tentatives exactement : ni deux, ni une boucle sans fin.
            verify(refundRepository, times(3)).saveAndFlush(any());
        }
    }

    @Test
    @DisplayName("le service ne consulte pas le devis : un remboursement ne révise pas ce qui est dû")
    void aucunEffetSurLeDevis() {
        // Exigence 7.13. Le service ne dépend d'aucun composant de devis : c'est structurellement
        // ce qui garantit qu'un remboursement ne déplace ni coût, ni statut de paiement.
        stubPayment(100.0);

        service.create(request(new BigDecimal("10.00")));

        verify(refundRepository, never()).sumRefundsForStudentAndSeries(anyLong(), anyLong());
        verify(refundRepository, never()).sumRefundsForGroup(anyLong());
    }
}
