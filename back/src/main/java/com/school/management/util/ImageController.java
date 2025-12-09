package com.school.management.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@RestController
public class ImageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageController.class);

    @Value("${app.upload.dir}")
    private String uploadDir;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            if (!Files.exists(rootLocation)) {
                Files.createDirectories(rootLocation);
                LOGGER.info("Created upload directory: {}", rootLocation);
            }
        } catch (IOException e) {
            LOGGER.error("Could not create upload directory: {}", rootLocation, e);
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    @GetMapping("/personne/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        // Validation Path Traversal - Sécurité critique
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            LOGGER.warn("Attempted path traversal attack with filename: {}", filename);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Path file = rootLocation.resolve(filename).normalize();

            // Vérifier que le fichier résolu est bien dans le répertoire autorisé
            if (!file.startsWith(rootLocation)) {
                LOGGER.warn("Access denied - file outside allowed directory: {}", file);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                MediaType mediaType = getMediaTypeForFileName(filename);
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                        .body(resource);
            } else {
                LOGGER.debug("File not found or not readable: {}", filename);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            LOGGER.error("Error reading file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private MediaType getMediaTypeForFileName(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.valueOf("image/webp");
            case "svg" -> MediaType.valueOf("image/svg+xml");
            default -> MediaType.IMAGE_JPEG;
        };
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }
}
