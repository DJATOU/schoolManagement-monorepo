/**
 * Plafond de remboursement d'un versement.
 *
 * <p>Les trois montants sont exposés ensemble et non le seul plafond : un administrateur qui a une
 * famille devant lui doit pouvoir dire <em>pourquoi</em> le plafond vaut ce qu'il vaut. « Vous ne
 * pouvez pas rembourser plus de 40 € » sans dire que 60 € ont déjà été rendus sur un versement de
 * 100 € l'oblige à chercher l'information ailleurs.</p>
 */
export interface RefundCap {
  paymentId: number;
  /** Montant versé du paiement. */
  amountPaid: number;
  /** Somme des remboursements déjà accordés sur ce versement. */
  alreadyRefunded: number;
  /** Plafond restant : `amountPaid - alreadyRefunded`. */
  refundableCap: number;
}

/** Demande d'enregistrement d'un remboursement. */
export interface RefundRequest {
  paymentId: number;
  /**
   * Bénéficiaire, **ignoré par le serveur** : il retient toujours l'étudiant du versement.
   * Transmis pour rester compatible avec le contrat existant.
   */
  studentId: number;
  amount: number;
  /** Motif obligatoire : une sortie de caisse doit pouvoir être justifiée lors d'un contrôle. */
  reason: string;
  refundDate?: Date;
}

/** Remboursement enregistré, tel que renvoyé par le serveur. */
export interface Refund {
  id: number;
  paymentId: number;
  studentId: number;
  amount: number;
  refundDate: Date;
  /** Numéro de pièce, de la forme `REMB-AAAA-NNNN`. */
  refundNumber: string;
  /** Motif. Nul pour un remboursement antérieur à la traçabilité. */
  reason?: string;
  /**
   * Plafond restant après cet enregistrement.
   *
   * <p>Renvoyé à la création pour que l'interface actualise ses montants sans second appel. Absent
   * des lectures où il n'a pas de sens (historique, listes).</p>
   */
  refundableCap?: number;
}

/**
 * Données du reçu d'un remboursement.
 *
 * <p>Toutes les valeurs viennent du serveur, mentions de repli comprises — « Hors série », « Hors
 * groupe », « Administrateur non identifié ». Le client n'en décide aucune : deux impressions d'une
 * même pièce comptable doivent porter des valeurs identiques, et laisser ces choix ici rendrait
 * cette stabilité dépendante du code frontend.</p>
 */
export interface RefundReceipt {
  refundId: number;
  refundNumber: string;
  refundDate: Date;
  amount: number;
  reason?: string;
  studentFirstName: string;
  studentLastName: string;
  paymentDate?: Date;
  amountPaid: number;
  groupName: string;
  seriesName: string;
  /** Administrateur ayant enregistré le remboursement, ou mention de repli. */
  recordedBy: string;
  /** Rang de cette production : 1 pour l'original, au-delà pour un duplicata. */
  issuanceRank: number;
  issuedAt: string;
  /** Nom de fichier proposé, stable d'une production à l'autre. */
  fileName: string;
}
