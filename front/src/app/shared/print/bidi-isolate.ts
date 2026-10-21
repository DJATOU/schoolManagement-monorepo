/**
 * Isolement des passages latins d'un texte arabe imprimé.
 *
 * <p>Dans un paragraphe de droite à gauche, l'algorithme bidirectionnel réordonne les suites de
 * chiffres et de lettres latines séparées par des espaces : « 1.200,00 دج » sortait
 * « 200,00 1.دج », « 4 AM » sortait « AM 4 ». Chaque passage latin (montant, date, niveau, numéro de
 * reçu, nom, téléphone) est donc isolé de gauche à droite : il garde l'ordre dans lequel il est
 * écrit, et prend place comme un seul mot dans la phrase arabe.</p>
 *
 * <p>Un passage commence sur une lettre latine ou un chiffre (ou une parenthèse ouvrante, un signe
 * qui les précède), s'étend sur tout ce qui n'est pas une lettre arabe, et s'arrête sur sa dernière
 * lettre latine, son dernier chiffre, un « % » ou une parenthèse fermante ouverte dans le passage :
 * la ponctuation et les espaces qui le suivent restent dans la phrase arabe.</p>
 *
 * <p>Dans une phrase arabe, un tiret, un point médian, « = » ou « × » entre espaces, ou des
 * deux-points suivis d'une espace, après une valeur faite de chiffres (« رقم 2 — 6/10/2030 »,
 * « 72.000,00 دج × 60 % = 43.200,00 دج ») sépare deux valeurs : chacune est isolée à part et elles se
 * suivent dans l'ordre de la phrase arabe. Après des lettres latines, le séparateur fait partie de la
 * donnée (« Séance 7 — 4 AM (استدراك) ») ; un texte sans lettre arabe (« Maths 4 AM A — Séance 3 »)
 * reste de même un seul passage, dans son ordre.</p>
 */

/** Marques de direction : `Intl` en insère dans les dates arabes ; l'isolement les remplace. */
const BIDI_CONTROLS = /[\u200E\u200F\u061C\u202A-\u202E\u2066-\u2069]/g;

/** Lettres et signes arabes (blocs arabe, supplément, étendu A, formes de présentation A et B). */
const ARABIC = /[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]/;

/** Lettre latine, accents compris (« × » et « ÷ », dans le même bloc, n'en sont pas). */
const LATIN_LETTER = /[A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u024F]/;

/** Lettre latine (accents compris) ou chiffre ; « × » et « ÷ », dans le même bloc, n'en sont pas. */
const LATIN_OR_DIGIT = /[A-Za-z0-9\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u024F]/;

const OPENERS = new Set(['(', '[']);
const CLOSERS = new Map([[')', '('], [']', '[']]);
const SIGNS = new Set(['-', '+', '\u2212']);

/**
 * Séparateurs de valeurs dans une phrase arabe : tiret cadratin, demi-cadratin, point médian, « = »,
 * « × » entre espaces ; deux-points suivis d'une espace (« 20/10/2029: 1.200,00 »). « 14:30 » n'en
 * est pas un.
 */
const SEPARATOR = /^( [\u2014\u2013\u00B7=\u00D7] |: )/;

/** Segment d'un texte : passage latin à isoler, ou texte laissé dans la phrase. */
export interface BidiSegment {
  text: string;
  ltr: boolean;
}

/** Retire les marques de direction (LRM, RLM, ALM, enchâssements, isolements). */
export function stripBidiControls(text: string): string {
  return text.replace(BIDI_CONTROLS, '');
}

/** Découpe un texte en passages latins et en texte ordinaire (marques de direction retirées). */
export function bidiSegments(source: string): BidiSegment[] {
  const text = stripBidiControls(source);
  const arabicSentence = ARABIC.test(text);
  const segments: BidiSegment[] = [];
  let plain = '';
  let i = 0;
  while (i < text.length) {
    if (!startsRun(text, i)) {
      plain += text[i];
      i++;
      continue;
    }
    const end = runEnd(text, i, arabicSentence);
    if (plain) {
      segments.push({ text: plain, ltr: false });
      plain = '';
    }
    segments.push({ text: text.slice(i, end + 1), ltr: true });
    i = end + 1;
  }
  if (plain) {
    segments.push({ text: plain, ltr: false });
  }
  return segments;
}

/** Texte échappé pour HTML, chaque passage latin dans un `<bdi dir="ltr">`. */
export function isolateLtrRuns(text: string): string {
  return bidiSegments(text)
    .map(segment => (segment.ltr ? `<bdi dir="ltr">${escapeHtml(segment.text)}</bdi>` : escapeHtml(segment.text)))
    .join('');
}

/** Échappe un texte pour un contenu ou une valeur d'attribut HTML. */
export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function startsRun(text: string, i: number): boolean {
  const char = text[i];
  if (LATIN_OR_DIGIT.test(char)) {
    return true;
  }
  const next = text[i + 1];
  if (next === undefined) {
    return false;
  }
  if (OPENERS.has(char)) {
    return LATIN_OR_DIGIT.test(next);
  }
  return SIGNS.has(char) && /[0-9]/.test(next);
}

/** Indice du dernier caractère du passage commencé en `start`. */
function runEnd(text: string, start: number, arabicSentence: boolean): number {
  const open: string[] = [];
  let end = start;
  let letters = false;
  for (let j = start; j < text.length; j++) {
    const char = text[j];
    const separator = arabicSentence && !letters && open.length === 0 && SEPARATOR.test(text.slice(j, j + 3));
    if (ARABIC.test(char) || char === '\n' || separator) {
      break;
    }
    letters = letters || LATIN_LETTER.test(char);
    if (OPENERS.has(char)) {
      open.push(char);
    } else if (CLOSERS.has(char)) {
      if (open.length > 0 && open[open.length - 1] === CLOSERS.get(char)) {
        open.pop();
        end = j;
      }
    } else if (LATIN_OR_DIGIT.test(char) || char === '%') {
      end = j;
    }
  }
  return end;
}
