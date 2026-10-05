package com.school.management.persistance;

/**
 * Chemin d'encaissement qui a produit un Encaissement.
 *
 * <p>Les deux chemins appliquent des plafonds différents — coût de la série pour un
 * encaissement ordinaire, prix d'une séance pour un rattrapage — et le reçu doit le dire.</p>
 */
public enum EncashmentKind {

    /** Versement sur une série, avec report éventuel sur les séries suivantes. */
    REGULAR,

    /** Versement ciblé sur une séance suivie en rattrapage. */
    CATCH_UP
}
