package com.school.management.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Horloge de l'application : l'heure courante lue par les règles qui la comparent à une séance.
 *
 * <p>Passer par un {@link Clock} plutôt que par {@code new Date()} rend ces règles testables :
 * les jeux d'essai placent leurs séances dans une année scolaire « courante » datée de 2029-2030,
 * donc dans le futur, et une règle lisant l'heure système les refuserait.</p>
 *
 * <p>{@value #FIXED_INSTANT_PROPERTY} fige l'horloge sur un instant ISO-8601. Ce réglage est
 * réservé aux tests (voir {@code src/test/resources/application.properties}) ; il est journalisé
 * en avertissement s'il est renseigné.</p>
 */
@Configuration
public class ClockConfig {

    /** Instant figé, au format ISO-8601 ({@code 2100-01-01T00:00:00Z}) ; vide en exploitation. */
    public static final String FIXED_INSTANT_PROPERTY = "app.clock.fixed-instant";

    private static final Logger LOGGER = LoggerFactory.getLogger(ClockConfig.class);

    @Bean
    public Clock clock(@Value("${" + FIXED_INSTANT_PROPERTY + ":}") String fixedInstant) {
        if (fixedInstant == null || fixedInstant.isBlank()) {
            return Clock.systemDefaultZone();
        }
        Instant instant = Instant.parse(fixedInstant.strip());
        LOGGER.warn("Horloge figée sur {} ({}) : réglage réservé aux tests.", instant, FIXED_INSTANT_PROPERTY);
        return Clock.fixed(instant, ZoneId.systemDefault());
    }
}
