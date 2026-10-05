package com.school.management.persistance;

/**
 * Nature d'une correction, une valeur par opération des exigences 2 à 10 de la spec
 * admin-corrections.
 *
 * <p>La colonne n'est pas contrainte en base : ajouter une opération ne demande qu'une valeur ici,
 * sans migration.</p>
 */
public enum CorrectionAction {

    /** Exigence 2 : annulation d'un encaissement. */
    ENCASHMENT_CANCELLED,
    /** Exigence 3 : remplacement d'un encaissement par un encaissement corrigé. */
    ENCASHMENT_REPLACED,
    /** Exigence 3.6 : mode de paiement ou note corrigés, sans remplacement. */
    ENCASHMENT_DETAILS_EDITED,

    /** Exigence 5 : date d'arrivée corrigée. */
    ARRIVAL_DATE_CORRECTED,
    /** Exigence 6 : départ enregistré, date de départ corrigée, inscription rouverte. */
    DEPARTURE_RECORDED,
    DEPARTURE_CORRECTED,
    ENROLMENT_REOPENED,

    /** Exigence 8 : présence corrigée, ajoutée ou retirée. */
    PRESENCE_CHANGED,
    ATTENDANCE_ADDED,
    ATTENDANCE_REMOVED,
    /** Exigence 9 : présence de rattrapage retirée. */
    CATCH_UP_REMOVED,

    /** Exigence 10 : séance dévalidée. */
    SESSION_UNVALIDATED,

    /** Spec teacher-payroll, exigence 7.1 : paie d'enseignant annulée. */
    PAYOUT_CANCELLED,
    /** Spec teacher-payroll, exigence 7.2 : paie initiale remplacée par une paie à un autre taux. */
    PAYOUT_REPLACED
}
