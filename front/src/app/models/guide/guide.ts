/**
 * Guide d'utilisation de l'administrateur.
 *
 * <p>Le contenu vit dans {@code src/assets/guides/<langue>/} : un {@code manifest.json} (titre,
 * libellés de l'écran, liste des chapitres) et un fichier Markdown par chapitre. Un chapitre est
 * découpé en pages par la ligne {@code <!-- page -->} : la mise en page d'un livre ne se calcule
 * pas, elle s'écrit, et un test vérifie que chaque page tient dans le format.</p>
 *
 * <p>L'arabe n'existe que dans le guide : l'interface de l'application n'est traduite qu'en
 * français et en anglais. Les libellés de l'écran du guide viennent donc du manifeste, et non des
 * fichiers de traduction de l'application.</p>
 */

export type GuideLang = 'fr' | 'en' | 'ar';

/** Libellés de l'écran du guide, dans la langue du guide. */
export interface GuideLabels {
  /** Sous-titre de la couverture (« Pour l'administrateur de l'école »). */
  audience: string;
  contents: string;
  previous: string;
  next: string;
  /** « Page {{n}} sur {{total}} ». */
  pageOf: string;
  bookMode: string;
  scrollMode: string;
  language: string;
  /** Titres des encadrés : `[!TIP]`, `[!NOTE]`, `[!WARNING]`. */
  example: string;
  note: string;
  warning: string;
  loading: string;
  error: string;
  retry: string;
  /** Invitation de la couverture : « Tournez la page ». */
  turnHint: string;
  /** Aide sous le livre : clavier, glisser. */
  keyboardHint: string;
  /** Nom accessible du livre. */
  bookAria: string;
}

/** Contrat de {@code assets/guides/<langue>/manifest.json}. */
export interface GuideManifest {
  lang: GuideLang;
  dir: 'ltr' | 'rtl';
  /** Nom de la langue, dans cette langue (« Français », « English », « العربية »). */
  languageName: string;
  title: string;
  labels: GuideLabels;
  chapters: { id: string; file: string }[];
}

export type GuidePageKind = 'cover' | 'contents' | 'content';

/** Une page du livre. Couverture et sommaire sont dessinés par l'écran ; une page de contenu porte son HTML. */
export interface GuidePage {
  index: number;
  kind: GuidePageKind;
  chapterId: string | null;
  chapterTitle: string | null;
  html: string;
}

export interface GuideChapter {
  id: string;
  title: string;
  /** Index de la première page du chapitre dans {@link GuideBook.pages}. */
  firstPage: number;
  /** Chapitre entier, pour la lecture continue. */
  html: string;
}

export interface GuideBook {
  lang: GuideLang;
  dir: 'ltr' | 'rtl';
  languageName: string;
  title: string;
  labels: GuideLabels;
  chapters: GuideChapter[];
  pages: GuidePage[];
}
