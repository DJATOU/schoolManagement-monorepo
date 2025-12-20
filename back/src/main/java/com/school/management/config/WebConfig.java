package com.school.management.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.lang.NonNull;

import java.nio.file.Paths;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Convertir le chemin en chemin absolu et s'assurer qu'il se termine par /
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().toString();
        String resourceLocation = "file:" + absolutePath + "/";

        registry.addResourceHandler("/personne/**")
                .addResourceLocations(resourceLocation);
    }
}
