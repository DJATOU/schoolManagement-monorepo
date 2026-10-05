package com.school.management.service.correction;

/**
 * Nature d'un effet d'une correction autre qu'un écart de montant (exigence 4.2).
 */
public enum CorrectionEffectType {

    /** Un Encaissement est annulé : il reste au registre, mais ne compte plus. */
    ENCASHMENT_CANCELLED,

    /** Un Encaissement est enregistré, par exemple en remplacement d'un autre. */
    ENCASHMENT_CREATED,

    /** Une Imputation cesse de créditer sa Série. */
    ALLOCATION_NEUTRALIZED,

    /** Une Imputation crédite une Série. */
    ALLOCATION_CREATED,

    /** La Fenêtre_Inscription d'un étudiant change : arrivée, départ, réouverture. */
    ENROLMENT_WINDOW_CHANGED,

    /** Une absence est retirée. */
    ABSENCE_REMOVED,

    /** Une présence ordinaire est retirée : l'étudiant n'était plus inscrit ce jour-là. */
    PRESENCE_REMOVED,

    /**
     * Une présence ordinaire sort de la Fenêtre_Inscription et reste : la séance a été suivie,
     * elle demeure facturable comme séance consommée.
     */
    PRESENCE_KEPT,

    /** Une présence ou une absence est enregistrée dans la même opération que la correction. */
    ATTENDANCE_RECORDED,
    /** Une présence de rattrapage saisie à tort est retirée (exigence 9.1). */
    CATCH_UP_REMOVED,
    /** Une Séance validée par erreur redevient à valider, ses lignes désactivées (exigence 10.1). */
    SESSION_UNVALIDATED,
    /** La séance manquée qu'un rattrapage compensait redevient à rattraper (exigence 9.2). */
    CATCH_UP_REOPENED,
    /** La demande de rattrapage qui avait produit la présence retirée passe à « annulée » (D9). */
    CATCH_UP_REQUEST_CANCELLED,
    /** Une présence passe à absent ou l'inverse, justification comprise (exigences 8.1 à 8.3). */
    ATTENDANCE_CHANGED,

    /**
     * Le versé d'une Série dépasse son coût après la correction : le trop-perçu est annoncé, ni
     * reporté ni remboursé par elle (exigence 5.9).
     */
    EXCESS_LEFT,

    /** Une Séance devient facturable à l'étudiant. */
    SESSION_BECAME_BILLABLE,

    /** Une Séance cesse d'être facturable à l'étudiant. */
    SESSION_BECAME_NOT_BILLABLE,

    /** Une part de ventilation change de Séance, sans changer de Série ni d'Encaissement. */
    VENTILATION_MOVED,

    /** Le mode de paiement ou la note d'un Encaissement changent, sans Remplacement. */
    ENCASHMENT_DETAILS_EDITED,

    /** Une Paie d'enseignant est annulée : elle reste lisible, mais ne compte plus. */
    PAYOUT_CANCELLED,

    /** Une Paie d'enseignant est enregistrée en remplacement d'une autre. */
    PAYOUT_CREATED,

    /** Ce que la Série a versé à l'enseignant et gardé pour l'école change. */
    PAYOUT_SHARES_CHANGED
}
