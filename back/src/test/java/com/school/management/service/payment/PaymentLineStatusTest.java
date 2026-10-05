package com.school.management.service.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Statut stocké d'un cumul de série")
class PaymentLineStatusTest {

    @ParameterizedTest(name = "versé {0}, coût {1} → {2}")
    @CsvSource(nullValues = "inconnu", value = {
            "8000.00, 8000.00, COMPLETED",   // soldé pile
            "9000.00, 8000.00, COMPLETED",   // trop-perçu : soldé, l'excédent se lit ailleurs
            "4000.00, 8000.00, IN_PROGRESS",
            "0.00,    8000.00, PENDING",     // rien de versé, par exemple après annulation
            "0.00,    0.00,    COMPLETED",   // exempté : rien à payer, donc rien de dû
            "4000.00, inconnu, IN_PROGRESS", // coût inconnu : jamais annoncé soldé
            "0.00,    inconnu, PENDING"
    })
    void statusFollowsPaidAndCost(String paid, String cost, String expected) {
        Optional<BigDecimal> resolvedCost = cost == null ? Optional.empty() : Optional.of(new BigDecimal(cost));
        assertThat(PaymentLineStatus.of(new BigDecimal(paid), resolvedCost)).isEqualTo(expected);
    }
}
