package com.school.management.service.correction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Empreinte SHA-256 d'un texte UTF-8, en hexadécimal minuscule. */
final class Sha256 {

    private Sha256() {
    }

    static String hex(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 fait partie des algorithmes que toute JVM doit fournir.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
