package com.school.management.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service pour générer les URLs des images de manière centralisée et configurable.
 * Supporte différents environnements (dev, staging, prod) via configuration.
 */
@Service
public class ImageUrlService {

    @Value("${server.base-url}")
    private String serverBaseUrl;

    private static final String DEFAULT_AVATAR = "assets/default-avatar.png";

    /**
     * Génère l'URL complète pour une photo d'étudiant
     *
     * @param photoFilename le nom du fichier photo (peut être null)
     * @return l'URL complète ou l'avatar par défaut
     */
    public String getStudentPhotoUrl(String photoFilename) {
        if (photoFilename == null || photoFilename.isBlank()) {
            return DEFAULT_AVATAR;
        }
        return serverBaseUrl + "/api/students/photos/" + photoFilename;
    }

    /**
     * Génère l'URL complète pour une photo de professeur
     *
     * @param photoFilename le nom du fichier photo (peut être null)
     * @return l'URL complète ou l'avatar par défaut
     */
    public String getTeacherPhotoUrl(String photoFilename) {
        if (photoFilename == null || photoFilename.isBlank()) {
            return DEFAULT_AVATAR;
        }
        return serverBaseUrl + "/personne/" + photoFilename;
    }

    /**
     * Génère l'URL complète pour une photo (générique)
     * Utilise l'endpoint /personne/ par défaut
     *
     * @param photoFilename le nom du fichier photo (peut être null)
     * @return l'URL complète ou l'avatar par défaut
     */
    public String getPhotoUrl(String photoFilename) {
        if (photoFilename == null || photoFilename.isBlank()) {
            return DEFAULT_AVATAR;
        }
        return serverBaseUrl + "/personne/" + photoFilename;
    }

    /**
     * Extrait le nom du fichier depuis un chemin complet (pour rétrocompatibilité)
     * Exemple: "C:/Users/djato/Pictures/personne/photo.jpg" -> "photo.jpg"
     *
     * @param photoPath le chemin complet ou le nom du fichier
     * @return le nom du fichier uniquement
     */
    public String extractFilename(String photoPath) {
        if (photoPath == null || photoPath.isBlank()) {
            return null;
        }

        // Si c'est déjà juste un nom de fichier (pas de / ou \), le retourner tel quel
        if (!photoPath.contains("/") && !photoPath.contains("\\")) {
            return photoPath;
        }

        // Extraire le nom du fichier du chemin complet
        int lastSlash = Math.max(photoPath.lastIndexOf('/'), photoPath.lastIndexOf('\\'));
        return lastSlash >= 0 ? photoPath.substring(lastSlash + 1) : photoPath;
    }
}
