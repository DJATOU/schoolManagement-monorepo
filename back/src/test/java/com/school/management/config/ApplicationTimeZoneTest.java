package com.school.management.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.school.management.domain.valueobject.EnrolmentWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le fuseau de l'école, appliqué à la JVM au démarrage (spec admin-corrections, C.9, D1).
 *
 * <p>Le cas évité : un conteneur laissé en UTC, qui imprimait un versement de 10:00 à 09:00 et
 * datait de la veille ce qui se passait entre minuit et une heure à Alger. Chaque test part d'une
 * JVM réglée sur UTC, comme un conteneur sans {@code TZ}, et rend le fuseau d'origine ensuite.</p>
 */
@DisplayName("Fuseau de l'école appliqué au démarrage")
class ApplicationTimeZoneTest {

    private TimeZone original;

    /** Les lignes du journal de l'écouteur, lues sur son enregistreur : la console est déjà prise. */
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApplicationTimeZone.class);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private Level level;

    @BeforeEach
    void containerLeftInUtc() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        level = logger.getLevel();
        logger.setLevel(Level.INFO);
        journal.start();
        logger.addAppender(journal);
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
        logger.detachAppender(journal);
        logger.setLevel(level);
    }

    private List<String> logged() {
        return journal.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("sans réglage, ou réglage vide : Africa/Algiers, quel que soit le conteneur")
    void defaultsToAlgiers() {
        assertThat(ApplicationTimeZone.apply(null)).isEqualTo(ZoneId.of("Africa/Algiers"));
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Africa/Algiers");

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        assertThat(ApplicationTimeZone.apply("   ")).isEqualTo(ZoneId.of("Africa/Algiers"));
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Africa/Algiers"));
    }

    @Test
    @DisplayName("une séance à 00:30 à Alger est du jour même, et non de la veille comme en UTC")
    void sessionDayFollowsTheSchoolZone() {
        Date halfPastMidnightInAlgiers = Date.from(Instant.parse("2030-01-14T23:30:00Z"));
        assertThat(EnrolmentWindow.dayOf(halfPastMidnightInAlgiers)).as("conteneur en UTC")
                .isEqualTo(LocalDate.of(2030, 1, 14));

        ApplicationTimeZone.apply(null);

        assertThat(EnrolmentWindow.dayOf(halfPastMidnightInAlgiers)).isEqualTo(LocalDate.of(2030, 1, 15));
    }

    @Test
    @DisplayName("autre pays : la valeur réglée s'applique, espaces autour ignorés")
    void anotherCountry() {
        assertThat(ApplicationTimeZone.apply(" Africa/Casablanca ")).isEqualTo(ZoneId.of("Africa/Casablanca"));
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Africa/Casablanca");
    }

    @Test
    @DisplayName("fuseau inconnu : démarrage refusé en nommant la valeur, et rien n'est changé")
    void unknownZoneRefusesToStart() {
        for (String typo : new String[] {"Africa/Alger", "UTC+1h"}) {
            assertThatThrownBy(() -> ApplicationTimeZone.apply(typo))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_TIMEZONE")
                    .hasMessageContaining("« " + typo + " »")
                    .hasMessageContaining("démarrage refusé")
                    .hasMessageContaining("Africa/Algiers");
            assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
        }
    }

    @Test
    @DisplayName("le démarrage lit app.timezone, renseignée par la variable APP_TIMEZONE du .env")
    void readsTheEnvironmentVariable() {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("env",
                Map.of("APP_TIMEZONE", "Africa/Tunis")));

        new ApplicationTimeZone().onApplicationEvent(prepared(environment));

        assertThat(TimeZone.getDefault().getID()).isEqualTo("Africa/Tunis");
    }

    @Test
    @DisplayName("propriété absente de l'environnement : Africa/Algiers")
    void missingPropertyMeansAlgiers() {
        new ApplicationTimeZone().onApplicationEvent(prepared(new MockEnvironment()));

        assertThat(TimeZone.getDefault().getID()).isEqualTo("Africa/Algiers");
    }

    @Test
    @DisplayName("le journal dit le fuseau appliqué, son décalage, l'heure locale et le fuseau du système")
    void logsTheAppliedZone() {
        ApplicationTimeZone.apply(null);

        assertThat(logged()).singleElement().asString()
                .startsWith("Fuseau de l'école : Africa/Algiers (UTC+01:00), heure locale ")
                .endsWith("fuseau du système : UTC (non utilisé par l'application)");
    }

    @Test
    @DisplayName("système déjà sur le bon fuseau : rien de plus à signaler")
    void logsNothingMoreWhenTheSystemAgrees() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Algiers"));

        ApplicationTimeZone.apply("Africa/Algiers");

        assertThat(logged()).singleElement().asString().endsWith("fuseau du système : Africa/Algiers");
    }

    @Test
    @DisplayName("fuseau sans décalage : écrit UTC+00:00")
    void utcOffsetIsWrittenInFull() {
        ApplicationTimeZone.apply("UTC");

        assertThat(logged()).singleElement().asString().startsWith("Fuseau de l'école : UTC (UTC+00:00)");
    }

    @Test
    @DisplayName("appliqué juste après l'initialisation des journaux, avant tout le reste")
    void runsRightAfterLogging() {
        assertThat(new ApplicationTimeZone().getOrder()).isEqualTo(LoggingApplicationListener.DEFAULT_ORDER + 1);
    }

    private static ApplicationEnvironmentPreparedEvent prepared(ConfigurableEnvironment environment) {
        return new ApplicationEnvironmentPreparedEvent(new DefaultBootstrapContext(), new SpringApplication(),
                new String[0], environment);
    }
}
