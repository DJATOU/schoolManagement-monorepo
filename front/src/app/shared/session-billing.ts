import { SessionHistoryDTO } from '../models/session/SessionHistoryDTO';
import { SeriesHistoryDTO } from '../models/sessionSerie/SeriesHistoryDTO';

/**
 * Lecture du prorata dans les historiques (exigences 11.3 à 11.6).
 *
 * <p>Une séance écartée du calcul du coût — tenue avant l'inscription de l'étudiant et non
 * suivie — reste affichée dans l'historique, mais elle <strong>n'est pas une dette</strong>.
 * La confondre avec une séance facturable impayée fait croire à un retard de paiement qui
 * n'existe pas. Les trois vues qui présentent l'historique (fenêtre de paiement, historique
 * complet, PDF) doivent donc trancher exactement de la même façon : d'où ce module unique
 * plutôt qu'un test recopié dans chaque composant.</p>
 */

/** Codes de présence renvoyés par le backend pour lesquels une assiduité est renseignée. */
const ATTENDANCE_RECORDED = ['PRESENT', 'ABSENT'];

/**
 * Vrai lorsque la séance est écartée de la facturation de cet étudiant.
 *
 * <p>Trois sources, par ordre de fiabilité décroissante :</p>
 * <ol>
 *   <li>{@link SessionHistoryDTO.inclusionReason} — le motif du résolveur de séances
 *       facturables. Il porte la décision <em>et</em> sa raison ; c'est la source de vérité.</li>
 *   <li>{@link SessionHistoryDTO.billable} — le même verdict sans sa raison, suffisant ici.</li>
 *   <li>Repli documenté, pour les réponses qui ne portent encore ni l'un ni l'autre : une
 *       séance sans assiduité renseignée et sans montant affecté. C'est une
 *       <strong>approximation</strong> — une séance postérieure à l'inscription, encore sans
 *       feuille de présence, y ressemble et se retrouve à tort classée non facturée. Elle
 *       reste toutefois plus juste que l'affichage « non payé » qu'elle a remplacé, qui
 *       présentait toutes ces séances comme des impayés.</li>
 * </ol>
 */
export function isExcludedSession(session: SessionHistoryDTO): boolean {
  if (session.inclusionReason !== undefined && session.inclusionReason !== null) {
    return session.inclusionReason === 'EXCLUDED';
  }
  if (session.billable !== undefined && session.billable !== null) {
    return session.billable === false;
  }
  const attendanceRecorded = ATTENDANCE_RECORDED.includes(session.attendanceStatus);
  const carriesMoney = (session.amountPaid ?? 0) > 0;
  return !attendanceRecorded && !carriesMoney;
}

/**
 * Vrai lorsque la séance est facturable et reste à régler : c'est la seule catégorie qui
 * constitue une dette, et celle dont l'exigence 11.4 demande de distinguer les exclues.
 */
export function isBillableUnpaid(session: SessionHistoryDTO): boolean {
  return !isExcludedSession(session)
    && session.isExempted !== true
    && (session.paymentStatus === 'UNPAID' || session.paymentStatus === 'PARTIAL');
}

/**
 * Vrai lorsque la séance est facturée parce que l'étudiant l'a suivie alors qu'il n'était pas
 * encore inscrit (exigence 11.5). Sans cette étiquette, la facturation d'une séance antérieure
 * à l'inscription paraît arbitraire.
 *
 * <p>Le motif du serveur est privilégié : {@code ATTENDED_BEFORE_ENROLMENT} désigne exactement
 * la population que l'exigence demande d'expliquer. L'indicateur {@link
 * SessionHistoryDTO.catchUpSession} de la feuille de présence, lui, marque <em>tous</em> les
 * rattrapages, y compris ceux postérieurs à l'inscription — dont la facturation découle
 * simplement de la date et n'a besoin d'aucune justification à l'écran. Il ne sert donc que de
 * repli, quand le motif est absent.</p>
 */
export function isCatchUpBilled(session: SessionHistoryDTO): boolean {
  if (session.inclusionReason !== undefined && session.inclusionReason !== null) {
    return session.inclusionReason === 'ATTENDED_BEFORE_ENROLMENT';
  }
  return session.catchUpSession === true && !isExcludedSession(session);
}

/**
 * Nombre de séances facturables de la série (exigence 11.6).
 *
 * <p>Le décompte du serveur est retenu s'il est présent ; sinon il est déduit des séances
 * affichées, l'historique restituant l'union des séances facturables et des séances
 * écartées pour un inscrit régulier.</p>
 */
export function countBillableSessions(series: SeriesHistoryDTO): number {
  if (series.billableSessions !== undefined && series.billableSessions !== null) {
    return series.billableSessions;
  }
  return (series.sessions ?? []).filter(session => !isExcludedSession(session)).length;
}

/** Nombre de séances écartées de la facturation, pour la note d'explication du prorata. */
export function countExcludedSessions(series: SeriesHistoryDTO): number {
  return (series.sessions ?? []).filter(isExcludedSession).length;
}
