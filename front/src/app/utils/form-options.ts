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
 * Clés de traduction des codes de sexe stockés par les formulaires.
 * Le formulaire enregistre un code ('male'/'female') : le récapitulatif doit afficher
 * le libellé traduit, pas le code brut.
 */
export const GENDER_LABEL_KEYS: Record<string, string> = {
  male: 'COMMON.MALE',
  female: 'COMMON.FEMALE'
};

/** Clés de traduction des codes de statut matrimonial ('single'/'married'). */
export const MARITAL_STATUS_LABEL_KEYS: Record<string, string> = {
  single: 'TEACHER_FORM.SINGLE',
  married: 'TEACHER_FORM.MARRIED'
};
