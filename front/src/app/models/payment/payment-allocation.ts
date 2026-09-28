import { Payment } from './payment';

/**
 * Une part de versement reportée sur une série et sa série destinataire.
 *
 * <p>Contrat serveur : `PaymentAllocationResultDTO.CarriedOverAmountDTO`.</p>
 */
export interface CarriedOverAmount {
  /** Identifiant de la série créditée par report. */
  seriesId: number;
  /** Nom de la série, pour que l'écran et le reçu la nomment explicitement. */
  seriesName: string;
  /** Montant reporté sur cette série. */
  amount: number;
}

/**
 * Résultat d'un encaissement réparti : ce qui a été imputé sur la série visée et ce qui a été
 * reporté sur les séries suivantes.
 *
 * <p>Un versement ne crédite plus forcément une seule série : au-delà du montant dû de la série
 * visée, le surplus est reporté sur les séries suivantes par identifiant croissant. Le front doit
 * donc recevoir le détail de la répartition pour la récapituler à l'écran et l'imprimer sur le
 * reçu.</p>
 *
 * <p>Attention à `payment` : c'est la ligne de paiement de la série, dont `amountPaid` porte le
 * <strong>cumul</strong> de tous les versements de cette série. Le montant du versement du jour
 * est `amountReceived`, jamais `payment.amountPaid`.</p>
 *
 * <p>Contrat serveur : `PaymentAllocationResultDTO`, renvoyé par
 * `POST /api/payments/process`.</p>
 */
export interface PaymentAllocationResult {
  /** L'étudiant qui a versé. */
  studentId: number;
  /** Le groupe concerné. */
  groupId: number;
  /** La série visée à la saisie, source des reports. */
  seriesId: number;
  /** Montant total reçu : somme de la part imputée et des parts reportées. */
  amountReceived: number;
  /** Part imputée sur la série visée, nulle si celle-ci était déjà soldée. */
  amountAllocated: number;
  /** Somme des parts reportées, nulle en l'absence de report. */
  amountCarriedOver: number;
  /** Détail des reports, par identifiant de série croissant. */
  carryOvers: CarriedOverAmount[];
  /** Ligne de paiement principale créditée : porte la date d'encaissement faisant foi. */
  payment: Payment;
}
