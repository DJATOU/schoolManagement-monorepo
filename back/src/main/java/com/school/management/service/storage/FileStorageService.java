package com.school.management.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Interface pour la gestion du stockage de fichiers.
 * Permet de basculer facilement entre stockage local et cloud (S3, Azure, etc.)
 */
public interface FileStorageService {

    /**
     * Sauvegarde un fichier et retourne le nom du fichier généré
     *
     * @param file le fichier à sauvegarder
     * @return le nom du fichier sauvegardé
     * @throws IOException si une erreur survient lors de la sauvegarde
     */
    String saveFile(MultipartFile file) throws IOException;

    /**
     * Charge un fichier depuis le stockage
     *
     * @param filename le nom du fichier à charger
     * @return la ressource correspondant au fichier
     * @throws IOException si le fichier n'existe pas ou n'est pas accessible
     */
    Resource loadFile(String filename) throws IOException;

    /**
     * Supprime un fichier du stockage
     *
     * @param filename le nom du fichier à supprimer
     * @throws IOException si une erreur survient lors de la suppression
     */
    void deleteFile(String filename) throws IOException;

    /**
     * Vérifie si un fichier existe dans le stockage
     *
     * @param filename le nom du fichier
     * @return true si le fichier existe, false sinon
     */
    boolean fileExists(String filename);

    /**
     * Initialise le stockage (création de répertoires, connexion au cloud, etc.)
     *
     * @throws IOException si l'initialisation échoue
     */
    void init() throws IOException;
}
