package com.school.management.persistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires des valeurs par défaut et des invariants des entités de persistance.
 *
 * <p>Placés dans le paquet {@code com.school.management.persistance} afin d'invoquer
 * directement la méthode {@code protected} {@link DiscountEntity#onCreate()}
 * (le hook {@code @PrePersist}) sans avoir à câbler un contexte Spring/JPA complet.</p>
 *
 * <p>Couvre :</p>
 * <ul>
 *   <li>{@code catchUpRight} vaut {@code true} par défaut sur une présence absente
 *       (Exigence 7.1) ;</li>
 *   <li>l'invariant de portée {@code @PrePersist} de {@link DiscountEntity} rejette
 *       les cas zéro/multi-portée et accepte exactement une portée cohérente
 *       (Exigence 12.8) ;</li>
 *   <li>{@code notes} reste {@code null} quand aucune note n'est fournie
 *       (Exigence 11.3).</li>
 * </ul>
 */
class EntityDefaultsAndInvariantsTest {

    // ---------------------------------------------------------------------
    // Exigence 7.1 — catchUpRight par défaut à true
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("AttendanceEntity : droit au rattrapage par défaut")
    class CatchUpRightDefault {

        @Test
        @DisplayName("builder() sans catchUpRight → true (via @Builder.Default)")
        void builderDefaultsCatchUpRightToTrue() {
            AttendanceEntity attendance = AttendanceEntity.builder()
                    .isPresent(false)
                    .build();

            assertTrue(attendance.getCatchUpRight(),
                    "catchUpRight doit valoir true par défaut sur une présence absente");
        }

        @Test
        @DisplayName("builder() sur une absence non justifiée → droit maintenu à true")
        void builderKeepsCatchUpRightTrueRegardlessOfJustification() {
            AttendanceEntity attendance = AttendanceEntity.builder()
                    .isPresent(false)
                    .isJustified(false)
                    .build();

            assertTrue(attendance.getCatchUpRight(),
                    "le droit au rattrapage est indépendant de la justification de l'absence");
        }

        @Test
        @DisplayName("Un administrateur peut révoquer explicitement le droit → false")
        void catchUpRightCanBeRevoked() {
            AttendanceEntity attendance = new AttendanceEntity();
            attendance.setCatchUpRight(false);

            assertTrue(!attendance.getCatchUpRight(),
                    "un droit explicitement révoqué doit valoir false");
        }
    }

    // ---------------------------------------------------------------------
    // Exigence 12.8 — invariant de portée du discount (@PrePersist)
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("DiscountEntity : invariant de portée @PrePersist")
    class DiscountScopeInvariant {

        private DiscountEntity discountWith(DiscountScope scope, Long groupId, Long seriesId, Long sessionId) {
            return DiscountEntity.builder()
                    .scope(scope)
                    .groupId(groupId)
                    .seriesId(seriesId)
                    .sessionId(sessionId)
                    .rate(new BigDecimal("0.25"))
                    .build();
        }

        @Test
        @DisplayName("Aucune portée renseignée → rejet")
        void zeroScopeRejected() {
            DiscountEntity discount = discountWith(DiscountScope.GROUP, null, null, null);

            assertThrows(IllegalStateException.class, discount::onCreate,
                    "un discount sans identifiant de portée doit être rejeté");
        }

        @Test
        @DisplayName("Scope null → rejet")
        void nullScopeRejected() {
            DiscountEntity discount = discountWith(null, 1L, null, null);

            assertThrows(IllegalStateException.class, discount::onCreate,
                    "un discount sans scope déclaré doit être rejeté");
        }

        @Test
        @DisplayName("Deux identifiants de portée renseignés → rejet")
        void multiScopeRejected() {
            DiscountEntity discount = discountWith(DiscountScope.GROUP, 1L, 2L, null);

            assertThrows(IllegalStateException.class, discount::onCreate,
                    "un discount avec plusieurs portées doit être rejeté");
        }

        @Test
        @DisplayName("scope=GROUP mais seriesId renseigné → rejet (portée incohérente)")
        void scopeMismatchRejected() {
            DiscountEntity discount = discountWith(DiscountScope.GROUP, null, 2L, null);

            assertThrows(IllegalStateException.class, discount::onCreate,
                    "l'identifiant renseigné doit correspondre au scope déclaré");
        }

        @Test
        @DisplayName("scope=GROUP avec uniquement groupId → accepté")
        void groupScopeAccepted() {
            DiscountEntity discount = discountWith(DiscountScope.GROUP, 1L, null, null);

            assertDoesNotThrow(discount::onCreate);
        }

        @Test
        @DisplayName("scope=SERIES avec uniquement seriesId → accepté")
        void seriesScopeAccepted() {
            DiscountEntity discount = discountWith(DiscountScope.SERIES, null, 2L, null);

            assertDoesNotThrow(discount::onCreate);
        }

        @Test
        @DisplayName("scope=SESSION avec uniquement sessionId → accepté")
        void sessionScopeAccepted() {
            DiscountEntity discount = discountWith(DiscountScope.SESSION, null, null, 3L);

            assertDoesNotThrow(discount::onCreate);
        }
    }

    // ---------------------------------------------------------------------
    // Exigence 11.3 — notes null quand aucune note n'est fournie
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("PaymentEntity : note absente")
    class PaymentNotesAbsent {

        @Test
        @DisplayName("builder() sans notes → getNotes() null")
        void builderLeavesNotesNullWhenAbsent() {
            PaymentEntity payment = PaymentEntity.builder()
                    .amountPaid(120.0)
                    .build();

            assertNull(payment.getNotes(),
                    "notes doit rester null quand aucune note n'est fournie");
        }

        @Test
        @DisplayName("Constructeur sans argument → getNotes() null")
        void noArgsConstructorLeavesNotesNull() {
            PaymentEntity payment = new PaymentEntity();

            assertNull(payment.getNotes(),
                    "notes doit être null par défaut via le constructeur sans argument");
        }
    }
}
