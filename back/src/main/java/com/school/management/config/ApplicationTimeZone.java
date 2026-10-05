package com.school.management.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Fuseau de l'école, appliqué à la JVM au démarrage (spec admin-corrections, D1, C.9).
 *
 * <p>Les dates et heures sont stockées en heure d'horloge, sans fuseau : le jour d'une séance,
 * l'heure d'un reçu, « aujourd'hui » pour une inscription sans date se lisent dans le fuseau de la
 * JVM. Celui-ci venait du conteneur ({@code TZ}), réglé sur UTC par défaut : un oubli de
 * configuration imprimait un versement de 10:00 à 09:00, et datait de la veille ce qui se passait
 * entre minuit et une heure à Alger.</p>
 *
 * <p>Le fuseau est donc un réglage de l'application, {@code app.timezone} (variable
 * {@code APP_TIMEZONE}), {@value #DEFAULT_ZONE} par défaut, appliqué quel que soit le réglage du
 * conteneur. Installer l'application dans un autre pays revient à changer cette valeur.</p>
 *
 * <p>Une valeur inconnue empêche le démarrage, en la nommant : elle ne peut venir que d'une saisie
 * lors d'une installation, où l'échec se voit tout de suite. Se rabattre sans bruit sur Alger
 * fausserait au contraire toutes les heures d'une école située ailleurs.</p>
 *
 * <p>Inscrit par {@code SchoolManagementApplication.main} et non par Spring : il doit agir avant
 * la création de tout composant, et notamment de la source de données, dont les connexions
 * transmettent le fuseau de la JVM à PostgreSQL. Il passe juste après l'initialisation des
 * journaux, pour que sa ligne de démarrage y figure.</p>
 */
public final class ApplicationTimeZone implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    /** Propriété du fuseau ; la variable d'environnement {@code APP_TIMEZONE} la renseigne. */
    public static final String PROPERTY = "app.timezone";

    /** Fuseau de l'école, sans réglage : l'Algérie est à UTC+1 toute l'année. */
    public static final String DEFAULT_ZONE = "Africa/Algiers";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationTimeZone.class);

    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        apply(event.getEnvironment().getProperty(PROPERTY));
    }

    @Override
    public int getOrder() {
        return LoggingApplicationListener.DEFAULT_ORDER + 1;
    }

    /**
     * Applique le fuseau configuré à la JVM et le journalise.
     *
     * @param configured valeur de {@code app.timezone} ; absente ou vide, {@value #DEFAULT_ZONE}
     * @return le fuseau appliqué
     * @throws IllegalStateException si la valeur n'est pas un fuseau connu ; rien n'est alors changé
     */
    static ZoneId apply(String configured) {
        ZoneId system = ZoneId.systemDefault();
        ZoneId zone = resolve(configured);
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        ZonedDateTime now = ZonedDateTime.now(zone);
        LOGGER.info("Fuseau de l'école : {} (UTC{}), heure locale {} ; fuseau du système : {}{}",
                zone.getId(), now.getOffset().getId().replace("Z", "+00:00"), now.format(LOCAL_TIME), system.getId(),
                zone.equals(system) ? "" : " (non utilisé par l'application)");
        return zone;
    }

    /** Le fuseau désigné par la valeur, {@value #DEFAULT_ZONE} si elle est absente ou vide. */
    static ZoneId resolve(String configured) {
        String id = configured == null || configured.isBlank() ? DEFAULT_ZONE : configured.strip();
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            throw new IllegalStateException("APP_TIMEZONE (" + PROPERTY + ") = « " + id + " » n'est pas un fuseau "
                    + "connu : démarrage refusé. Exemples : Africa/Algiers, Africa/Tunis, Africa/Casablanca, "
                    + "Europe/Paris.", e);
        }
    }
}
