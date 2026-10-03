package com.school.management;

import com.school.management.config.ApplicationTimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SchoolManagementApplication {

    public static void main(String[] args) {
        application().run(args);
    }

    /**
     * L'application telle que la démarre {@link #main} : le fuseau de l'école est appliqué avant la
     * création de tout composant (spec admin-corrections, C.9).
     */
    static SpringApplication application() {
        SpringApplication application = new SpringApplication(SchoolManagementApplication.class);
        application.addListeners(new ApplicationTimeZone());
        return application;
    }
}
