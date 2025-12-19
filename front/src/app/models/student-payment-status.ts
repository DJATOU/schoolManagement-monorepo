/**
 * Interface pour le statut de paiement d'un étudiant
 * Utilisée pour afficher un indicateur visuel sur la card de l'étudiant
 */
export interface StudentPaymentStatus {
  /** ID de l'étudiant */
  studentId: number;

  /** Statut de paiement global:
   * - 'GOOD': À jour (a payé tout ce qui est dû)
   * - 'LATE': En retard (montant dû > montant payé)
   * - 'NA': Non applicable (aucune session validée/payable)
   */
  paymentStatus: 'GOOD' | 'LATE' | 'NA';

  /** Liste des groupes où l'étudiant est en retard (vide si GOOD ou NA) */
  lateGroups: LateGroupDetails[];

  /** Montant total dû sur tous les groupes */
  totalDue: number;

  /** Montant total payé sur tous les groupes */
  totalPaid: number;
}

/**
 * Détails d'un groupe où l'étudiant est en retard
 */
export interface LateGroupDetails {
  /** ID du groupe */
  groupId: number;

  /** Nom du groupe */
  groupName: string;

  /** Nombre de sessions impayées ou partiellement payées */
  unpaidSessionsCount: number;

  /** Montant dû pour ce groupe */
  dueAmount: number;

  /** Montant payé pour ce groupe */
  paidAmount: number;
}
