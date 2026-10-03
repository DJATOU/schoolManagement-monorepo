package com.school.management.dto.session;

/**
 * Motif pour lequel une séance de l'historique est facturée à l'étudiant, ou ne l'est pas
 * (exigences 11.3, 11.4, 11.5).
 *
 * <p>Sans ce motif, l'interface devait deviner : elle classait « non facturée » toute séance
 * sans feuille de présence et sans montant affecté, ce qui étiquetait à tort une séance
 * <em>future</em> dont la présence n'est simplement pas encore renseignée. Le motif vient
 * désormais du backend, qui détient la règle du prorata.</p>
 *
 * <p>Il sert aussi à n'étiqueter « rattrapage » (exigence 11.5) que les séances réellement
 * concernées : celles <strong>antérieures à l'inscription</strong> facturées parce que suivies.
 * L'indicateur {@code catchUpSession} de la fiche de présence, lui, marque tous les rattrapages,
 * y compris ceux postérieurs à l'inscription — dont la facturation n'a rien de surprenant et ne
 * demande aucune justification à l'écran.</p>
 */
public enum BillingInclusionReason {

    /**
     * Séance d'une Fenêtre_Inscription de l'étudiant, de son arrivée à son départ inclus :
     * facturable à ce seul titre (exigence 1.1), que l'étudiant y ait assisté ou non. Le nom
     * date d'avant la date de sortie ; la valeur est gardée, l'écran la lit.
     */
    AFTER_ENROLMENT,

    /**
     * Séance hors de toute fenêtre, facturée parce que l'étudiant y a assisté (exigence 1.2) :
     * avant son arrivée, après son départ, ou sans inscription au groupe (exigence 1.4). C'est le
     * cas à étiqueter « rattrapage » dans l'historique : sans cette mention, sa facturation paraît
     * arbitraire (exigence 11.5).
     */
    ATTENDED_BEFORE_ENROLMENT,

    /**
     * Séance écartée : hors de toute fenêtre et non suivie. Elle reste visible dans l'historique,
     * non présente et non facturée — ce n'est pas une dette (exigences 11.3, 11.4).
     */
    EXCLUDED
}
