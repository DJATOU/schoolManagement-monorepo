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

    /** Une absence est retirée. */
    ABSENCE_REMOVED,

    /** Une Séance devient facturable à l'étudiant. */
    SESSION_BECAME_BILLABLE,

    /** Une Séance cesse d'être facturable à l'étudiant. */
    SESSION_BECAME_NOT_BILLABLE,

    /** Une part de ventilation change de Séance, sans changer de Série ni d'Encaissement. */
    VENTILATION_MOVED,

    /** Le mode de paiement ou la note d'un Encaissement changent, sans Remplacement. */
    ENCASHMENT_DETAILS_EDITED
}
