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
    ENCASHMENT_DETAILS_EDITED
}
