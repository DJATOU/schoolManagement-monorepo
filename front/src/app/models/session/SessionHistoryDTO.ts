export interface SessionHistoryDTO {
    catchUpSession: boolean;
    sessionId: number;
    sessionName: string;
    sessionDate: string; // ou Date si vous gérez le parsing
    attendanceStatus: string;
    isJustified: boolean;
    description: string;
    paymentStatus: string;
    amountPaid: number;
    paymentDate: string;
    // Présence exemptée : vrai lorsque l'étudiant bénéficie d'une exemption (réduction 100 %)
    // sur cette séance ; pilote la légende « Présent et exempté ».
    isExempted?: boolean;
    // Montant remboursé rattaché à cette séance
    refundedAmount?: number;
    /**
     * Séance retenue dans le coût au prorata de l'étudiant (exigences 11.3, 11.4).
     *
     * Optionnel : les réponses d'une version antérieure du serveur ne le portent pas. Quand il
     * est absent, l'historique retombe sur l'assiduité renseignée (voir
     * `shared/session-billing.ts`).
     */
    billable?: boolean;
    /**
     * Motif d'inclusion (ou d'exclusion) de la séance dans la facturation, renseigné par le
     * serveur qui détient la règle du prorata.
     *
     * Complète `billable` : savoir qu'une séance est facturée ne suffit pas, il faut savoir
     * *pourquoi* pour n'étiqueter « rattrapage » que les séances antérieures à l'inscription
     * facturées parce que suivies (exigence 11.5).
     *
     * Optionnel pour la même raison que `billable`.
     */
    inclusionReason?: 'AFTER_ENROLMENT' | 'ATTENDED_BEFORE_ENROLMENT' | 'EXCLUDED';
    /**
     * Montant net dû pour cette séance, réduction appliquée. Nul si la séance n'est pas
     * facturable ou a été dévalidée.
     *
     * À ne pas confondre avec `amountPaid`, qui est la part des versements affectée à cette
     * séance : sur une séance suivie et impayée, `amountPaid` vaut zéro et ne dit rien du
     * montant attendu.
     */
    amountDue?: number;
    /**
     * Reste à régler sur cette séance, jamais négatif. Nul si la séance n'est pas facturable.
     *
     * Calculé par le serveur : sur une séance partiellement couverte, afficher le montant dû
     * complet surévaluerait la dette.
     */
    amountRemaining?: number;
  
    /**
     * Séance manquée par l'étudiant, puis rattrapée dans un autre groupe.
     *
     * Porté par la séance MANQUÉE, pas par celle de rattrapage. La présence reste une absence :
     * la mention « Rattrapée » est un affichage dérivé, jamais une réécriture de la feuille.
     */
    caughtUpElsewhere?: boolean;
    /** Date de la séance de rattrapage. */
    caughtUpOnDate?: string;
    /** Groupe où la séance a été rattrapée. */
    caughtUpInGroupName?: string;

    /**
     * Séance écartée parce qu'elle est déjà facturée dans la série d'origine du rattrapage
     * compensatoire qui la couvre.
     *
     * À distinguer de `billable === false` : les deux se lisent « non facturée », mais seul
     * celui-ci s'explique par « déjà payée ailleurs ». Sans ce motif, une séance suivie mais non
     * facturée ressemble à une erreur de calcul.
     */
    billedInOriginSeries?: boolean;
    originSeriesName?: string;
    originGroupName?: string;
    originSessionDate?: string;

    /** Séance de rattrapage dont la séance manquée n'est pas déterminable. */
    missedSessionUnknown?: boolean;

    /** Auteur de la dernière modification de la justification, absent si jamais modifiée. */
    justificationUpdatedBy?: string;
    /** Horodatage de la dernière modification de la justification. */
    justificationUpdatedAt?: string;
}
