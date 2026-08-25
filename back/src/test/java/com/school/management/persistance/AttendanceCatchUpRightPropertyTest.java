package com.school.management.persistance;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) — Property 15.
 *
 * <p>Vérifie au niveau de l'entité que le droit au rattrapage
 * ({@code catchUpRight}) est vrai par défaut pour une présence créée avec
 * {@code isPresent == false}, quelle que soit la valeur de {@code isJustified}
 * (justifiée, non justifiée, ou non renseignée).</p>
 */
class AttendanceCatchUpRightPropertyTest {

    // Feature: payment-attendance-rules, Property 15: For any attendance created with isPresent == false and any value of isJustified, the Catch_Up_Right defaults to true.
    @Property(tries = 100)
    void property15_catchUpRightDefaultsTrueIndependentOfJustification(
            @ForAll("justifiedValues") Boolean isJustified) {

        AttendanceEntity attendance = AttendanceEntity.builder()
                .isPresent(false)
                .isJustified(isJustified)
                .build();

        assertThat(attendance.getCatchUpRight()).isTrue();
    }

    /** Génère true, false et null pour {@code isJustified}. */
    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<Boolean> justifiedValues() {
        return net.jqwik.api.Arbitraries.of(Boolean.TRUE, Boolean.FALSE, null);
    }
}
