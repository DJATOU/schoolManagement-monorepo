package com.school.management.service.payment;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test de fumée (smoke test) prouvant que le moteur jqwik est correctement
 * câblé dans la chaîne de build (tâche 1 — feature payment-attendance-rules).
 *
 * <p>Ce test minimal vérifie uniquement que les {@code @Property} jqwik sont
 * découvertes et exécutées par la JUnit Platform. Il pourra être supprimé une
 * fois les vraies propriétés (Properties 1–22) implémentées.
 */
class JqwikToolingSmokeTest {

    // Feature: payment-attendance-rules, Tooling smoke test:
    // vérifie que jqwik exécute au moins une @Property.
    @Property(tries = 100)
    void additionIsCommutative(@ForAll @IntRange(min = -1000, max = 1000) int a,
                               @ForAll @IntRange(min = -1000, max = 1000) int b) {
        assertEquals(a + b, b + a);
    }
}
