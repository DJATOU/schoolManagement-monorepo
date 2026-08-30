export interface Attendance {
  id: number;
  studentId: number;
  sessionId: number;
  groupId: number;
  originalGroupId?: number;
  sessionSeriesId?: number;

  isPresent: boolean;
  isJustified: boolean;
  justificationReason?: string;

  /**
   * Séance suivie hors du groupe de l'étudiant.
   *
   * Ce drapeau ne dit **que** cela. Il ne décide pas de la facturation : le serveur classe la
   * présence à l'enregistrement (`catchUpBillingState`), lui seul connaissant les inscriptions
   * de l'étudiant.
   */
  isCatchUp: boolean;
  catchUpForSessionId?: number;
  catchUpFromGroupId?: number;

  /**
   * État de facturation du rattrapage, décidé par le serveur. Absent pour une présence ordinaire.
   *
   * - `PENDING` : vrai rattrapage à préciser. Ne facture rien tant que la séance manquée et la
   *   décision « déjà payée » ne sont pas renseignées.
   * - `RESOLVED` : les deux décisions sont prises.
   * - `HOST_BILLED` : aucun groupe de même niveau et même matière, la séance est facturée sur
   *   place. L'absence de séance manquée est ici voulue.
   */
  catchUpBillingState?: 'PENDING' | 'RESOLVED' | 'HOST_BILLED';

  /** Séance manquée rattrapée, renseignée à la résolution d'un rattrapage. */
  missedSessionId?: number;

  /** Décision « la séance manquée était-elle déjà payée ? ». Absente tant que non tranchée. */
  missedSessionAlreadyPaid?: boolean;

  paymentStatus: 'PENDING' | 'PAID' | 'EXEMPTED' | 'COVERED_BY_CATCHUP';

  description?: string;
  dateCreation: Date;
  dateUpdate: Date;
  createdBy: string;
  updatedBy: string;
  active: boolean;
}
