package com.school.management.service.payment;

import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.PaymentIdempotencyEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.EncashmentAllocationRepository;
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
 *
 * <p>Le chemin rattrapage suit la même règle (spec admin-corrections, exigence 1.7), avec la séance
 * dans l'empreinte.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Idempotence des encaissements")
class PaymentIdempotencyServiceTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long GROUP_ID = 3L;
    private static final Long SERIES_ID = 10L;
    private static final Long SESSION_ID = 40L;
    private static final Long ENCASHMENT_ID = 500L;
    private static final String KEY = "a3f1c0de-4b2e-4d8a-9f10-77c2b5e6a001";
    private static final Date RECEIVED_AT = new Date(1_700_000_000_000L);

    @Mock private PaymentIdempotencyRepository idempotencyRepository;
    @Mock private EncashmentAllocationRepository allocationRepository;

    @InjectMocks private PaymentIdempotencyService service;

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static SessionSeriesEntity series(Long id, String name) {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(id);
        series.setName(name);
        return series;
    }

    private static EncashmentEntity encashment() {
        return EncashmentEntity.builder()
                .id(ENCASHMENT_ID)
                .receiptNumber("RECU-2030-0001")
                .amountReceived(money("6000.00"))
                .receivedAt(RECEIVED_AT)
                .build();
    }

    private static EncashmentAllocationEntity allocation(Long seriesId, String name, String amount,
                                                         boolean carriedOver, boolean active) {
        return EncashmentAllocationEntity.builder()
                .series(series(seriesId, name))
                .amount(money(amount))
                .carriedOver(carriedOver)
                .active(active)
                .build();
    }

    /** Empreinte d'un versement de série de 6 000 DA. */
    private PaymentIdempotencyEntity record() {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(55L);
        return PaymentIdempotencyEntity.builder()
                .idempotencyKey(KEY)
                .studentId(STUDENT_ID)
                .groupId(GROUP_ID)
                .sessionSeriesId(SERIES_ID)
                .amountReceived(money("6000.00"))
                .amountAllocated(money("4000.00"))
                .payment(payment)
                .originPaymentDate(RECEIVED_AT)
                .encashment(encashment())
                .build();
    }

    /** Empreinte d'un rattrapage de 2 000 DA sur la séance {@link #SESSION_ID}. */
    private PaymentIdempotencyEntity catchUpRecord() {
        PaymentIdempotencyEntity record = record();
        record.setSessionId(SESSION_ID);
        record.setAmountReceived(money("2000.00"));
        record.setAmountAllocated(money("2000.00"));
        return record;
    }

    private void givenRecord(PaymentIdempotencyEntity record) {
        when(idempotencyRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(record));
    }

    private void givenAllocations(EncashmentAllocationEntity... allocations) {
        when(allocationRepository.findByEncashmentIdOrderByIdAsc(ENCASHMENT_ID)).thenReturn(List.of(allocations));
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((CustomServiceException) e).getStatus();
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
            assertThat(service.findCatchUpReplay(null, STUDENT_ID, SESSION_ID, money("2000.00")))
                    .isEmpty();
            verifyNoInteractions(idempotencyRepository, allocationRepository);
        }

        @Test
        @DisplayName("clé nulle : rien n'est conservé, donc rien ne bloquera un versement ultérieur")
        void rienConserve() {
            service.remember(null, null, null);
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
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        @DisplayName("clé de plus de 100 caractères : 400, avant toute écriture")
        void cleTropLongue() {
            assertThatThrownBy(() -> service.normalizeKey("x".repeat(101)))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
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
            verifyNoInteractions(allocationRepository);
        }

        @Test
        @DisplayName("empreinte conservée avec la requête, son résultat et son Encaissement")
        void empreinteConservee() {
            PaymentEntity payment = new PaymentEntity();
            payment.setId(55L);
            EncashmentEntity encashment = encashment();
            PaymentAllocationResult result = new PaymentAllocationResult(STUDENT_ID, GROUP_ID,
                    SERIES_ID, money("6000.00"), money("4000.00"), List.of(), payment, encashment);

            service.remember(KEY, result, null);

            ArgumentCaptor<PaymentIdempotencyEntity> captor =
                    ArgumentCaptor.forClass(PaymentIdempotencyEntity.class);
            verify(idempotencyRepository).save(captor.capture());
            PaymentIdempotencyEntity saved = captor.getValue();
            assertThat(saved.getIdempotencyKey()).isEqualTo(KEY);
            assertThat(saved.getStudentId()).isEqualTo(STUDENT_ID);
            assertThat(saved.getSessionSeriesId()).isEqualTo(SERIES_ID);
            assertThat(saved.getSessionId()).as("versement de série : pas de séance").isNull();
            assertThat(saved.getAmountReceived()).isEqualByComparingTo("6000.00");
            assertThat(saved.getAmountAllocated()).isEqualByComparingTo("4000.00");
            assertThat(saved.getPayment()).isSameAs(payment);
            // L'empreinte désigne l'Encaissement, et en reprend l'horodatage serveur.
            assertThat(saved.getEncashment()).isSameAs(encashment);
            assertThat(saved.getOriginPaymentDate()).isEqualTo(RECEIVED_AT);
        }

        @Test
        @DisplayName("rattrapage : la séance payée entre dans l'empreinte")
        void empreinteDuRattrapage() {
            PaymentAllocationResult result = new PaymentAllocationResult(STUDENT_ID, GROUP_ID,
                    SERIES_ID, money("2000.00"), money("2000.00"), List.of(), new PaymentEntity(), encashment());

            service.remember(KEY, result, SESSION_ID);

            ArgumentCaptor<PaymentIdempotencyEntity> captor =
                    ArgumentCaptor.forClass(PaymentIdempotencyEntity.class);
            verify(idempotencyRepository).save(captor.capture());
            assertThat(captor.getValue().getSessionId()).isEqualTo(SESSION_ID);
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
            givenRecord(record());
            givenAllocations(allocation(SERIES_ID, "Sept 2025", "4000.00", false, true));

            PaymentAllocationResult result = service.findReplay(
                    KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("6000.00")).orElseThrow();

            assertThat(result.amountReceived()).isEqualByComparingTo("6000.00");
            assertThat(result.amountAllocated()).isEqualByComparingTo("4000.00");
            assertThat(result.payment().getId()).isEqualTo(55L);
            assertThat(result.encashment().getReceiptNumber())
                    .as("le rejeu rend le même reçu").isEqualTo("RECU-2030-0001");
            // Aucune écriture : c'est tout l'objet du mécanisme.
            verify(idempotencyRepository, never()).save(any());
        }

        @Test
        @DisplayName("la répartition est relue depuis les Imputations de l'Encaissement, reports compris")
        void repartitionRelue() {
            givenRecord(record());
            givenAllocations(
                    allocation(SERIES_ID, "Sept 2025", "4000.00", false, true),
                    allocation(11L, "Oct 2025", "2000.00", true, true));

            PaymentAllocationResult result = service.findReplay(
                    KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("6000.00")).orElseThrow();

            assertThat(result.amountAllocated()).isEqualByComparingTo("4000.00");
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
        @DisplayName("Encaissement annulé depuis : le rejeu rend la réponse originale, pas une répartition vide")
        void encaissementAnnuleDepuis() {
            // Ses Imputations sont inactives. Ne relire que les actives rendrait 0 DA imputé et
            // aucun report : une réponse que l'original n'a jamais donnée.
            givenRecord(record());
            givenAllocations(
                    allocation(SERIES_ID, "Sept 2025", "4000.00", false, false),
                    allocation(11L, "Oct 2025", "2000.00", true, false));

            PaymentAllocationResult result = service.findReplay(
                    KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("6000.00")).orElseThrow();

            assertThat(result.amountAllocated()).isEqualByComparingTo("4000.00");
            assertThat(result.amountCarriedOver()).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("montant identique d'échelle différente : reconnu comme le même versement")
        void echelleDifferente() {
            // 6000 et 6000.00 sont le même montant. Comparer par equals ferait échouer le rejeu et
            // produirait un second encaissement, exactement ce qu'il faut empêcher.
            givenRecord(record());
            givenAllocations(allocation(SERIES_ID, "Sept 2025", "4000.00", false, true));

            assertThat(service.findReplay(KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("6000")))
                    .isPresent();
        }

        @Test
        @DisplayName("rattrapage rejoué : même étudiant, même séance, même montant")
        void rattrapageRejoue() {
            givenRecord(catchUpRecord());
            givenAllocations(allocation(SERIES_ID, "Sept 2025", "2000.00", false, true));

            assertThat(service.findCatchUpReplay(KEY, STUDENT_ID, SESSION_ID, money("2000")))
                    .hasValueSatisfying(result -> assertThat(result.amountAllocated()).isEqualByComparingTo("2000.00"));
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
            givenRecord(record());

            assertThatThrownBy(() -> service.findReplay(
                    KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("2000.00")))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("6000.00")
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));

            // Ni rejeu silencieux, ni versement : renvoyer le résultat d'un autre encaissement
            // produirait un reçu portant un montant que personne n'a versé.
            verify(idempotencyRepository, never()).save(any());
            verifyNoInteractions(allocationRepository);
        }

        @Test
        @DisplayName("étudiant, série ou groupe différent : 409")
        void autreEtudiantSerieOuGroupe() {
            givenRecord(record());

            assertThatThrownBy(() -> service.findReplay(KEY, 999L, GROUP_ID, SERIES_ID, money("6000.00")))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
            assertThatThrownBy(() -> service.findReplay(KEY, STUDENT_ID, GROUP_ID, 999L, money("6000.00")))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
            assertThatThrownBy(() -> service.findReplay(KEY, STUDENT_ID, 999L, SERIES_ID, money("6000.00")))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("rattrapage d'une autre séance, même montant : 409, jamais le rejeu du premier")
        void rattrapageAutreSeance() {
            givenRecord(catchUpRecord());

            assertThatThrownBy(() -> service.findCatchUpReplay(KEY, STUDENT_ID, 41L, money("2000.00")))
                    .isInstanceOf(CustomServiceException.class)
                    .hasMessageContaining("rattrapage de la séance " + SESSION_ID)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("clé d'un rattrapage présentée comme versement de série, et l'inverse : 409")
        void cleChangeDeChemin() {
            // Même étudiant, même série, même montant : sans la séance dans l'empreinte, le
            // versement de série rejouerait le rattrapage et n'encaisserait rien.
            givenRecord(catchUpRecord());
            assertThatThrownBy(() -> service.findReplay(KEY, STUDENT_ID, GROUP_ID, SERIES_ID, money("2000.00")))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));

            givenRecord(record());
            assertThatThrownBy(() -> service.findCatchUpReplay(KEY, STUDENT_ID, SESSION_ID, money("6000.00")))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        }
    }
}
