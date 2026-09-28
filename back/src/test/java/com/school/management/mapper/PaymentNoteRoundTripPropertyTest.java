package com.school.management.mapper;

import com.school.management.dto.PaymentDTO;
import com.school.management.persistance.PaymentEntity;
import com.school.management.shared.mapper.MappingContext;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test property-based (jqwik) de l'aller-retour de la note de paiement.
 *
 * <p>Feature: payment-attendance-rules, Property 19: For any note string (including empty),
 * persisting a payment with that note and reading it back returns the same note; persisting
 * with no note stores null.</p>
 *
 * <p><strong>Validates: Requirements 11.1, 11.3</strong></p>
 *
 * <p>La propriété est vérifiée sur l'aller-retour à travers le {@link PaymentMapper}
 * ({@code PaymentEntity → PaymentDTO → PaymentEntity}), qui est le composant portant la note
 * dans les deux sens (requirement 11.1 : note persistée ; 11.3 : absence → {@code null}).
 * Cette approche pure est déterministe et ne nécessite pas le contexte Spring : les
 * identifiants de référence étant {@code null}, aucune résolution de repository n'est requise
 * (un {@link MappingContext} sans repository suffit). Le générateur inclut la chaîne vide et
 * la valeur {@code null} ; un cas {@code null} explicite complète la couverture.</p>
 */
class PaymentNoteRoundTripPropertyTest {

    private final PaymentMapper mapper = new PaymentMapperImpl();

    /** Contexte de mapping sans repository : suffisant car aucune référence (ids null). */
    private final MappingContext emptyContext =
            MappingContext.of(null, null, null, null, null, null, null, null, null, null, null, null);

    /** Notes générées : chaînes (dont la chaîne vide) et la valeur {@code null} incluse. */
    @Provide
    Arbitrary<String> notes() {
        return Arbitraries.strings()
                .ofMaxLength(1000)
                .injectNull(0.1);
    }

    // Feature: payment-attendance-rules, Property 19: For any note string (including empty),
    // persisting a payment with that note and reading it back returns the same note;
    // persisting with no note stores null.
    @Property(tries = 100)
    void paymentNote_roundTripsThroughMapper(@ForAll("notes") String note) {
        // Entité d'origine portant la note générée (chaîne, chaîne vide ou null).
        PaymentEntity original = PaymentEntity.builder()
                .amountPaid(100.0)
                .notes(note)
                .build();

        // Aller : entité → DTO.
        PaymentDTO dto = mapper.toDto(original);
        assertThat(dto.getNotes()).isEqualTo(note);

        // Retour : DTO → entité. La note doit être préservée à l'identique.
        PaymentEntity roundTripped = mapper.toEntity(dto, emptyContext);
        assertThat(roundTripped.getNotes()).isEqualTo(note);
    }

    // Cas explicite : absence de note → null persisté (requirement 11.3).
    @Test
    void paymentWithoutNote_storesNull() {
        PaymentEntity original = PaymentEntity.builder()
                .amountPaid(50.0)
                .notes(null)
                .build();

        PaymentDTO dto = mapper.toDto(original);
        assertThat(dto.getNotes()).isNull();

        PaymentEntity roundTripped = mapper.toEntity(dto, emptyContext);
        assertThat(roundTripped.getNotes()).isNull();
    }
}
