package com.school.management;

import com.school.management.config.ApplicationTimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le démarrage applique le fuseau de l'école avant la création de tout composant (C.9) : sans
 * l'écouteur inscrit par {@code main}, le réglage {@code APP_TIMEZONE} serait lu mais sans effet.
 */
class SchoolManagementApplicationStartupTest {

    @Test
    @DisplayName("main inscrit le fuseau de l'école parmi les écouteurs du démarrage")
    void mainAppliesTheSchoolZone() {
        assertThat(SchoolManagementApplication.application().getListeners())
                .anyMatch(ApplicationTimeZone.class::isInstance);
    }
}
