import { CorrectionReasonType } from './correction';

/** Ce que corrige une entrée du Journal. Contrat serveur : `JournalCategory`. */
export type JournalCategory = 'ENCASHMENT' | 'ENROLMENT' | 'ATTENDANCE' | 'JUSTIFICATION' | 'CATCH_UP';

/**
 * Une entrée du Journal, déjà rédigée en français par le serveur (exigence 12.2). Contrat serveur :
 * `JournalEntry`.
 */
export interface JournalEntry {
  /** `yyyy-MM-ddTHH:mm:ss`, heure de l'école. */
  performedAt: string;
  category: JournalCategory;
  /** « Séance du 07/01/2030 (Math 1ère A) : Amine Belkacem absent → présent ». */
  description: string;
  /** « Janvier (Math 1ère A) : dû à ce jour 0,00 → 2 000,00 DA » ; nul s'il n'y en a pas (12.3). */
  amountEffect: string | null;
  /** Motif d'une correction ; nul pour une justification ou une décision de rattrapage. */
  reasonType: CorrectionReasonType | null;
  /** Texte du Motif, ou commentaire saisi avec la justification ou la décision. */
  reasonText: string | null;
  performedBy: string;
}

/** Journal d'un élève sur une période, le plus récent d'abord. Contrat serveur : `CorrectionJournal`. */
export interface CorrectionJournal {
  studentId: number;
  studentName: string;
  /** Premier jour inclus, `yyyy-MM-dd` ; nul pour « depuis le début ». */
  from: string | null;
  /** Dernier jour inclus, `yyyy-MM-dd` ; nul pour « jusqu'à aujourd'hui ». */
  to: string | null;
  entries: JournalEntry[];
}
