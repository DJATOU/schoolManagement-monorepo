package com.school.management.dto.importcsv;

import java.util.ArrayList;
import java.util.List;

/**
 * Résumé d'un import CSV : nombre d'éléments importés et liste des erreurs par ligne.
 *
 * <p>L'import est tolérant aux erreurs : les lignes valides sont créées et les lignes en échec
 * sont rapportées individuellement (numéro de ligne 1-indexé, en-tête = ligne 1).</p>
 */
public class ImportResultDTO {

    /** Erreur d'import associée à une ligne du fichier. */
    public static class ImportError {
        private int line;
        private String message;

        public ImportError() {
        }

        public ImportError(int line, String message) {
            this.line = line;
            this.message = message;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    private int imported = 0;
    private final List<ImportError> errors = new ArrayList<>();

    /**
     * Lignes importées avec une réserve : l'élément est créé, mais inutilisable pour une partie
     * du métier tant que l'administrateur ne l'a pas complété. Distinct d'une erreur, qui signale
     * une ligne NON importée — les confondre ferait croire à un échec, ou masquerait une lacune.
     */
    private final List<ImportError> warnings = new ArrayList<>();

    public void incrementImported() {
        this.imported++;
    }

    public void addError(int line, String message) {
        this.errors.add(new ImportError(line, message));
    }

    public void addWarning(int line, String message) {
        this.warnings.add(new ImportError(line, message));
    }

    public List<ImportError> getWarnings() {
        return warnings;
    }

    public int getImported() {
        return imported;
    }

    public void setImported(int imported) {
        this.imported = imported;
    }

    public List<ImportError> getErrors() {
        return errors;
    }
}
