package com.school.management.service.payment;

import com.school.management.persistance.PaymentCarryOverEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PaymentIdempotencyEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.PaymentIdempotencyRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Distinction entre le rejeu d'un encaissement et un second encaissement réel.
 *
 * <p>Le cas que ces tests protègent : un double clic sur « Encaisser » et un second versement du
 * même montant le même jour produisent une requête rigoureusement identique. Accepter les deux
 * inscrit au registre de l'argent jamais entré en caisse ; refuser les deux perd un versement
 * réel. Seule la clé, engendrée à l'ouverture du formulaire, les sépare.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Idempotence des encaissements")
class PaymentIdempotencyServiceTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long GROUP_ID = 3L;
    private static final Long SERIES_ID = 10L;
    private static final String KEY = "a3f1c0de-4b2e-4d8a-9f10-77c2b5e6a001";

    @Mock private PaymentIdempotencyRepository idempotencyRepository;
    @Mock private PaymentCarryOverRepository carryOverRepository;

    @InjectMocks private PaymentIdempotencyService service;

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private PaymentIdempotencyEntity record(String allocated) {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(55L);
        return PaymentIdempotencyEntity.builder()
                .idempotencyKey(KEY)
                .studentId(STUDENT_ID)
                .groupId(GROUP_ID)
                .sessionSeriesId(SERIES_ID)
                .amountReceived(money("6000.00"))
                .amountAllocated(money(allocated))
                .payment(payment)
                .originPaymentDate(new Date(1_700_000_000_000L))
                .build();
    }

    // ------------------------------------------------------------------
    // Absence de clé : comportement d'avant conservé
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Sans clé")
    class SansCle {

        @Test
        @DisplayName("clé nulle : aucun rejeu, la base n'est même pas consultée")
        void cleNulle() {
            assertThat(service.normalizeKey(null)).isNull();
            assertThat(service.findReplay(null, STUDENT_ID, GROUP_ID, SERIES_ID, money("6000.00")))
                    .isEmpty();
            verifyNoInteractions(idempotencyRepository, carryOverRepository);
        }

        @Test
        @DisplayName("clé nulle : rien n'est conservé, donc rien ne bloquera un versement ultérieur")
        void rienConserve() {
            service.remember(null, null, new Date());
            verify(idempotencyRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------
    // Forme de la clé
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Forme de la clé")
    class FormeDeLaCle {

        @Test
        @DisplayName("clé entourée d'espaces : nettoyée, pour que deux rejeux se reconnaissent")
        void cleNettoyee() {
            assertThat(service.normalizeKey("  " + KEY + "  ")).isEqualTo(KEY);
        }

        @Test
        @DisplayName("clé vide : 400 — ce n'est pas une absence de clé mais un client qui a cru en envoyer une")
        void cleVide() {
            // La traiter comme une absence rétablirait en silence le double encaissement que
            // l'appelant cherchait justement à éviter.
            assertThatThrownBy(() -> service.normalizeKey("   "))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("vide")
                    .extracting(e -> ((CustomServiceException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("clé de plus de 100 caractères : 400, avant toute écriture")
        void cleTropLongue() {
            assertThatThrownBy(() -> service.normalizeKey("x".repeat(101)))
                    .isInstanceOf(CustomServiceException.class)
                    .extracting(e -> ((CustomServiceException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            verifyNoInteractions(idempotencyRepository);
        }
    }

    // ------------------------------------------------------------------
    // Premier encaissement
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Premier encaissement")
    class PremierEncaissement {

        @Test
        @DisplayName("clé inconnue : aucun rejeu, le traitement normal doit avoir lieu")
        void cleInconnue() {
            when(idempotencyRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

            assertThat(service.findReplay(KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("6000.00")))
                    .isEmpty();
            verifyNoInteractions(carryOverRepository);
        }

        @Test
        @DisplayName("empreinte conservée avec la requête et son résultat")
        void empreinteConservee() {
            PaymentEntity payment = new PaymentEntity();
            payment.setId(55L);
            Date paymentDate = new Date(1_700_000_000_000L);
            PaymentAllocationResult result = new PaymentAllocationResult(STUDENT_ID, GROUP_ID,
                    SERIES_ID, money("6000.00"), money("4000.00"), List.of(), payment);

            service.remember(KEY, result, paymentDate);

            ArgumentCaptor<PaymentIdempotencyEntity> captor =
                    ArgumentCaptor.forClass(PaymentIdempotencyEntity.class);
            verify(idempotencyRepository).save(captor.capture());
            PaymentIdempotencyEntity saved = captor.getValue();
            assertThat(saved.getIdempotencyKey()).isEqualTo(KEY);
            assertThat(saved.getStudentId()).isEqualTo(STUDENT_ID);
            assertThat(saved.getSessionSeriesId()).isEqualTo(SERIES_ID);
            assertThat(saved.getAmountReceived()).isEqualByComparingTo("6000.00");
            assertThat(saved.getAmountAllocated()).isEqualByComparingTo("4000.00");
            assertThat(saved.getPayment()).isSameAs(payment);
            // L'horodatage est celui du serveur, seule clé de relecture des reports.
            assertThat(saved.getOriginPaymentDate()).isEqualTo(paymentDate);
        }
    }

    // ------------------------------------------------------------------
    // Rejeu
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Rejeu de la même soumission")
    class Rejeu {

        @Test
        @DisplayName("même clé et même requête : le résultat original est restitué, sans nouveau versement")
        void memeRequete() {
            when(idempotencyRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(record("4000.00")));
            when(carryOverRepository
                    .findByStudentIdAndSourceSeriesIdAndOriginPaymentDateAndActiveTrueOrderByIdAsc(
                            anyLong(), anyLong(), any()))
                    .thenReturn(List.of());

            Optional<PaymentAllocationResult> replay = service.findReplay(
                    KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("6000.00"));

            assertThat(replay).isPresent();
            PaymentAllocationResult result = replay.orElseThrow();
            assertThat(result.amountReceived()).isEqualByComparingTo("6000.00");
            assertThat(result.amountAllocated()).isEqualByComparingTo("4000.00");
            assertThat(result.payment().getId()).isEqualTo(55L);
            // Aucune écriture : c'est tout l'objet du mécanisme.
            verify(idempotencyRepository, never()).save(any());
        }

        @Test
        @DisplayName("le rejeu restitue les reports depuis la table qui fait foi, pas depuis une copie")
        void reportsRelus() {
            // Recopier le détail des reports le ferait diverger de payment_carry_over, que
            // consultent les relevés et l'historique de l'étudiant.
            SessionSeriesEntity target = new SessionSeriesEntity();
            target.setId(11L);
            target.setName("Oct 2025");
            PaymentCarryOverEntity carryOver = PaymentCarryOverEntity.builder()
                    .targetSeries(target)
                    .amount(money("2000.00"))
                    .build();

            when(idempotencyRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(record("4000.00")));
            when(carryOverRepository
                    .findByStudentIdAndSourceSeriesIdAndOriginPaymentDateAndActiveTrueOrderByIdAsc(
                            anyLong(), anyLong(), any()))
                    .thenReturn(List.of(carryOver));

            PaymentAllocationResult result = service.findReplay(
                    KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("6000.00")).orElseThrow();

            assertThat(result.carryOvers()).singleElement().satisfies(restored -> {
                assertThat(restored.seriesId()).isEqualTo(11L);
                assertThat(restored.seriesName()).isEqualTo("Oct 2025");
                assertThat(restored.amount()).isEqualByComparingTo("2000.00");
            });
            // L'invariant de conservation tient sur le rejeu comme sur l'original.
            assertThat(result.amountAllocated().add(result.amountCarriedOver()))
                    .isEqualByComparingTo(result.amountReceived());
        }

        @Test
        @DisplayName("montant identique d'échelle différente : reconnu comme le même versement")
        void echelleDifferente() {
            // 6000 et 6000.00 sont le même montant. Comparer par equals ferait échouer le rejeu et
            // produirait un second encaissement, exactement ce qu'il faut empêcher.
            when(idempotencyRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(record("4000.00")));
            when(carryOverRepository
                    .findByStudentIdAndSourceSeriesIdAndOriginPaymentDateAndActiveTrueOrderByIdAsc(
                            anyLong(), anyLong(), any()))
                    .thenReturn(List.of());

            assertThat(service.findReplay(KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("6000")))
                    .isPresent();
        }
    }

    // ------------------------------------------------------------------
    // Clé réutilisée à tort
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Clé réutilisée pour un encaissement différent")
    class CleReutilisee {

        @Test
        @DisplayName("montant différent : 409, et le message nomme l'encaissement déjà enregistré")
        void montantDifferent() {
            when(idempotencyRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(record("4000.00")));

            assertThatThrownBy(() -> service.findReplay(
                    KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("2000.00")))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("6000.00")
                    .extracting(e -> ((CustomServiceException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);

            // Ni rejeu silencieux, ni versement : renvoyer le résultat d'un autre encaissement
            // produirait un reçu portant un montant que personne n'a versé.
            verify(idempotencyRepository, never()).save(any());
            verifyNoInteractions(carryOverRepository);
        }

        @Test
        @DisplayName("étudiant différent : 409")
        void etudiantDifferent() {
            when(idempotencyRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(record("4000.00")));

            assertThatThrownBy(() -> service.findReplay(
                    KEY, 999L, GROUP_ID, SERIES_ID, money("6000.00")))
                    .isInstanceOf(CustomServiceException.class)
                    .extracting(e -> ((CustomServiceException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("série différente : 409")
        void serieDifferente() {
            when(idempotencyRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(record("4000.00")));

            assertThatThrownBy(() -> service.findReplay(
                    KEY, STUDENT_ID, GROUP_ID, 999L, money("6000.00")))
                    .isInstanceOf(CustomServiceException.class)
                    .extracting(e -> ((CustomServiceException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("groupe différent : 409")
        void groupeDifferent() {
            when(idempotencyRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(record("4000.00")));

            assertThatThrownBy(() -> service.findReplay(
                    KEY, STUDENT_ID, 999L, SERIES_ID, money("6000.00")))
                    .isInstanceOf(CustomServiceException.class)
                    .extracting(e -> ((CustomServiceException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }
}
