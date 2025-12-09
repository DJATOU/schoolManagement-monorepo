package com.school.management.infrastructure.storage;

import com.school.management.service.storage.FileStorageService;
import com.school.management.util.FileValidationUtil;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Service de gestion de fichiers avec gestion automatique du rollback.
 *
 * PHASE 1 REFACTORING: Extrait depuis StudentController et TeacherController
 * pour centraliser la logique de gestion des fichiers.
 *
 * Responsabilités:
 * - Upload de fichiers avec validation
 * - Téléchargement de fichiers
 * - Suppression de fichiers
 * - Rollback automatique en cas d'erreur
 *
 * @author Claude Code
 * @since Phase 1 Refactoring
 */
@Service
@RequiredArgsConstructor
public class FileManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileManagementService.class);

    private final FileStorageService fileStorageService;

    /**
     * Upload un fichier et retourne le nom du fichier sauvegardé.
     * Lance une exception en cas d'échec.
     *
     * @param file le fichier à uploader
     * @return le nom du fichier sauvegardé
     * @throws IOException si l'upload échoue
     * @throws IllegalArgumentException si la validation échoue
     */
    public String uploadFile(MultipartFile file) throws IOException {
        LOGGER.debug("Uploading file: {}", file.getOriginalFilename());

        // Validation du fichier (type, taille, sécurité)
        FileValidationUtil.validateImageFile(file);

        // Sauvegarde via l'abstraction de stockage
        String savedFileName = fileStorageService.saveFile(file);

        LOGGER.info("File uploaded successfully: {}", savedFileName);
        return savedFileName;
    }

    /**
     * Récupère un fichier depuis le stockage.
     *
     * @param filename le nom du fichier à récupérer
     * @return la ressource correspondant au fichier
     * @throws IOException si le fichier n'existe pas ou n'est pas accessible
     * @throws SecurityException si le nom de fichier n'est pas sécurisé
     */
    public Resource getFile(String filename) throws IOException {
        LOGGER.debug("Retrieving file: {}", filename);

        // Validation sécurité (protection path traversal)
        if (!FileValidationUtil.isSafeFilename(filename)) {
            LOGGER.warn("Attempted to access unsafe filename: {}", filename);
            throw new SecurityException("Invalid filename: " + filename);
        }

        return fileStorageService.loadFile(filename);
    }

    /**
     * Supprime un fichier du stockage.
     *
     * @param filename le nom du fichier à supprimer
     * @throws IOException si la suppression échoue
     */
    public void deleteFile(String filename) throws IOException {
        LOGGER.info("Deleting file: {}", filename);

        if (!FileValidationUtil.isSafeFilename(filename)) {
            throw new SecurityException("Invalid filename: " + filename);
        }

        fileStorageService.deleteFile(filename);
        LOGGER.info("File deleted successfully: {}", filename);
    }

    /**
     * Upload un fichier avec rollback automatique en cas d'erreur.
     * Utilisé pour des opérations transactionnelles où le fichier doit être
     * supprimé si l'opération échoue.
     *
     * Exemple d'utilisation:
     * <pre>
     * FileUploadResult result = fileManagementService.uploadWithRollback(file);
     * if (result.isSuccess()) {
     *     studentDto.setPhoto(result.getFilename());
     *     studentService.save(studentDto);
     * } else {
     *     return ResponseEntity.badRequest().body(result.getErrorMessage());
     * }
     * </pre>
     *
     * @param file le fichier à uploader
     * @return un résultat contenant soit le nom du fichier, soit l'erreur
     */
    public FileUploadResult uploadWithRollback(MultipartFile file) {
        String savedFileName = null;

        try {
            savedFileName = uploadFile(file);
            return FileUploadResult.success(savedFileName);

        } catch (IllegalArgumentException e) {
            // Erreur de validation (type, taille, etc.)
            LOGGER.warn("File validation failed: {}", e.getMessage());
            return FileUploadResult.failure(e.getMessage());

        } catch (IOException e) {
            // Erreur d'upload
            LOGGER.error("File upload failed: {}", e.getMessage(), e);

            // Tentative de cleanup si le fichier a été partiellement sauvegardé
            if (savedFileName != null) {
                try {
                    deleteFile(savedFileName);
                    LOGGER.info("Rollback successful: deleted {}", savedFileName);
                } catch (IOException deleteEx) {
                    LOGGER.error("Failed to rollback file: {}", savedFileName, deleteEx);
                }
            }

            return FileUploadResult.failure("File upload failed: " + e.getMessage());

        } catch (Exception e) {
            // Erreur inattendue
            LOGGER.error("Unexpected error during file upload", e);

            if (savedFileName != null) {
                try {
                    deleteFile(savedFileName);
                } catch (IOException deleteEx) {
                    LOGGER.error("Failed to rollback file: {}", savedFileName, deleteEx);
                }
            }

            return FileUploadResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Vérifie si un fichier existe dans le stockage.
     *
     * @param filename le nom du fichier
     * @return true si le fichier existe, false sinon
     */
    public boolean fileExists(String filename) {
        if (!FileValidationUtil.isSafeFilename(filename)) {
            return false;
        }
        return fileStorageService.fileExists(filename);
    }

    /**
     * Résultat d'un upload de fichier avec rollback.
     * Contient soit le nom du fichier en cas de succès,
     * soit un message d'erreur en cas d'échec.
     */
    @Value
    @Builder
    public static class FileUploadResult {
        boolean success;
        String filename;
        String errorMessage;

        /**
         * Crée un résultat de succès.
         *
         * @param filename le nom du fichier uploadé
         * @return le résultat de succès
         */
        public static FileUploadResult success(String filename) {
            return FileUploadResult.builder()
                .success(true)
                .filename(filename)
                .build();
        }

        /**
         * Crée un résultat d'échec.
         *
         * @param errorMessage le message d'erreur
         * @return le résultat d'échec
         */
        public static FileUploadResult failure(String errorMessage) {
            return FileUploadResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
        }
    }
}
