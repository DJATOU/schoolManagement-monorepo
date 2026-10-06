import { GuideBook, GuideManifest } from '../app/models/guide/guide';
import { buildGuideBook } from '../app/services/user-guide.service';

/** Manifeste minimal du guide : deux chapitres, libellés reconnaissables. */
export function guideManifest(overrides: Partial<GuideManifest> = {}): GuideManifest {
  return {
    lang: 'fr',
    dir: 'ltr',
    languageName: 'Français',
    title: 'Guide',
    labels: {
      audience: 'Pour l\'administrateur', contents: 'Sommaire', previous: 'Précédente', next: 'Suivante',
      pageOf: 'Page {{n}} sur {{total}}', bookMode: 'Livre', scrollMode: 'Continu', language: 'Langue',
      example: 'Exemple', note: 'À savoir', warning: 'Attention', loading: 'Chargement', error: 'Erreur',
      retry: 'Réessayer', turnHint: 'Tournez', keyboardHint: 'Flèches', bookAria: 'Livre du guide'
    },
    chapters: [{ id: 'demarrer', file: 'a.md' }, { id: 'paiements', file: 'b.md' }],
    ...overrides
  };
}

/** Trois pages. */
export const CHAPTER_A = '# Démarrer\n\nPremière page.\n\n<!-- page -->\n\nDeuxième page.\n\n<!-- page -->\n\nTroisième page.';

/** Trois pages, un encadré de chaque sorte et un lien vers le premier chapitre. */
export const CHAPTER_B = '# Paiements\n\n> [!TIP]\n> Lucas verse 800 DA.\n\n<!-- page -->\n\n'
  + '> [!WARNING]\n> Refusé en entier.\n\nVoir [Démarrer](#demarrer).\n\n<!-- page -->\n\n> [!NOTE]\n> Rien sans motif.';

/**
 * Livre de 8 pages : 0 couverture, 1 sommaire, 2–4 « Démarrer », 5–7 « Paiements ».
 * En double page : 0 = [—, couverture], 1 = [sommaire, 2], 2 = [3, 4], 3 = [5, 6], 4 = [7, —].
 */
export function guideBook(overrides: Partial<GuideManifest> = {}): GuideBook {
  return buildGuideBook(guideManifest(overrides), [CHAPTER_A, CHAPTER_B]);
}
