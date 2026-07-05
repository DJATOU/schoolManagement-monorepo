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
