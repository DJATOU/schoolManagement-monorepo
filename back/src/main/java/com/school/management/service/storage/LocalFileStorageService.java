package com.school.management.service.storage;

import com.school.management.util.FileValidationUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Implémentation du stockage de fichiers sur le disque local.
 * Utilisé en développement et pour les déploiements sur serveur unique.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileStorageService.class);

    @Value("${app.upload.dir}")
    private String uploadDir;

    private Path rootLocation;

    @PostConstruct
    @Override
    public void init() throws IOException {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
            LOGGER.info("Created upload directory: {}", rootLocation);
        } else {
            LOGGER.info("Using existing upload directory: {}", rootLocation);
        }
    }

    @Override
    public String saveFile(MultipartFile file) throws IOException {
        // Valider le fichier
        FileValidationUtil.validateImageFile(file);

        // Générer un nom de fichier sécurisé et unique
        String filename = FileValidationUtil.generateSafeFilename(file.getOriginalFilename());

        // Vérifier que le nom de fichier est sécurisé (pas de path traversal)
        if (!FileValidationUtil.isSafeFilename(filename)) {
            throw new SecurityException("Invalid filename: " + filename);
        }

        Path destinationFile = rootLocation.resolve(filename).normalize();

        // Double vérification que le fichier est bien dans le répertoire autorisé
        if (!destinationFile.startsWith(rootLocation)) {
            throw new SecurityException("Cannot store file outside upload directory");
        }

        // Copier le fichier
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        }

        LOGGER.info("File saved successfully: {}", filename);
        return filename;
    }

    @Override
    public Resource loadFile(String filename) throws IOException {
        // Valider le nom de fichier pour éviter le path traversal
        if (!FileValidationUtil.isSafeFilename(filename)) {
            throw new SecurityException("Invalid filename: " + filename);
        }

        try {
            Path file = rootLocation.resolve(filename).normalize();

            // Vérifier que le fichier est dans le répertoire autorisé
            if (!file.startsWith(rootLocation)) {
                throw new SecurityException("Access denied to file: " + filename);
            }

            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IOException("File not found or not readable: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new IOException("Error loading file: " + filename, e);
        }
    }

    @Override
    public void deleteFile(String filename) throws IOException {
        // Valider le nom de fichier
        if (!FileValidationUtil.isSafeFilename(filename)) {
            throw new SecurityException("Invalid filename: " + filename);
        }

        Path file = rootLocation.resolve(filename).normalize();

        // Vérifier que le fichier est dans le répertoire autorisé
        if (!file.startsWith(rootLocation)) {
            throw new SecurityException("Access denied to file: " + filename);
        }

        if (Files.exists(file)) {
            Files.delete(file);
            LOGGER.info("File deleted successfully: {}", filename);
        } else {
            LOGGER.warn("Attempted to delete non-existent file: {}", filename);
        }
    }

    @Override
    public boolean fileExists(String filename) {
        if (filename == null || !FileValidationUtil.isSafeFilename(filename)) {
            return false;
        }

        try {
            Path file = rootLocation.resolve(filename).normalize();
            return file.startsWith(rootLocation) && Files.exists(file);
        } catch (Exception e) {
            LOGGER.error("Error checking file existence: {}", filename, e);
            return false;
        }
    }
}
