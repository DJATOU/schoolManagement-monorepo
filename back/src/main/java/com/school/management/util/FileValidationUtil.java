package com.school.management.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

public class FileValidationUtil {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private FileValidationUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Valide qu'un fichier uploadé est une image valide
     *
     * @param file le fichier à valider
     * @throws IllegalArgumentException si le fichier n'est pas valide
     */
    public static void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required and must not be empty");
        }

        // Vérifier la taille
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum limit of %d MB. Current size: %d bytes",
                            MAX_FILE_SIZE / (1024 * 1024), file.getSize())
            );
        }

        // Vérifier l'extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File name is missing");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    String.format("File type '%s' not allowed. Allowed types: %s",
                            extension, ALLOWED_EXTENSIONS)
            );
        }

        // Vérifier le Content-Type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    String.format("Invalid content type '%s'. Must be an image.", contentType)
            );
        }
    }

    /**
     * Extrait l'extension d'un nom de fichier
     *
     * @param filename le nom du fichier
     * @return l'extension sans le point
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    /**
     * Génère un nom de fichier unique et sécurisé
     *
     * @param originalFilename le nom original du fichier
     * @return un nom de fichier unique
     */
    public static String generateSafeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Original filename cannot be null or empty");
        }

        // Nettoyer le nom de fichier (supprimer les caractères dangereux)
        String cleanFilename = originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_");

        // Ajouter un timestamp pour garantir l'unicité
        String extension = getFileExtension(cleanFilename);
        String nameWithoutExtension = cleanFilename.substring(0, cleanFilename.lastIndexOf('.'));

        return System.currentTimeMillis() + "_" + nameWithoutExtension + "." + extension;
    }

    /**
     * Valide qu'un nom de fichier ne contient pas de path traversal
     *
     * @param filename le nom de fichier à valider
     * @return true si le nom est sécurisé
     */
    public static boolean isSafeFilename(String filename) {
        return filename != null &&
                !filename.contains("..") &&
                !filename.contains("/") &&
                !filename.contains("\\") &&
                !filename.isBlank();
    }
}
