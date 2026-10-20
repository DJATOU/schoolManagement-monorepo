/**
 * Options partagées pour les listes déroulantes des formulaires (étudiant, enseignant).
 * Centralisé ici pour rester cohérent entre les différents formulaires.
 */

/** Préférence de communication : téléphone ou email. */
export interface CommunicationOption {
  value: string;
  labelKey: string; // clé de traduction ngx-translate
}

export const COMMUNICATION_OPTIONS: CommunicationOption[] = [
  { value: 'phone', labelKey: 'COMMON.PHONE' },
  { value: 'email', labelKey: 'COMMON.EMAIL' }
];

/** Mode de règlement d'un versement : code stocké par le serveur et clé de son libellé. */
export interface PaymentMethodOption {
  value: string;
  labelKey: string;
}

/**
 * Modes de règlement proposés à l'encaissement.
 *
 * <p>Partagés par le dialogue de versement et l'historique des versements : un reçu réimprimé
 * doit nommer le mode exactement comme le reçu d'origine.</p>
 */
export const PAYMENT_METHOD_OPTIONS: PaymentMethodOption[] = [
  { value: 'cash', labelKey: 'payment.dialog.methods.cash' },
  { value: 'cheque', labelKey: 'payment.dialog.methods.cheque' },
  { value: 'carte_bancaire', labelKey: 'payment.dialog.methods.card' },
  { value: 'autre', labelKey: 'payment.dialog.methods.other' }
];

/**
 * Liste des nationalités. L'Algérie est placée en premier et utilisée comme
 * valeur par défaut dans les formulaires.
 */
export const NATIONALITIES: string[] = [
  'Algérienne',
  'Tunisienne',
  'Marocaine',
  'Libyenne',
  'Mauritanienne',
  'Égyptienne',
  'Française',
  'Espagnole',
  'Italienne',
  'Allemande',
  'Britannique',
  'Belge',
  'Suisse',
  'Canadienne',
  'Américaine',
  'Turque',
  'Saoudienne',
  'Émiratie',
  'Qatarienne',
  'Koweïtienne',
  'Jordanienne',
  'Libanaise',
  'Syrienne',
  'Irakienne',
  'Palestinienne',
  'Sénégalaise',
  'Malienne',
  'Nigérienne',
  'Ivoirienne',
  'Camerounaise',
  'Chinoise',
  'Indienne',
  'Autre'
];

/** Nationalité utilisée par défaut dans les formulaires. */
export const DEFAULT_NATIONALITY = 'Algérienne';

/**
 * Clés de traduction des nationalités.
 *
 * <p>La valeur enregistrée reste la chaîne française (donnée en base) : seul le libellé affiché
 * est traduit.</p>
 */
const NATIONALITY_LABEL_KEYS: Record<string, string> = {
  'Algérienne': 'NATIONALITY.ALGERIENNE',
  'Tunisienne': 'NATIONALITY.TUNISIENNE',
  'Marocaine': 'NATIONALITY.MAROCAINE',
  'Libyenne': 'NATIONALITY.LIBYENNE',
  'Mauritanienne': 'NATIONALITY.MAURITANIENNE',
  'Égyptienne': 'NATIONALITY.EGYPTIENNE',
  'Française': 'NATIONALITY.FRANCAISE',
  'Espagnole': 'NATIONALITY.ESPAGNOLE',
  'Italienne': 'NATIONALITY.ITALIENNE',
  'Allemande': 'NATIONALITY.ALLEMANDE',
  'Britannique': 'NATIONALITY.BRITANNIQUE',
  'Belge': 'NATIONALITY.BELGE',
  'Suisse': 'NATIONALITY.SUISSE',
  'Canadienne': 'NATIONALITY.CANADIENNE',
  'Américaine': 'NATIONALITY.AMERICAINE',
  'Turque': 'NATIONALITY.TURQUE',
  'Saoudienne': 'NATIONALITY.SAOUDIENNE',
  'Émiratie': 'NATIONALITY.EMIRATIE',
  'Qatarienne': 'NATIONALITY.QATARIENNE',
  'Koweïtienne': 'NATIONALITY.KOWEITIENNE',
  'Jordanienne': 'NATIONALITY.JORDANIENNE',
  'Libanaise': 'NATIONALITY.LIBANAISE',
  'Syrienne': 'NATIONALITY.SYRIENNE',
  'Irakienne': 'NATIONALITY.IRAKIENNE',
  'Palestinienne': 'NATIONALITY.PALESTINIENNE',
  'Sénégalaise': 'NATIONALITY.SENEGALAISE',
  'Malienne': 'NATIONALITY.MALIENNE',
  'Nigérienne': 'NATIONALITY.NIGERIENNE',
  'Ivoirienne': 'NATIONALITY.IVOIRIENNE',
  'Camerounaise': 'NATIONALITY.CAMEROUNAISE',
  'Chinoise': 'NATIONALITY.CHINOISE',
  'Indienne': 'NATIONALITY.INDIENNE',
  'Autre': 'NATIONALITY.OTHER'
};

/**
 * Clé de traduction d'une nationalité enregistrée, ou `null` pour une valeur inconnue
 * (saisie ancienne hors liste), que l'affichage reprend alors telle quelle.
 */
export function nationalityLabelKey(value: string | null | undefined): string | null {
  return value ? NATIONALITY_LABEL_KEYS[value] ?? null : null;
}

/**
 * Clés de traduction des codes de sexe stockés par les formulaires.
 * Le formulaire enregistre un code ('male'/'female') : le récapitulatif doit afficher
 * le libellé traduit, pas le code brut.
 */
export const GENDER_LABEL_KEYS: Record<string, string> = {
  male: 'COMMON.MALE',
  female: 'COMMON.FEMALE'
};

/**
 * Clé de traduction d'un code de sexe, ou `null` pour une valeur inconnue.
 *
 * <p>Deux codifications coexistent : les formulaires enregistrent « male » / « female », l'import
 * CSV « M » / « F ». Les deux donnent le même libellé.</p>
 */
export function genderLabelKey(code: string | null | undefined): string | null {
  switch ((code ?? '').trim().toLowerCase()) {
    case 'male':
    case 'm':
      return GENDER_LABEL_KEYS['male'];
    case 'female':
    case 'f':
      return GENDER_LABEL_KEYS['female'];
    default:
      return null;
  }
}

/** Clés de traduction des codes de statut matrimonial ('single'/'married'). */
export const MARITAL_STATUS_LABEL_KEYS: Record<string, string> = {
  single: 'TEACHER_FORM.SINGLE',
  married: 'TEACHER_FORM.MARRIED'
};
