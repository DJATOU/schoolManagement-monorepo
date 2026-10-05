import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';

/**
 * Test de parité des clés de traduction FR / EN.
 *
 * Garantit que chaque clé de traduction définie dans `fr.json` possède une
 * contrepartie dans `en.json` et inversement. Toute clé introduite (notamment
 * par la fonctionnalité "année scolaire" : `schoolYear.*`, `parcours.*`,
 * `yearEnd.*`) doit exister dans les deux fichiers.
 *
 * Requirements: 15.1, 15.4
 */

type TranslationTree = { [key: string]: string | TranslationTree };

/**
 * Aplati un objet de traductions imbriqué en un ensemble de chemins pointés
 * (ex: `schoolYear.selector.label`).
 */
function flattenKeys(tree: TranslationTree, prefix = ''): string[] {
  const keys: string[] = [];
  for (const key of Object.keys(tree)) {
    const value = tree[key];
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...flattenKeys(value as TranslationTree, path));
    } else {
      keys.push(path);
    }
  }
  return keys;
}

/**
 * Lit la valeur d'un chemin pointé, ou une chaîne vide si le chemin n'existe pas.
 *
 * <p>Renvoyer une chaîne vide plutôt que de lever permet aux assertions de libellé de rapporter
 * le contenu attendu manquant, plutôt qu'une erreur d'accès sans rapport.</p>
 */
function readKey(tree: unknown, path: string): string {
  const value = path.split('.').reduce<unknown>(
    (node, segment) => (node !== null && typeof node === 'object'
      ? (node as Record<string, unknown>)[segment]
      : undefined),
    tree
  );
  return typeof value === 'string' ? value : '';
}

describe('i18n FR/EN key parity', () => {
  const frKeys = new Set(flattenKeys(frTranslations as unknown as TranslationTree));
  const enKeys = new Set(flattenKeys(enTranslations as unknown as TranslationTree));

  it('should define every French key in the English translation file', () => {
    const missingInEn = [...frKeys].filter((key) => !enKeys.has(key)).sort();
    expect(missingInEn)
      .withContext(`Keys present in fr.json but missing in en.json: ${JSON.stringify(missingInEn)}`)
      .toEqual([]);
  });

  it('should define every English key in the French translation file', () => {
    const missingInFr = [...enKeys].filter((key) => !frKeys.has(key)).sort();
    expect(missingInFr)
      .withContext(`Keys present in en.json but missing in fr.json: ${JSON.stringify(missingInFr)}`)
      .toEqual([]);
  });

  it('should have identical key sets in both translation files', () => {
    expect(frKeys.size).toBe(enKeys.size);
  });

  /**
   * Clés introduites par la facturation au prorata et le report du surplus.
   *
   * <p>Elles sont énumérées explicitement plutôt que déduites d'un préfixe : une clé absente
   * s'affiche à l'écran sous sa forme brute (« payment.dialog.hints.exclusionReason »), défaut
   * déjà rencontré dans ce dépôt. Un contrôle par préfixe ne détecterait pas l'oubli d'une clé
   * dans les <em>deux</em> fichiers à la fois.</p>
   *
   * Requirements: 9.2, 7.2
   */
  const prorataKeys = [
    // Motif d'exclusion et récapitulatif du prorata (exigences 9.1, 9.2)
    'payment.dialog.hints.excludedSessions',
    'payment.dialog.hints.exclusionReason',
    'payment.dialog.hints.existingExcess',
    // Aperçu de la répartition avant validation (exigence 9.3)
    'payment.dialog.allocation.title',
    'payment.dialog.allocation.received',
    'payment.dialog.allocation.allocated',
    'payment.dialog.allocation.carriedOver',
    'payment.dialog.allocation.carriedOverTotal',
    // Refus d'un montant non plaçable, avec l'action corrective (exigence 5.12)
    'payment.dialog.errors.exceedsChain',
    'payment.dialog.errors.exceedsChainUnopened',
    'payment.dialog.messages.successWithCarryOver',
    // Mentions du report sur le reçu (exigences 7.1, 7.2, 7.4)
    'payment.receipt.seriesTotal',
    'payment.receipt.billableSessions',
    'payment.receipt.allocationHeading',
    'payment.receipt.allocated',
    'payment.receipt.carriedOverTo',
    'payment.receipt.carriedOverTotal'
  ];

  it('should define the prorata and carry-over keys in both files', () => {
    const missing = prorataKeys.filter((key) => !frKeys.has(key) || !enKeys.has(key));
    expect(missing)
      .withContext(`Prorata / carry-over keys missing from fr.json or en.json: ${JSON.stringify(missing)}`)
      .toEqual([]);
  });

  it('should name the exclusion reason as a session held before enrolment', () => {
    // Exigence 9.2 : le motif affiché est « Séance antérieure à l'inscription ». Un libellé
    // vague (« séance non facturée ») laisserait le coût au prorata inexpliqué.
    const fr = readKey(frTranslations, 'payment.dialog.hints.exclusionReason');
    const en = readKey(enTranslations, 'payment.dialog.hints.exclusionReason');
    expect(fr).toContain('antérieure à l\'inscription');
    expect(en.toLowerCase()).toContain('before enrolment');
  });

  it('should name the destination series in every carry-over label', () => {
    // Exigence 7.2 : le reçu nomme explicitement la ou les séries destinataires. Un libellé
    // sans le paramètre {{series}} imprimerait « Reporté : 240 DA » sans dire vers où.
    const seriesAwareKeys = [
      'payment.dialog.allocation.allocated',
      'payment.dialog.allocation.carriedOver',
      'payment.receipt.allocated',
      'payment.receipt.carriedOverTo'
    ];
    for (const key of seriesAwareKeys) {
      expect(readKey(frTranslations, key))
        .withContext(`fr.json ${key} must interpolate the series name`)
        .toContain('{{series}}');
      expect(readKey(enTranslations, key))
        .withContext(`en.json ${key} must interpolate the series name`)
        .toContain('{{series}}');
    }
  });

  /**
   * Clés des écrans de correction (spec admin-corrections, D.3 à D.7) et du dialogue de
   * remboursement : relevées dans le code, y compris celles composées à l'exécution (catégories,
   * colonnes, états). Une clé oubliée dans les deux fichiers échapperait au contrôle de parité et
   * s'afficherait brute ; trois l'étaient dans le dialogue de remboursement.
   */
  const correctionScreenKeys = [
    'SESSION_MODAL.SESSION_LABEL', 'SESSION_MODAL.UNVALIDATE', 'SESSION_MODAL.UNVALIDATE_ERROR',
    'SESSION_MODAL.UNVALIDATE_SUBJECT', 'SESSION_MODAL.UNVALIDATE_SUCCESS', 'SESSION_MODAL.UNVALIDATE_TITLE',
    ...['ADD_STUDENT', 'CATCH_UP_REMOVED', 'DONE', 'JUSTIFICATION', 'LINE_ADDED', 'LINE_REMOVED', 'MENU', 'MENU_ARIA',
      'NOTE_ABSENT', 'NOTE_JUSTIFIED', 'NOTE_PRESENT', 'NO_LINE', 'NO_LINE_HINT', 'REASONS_ERROR', 'REMOVE',
      'REMOVE_CATCH_UP', 'STATE_HINT', 'STATE_LABEL', 'STATE_NEXT', 'STATE_TITLE', 'SUBJECT', 'TITLE_ADD',
      'TITLE_CATCH_UP', 'TITLE_CHANGE', 'TITLE_REMOVE', 'ADD.PRESENT', 'ADD.ABSENT', 'ADD.JUSTIFIED', 'STATE.PRESENT',
      'STATE.ABSENT', 'STATE.JUSTIFIED', 'STATE_INLINE.PRESENT', 'STATE_INLINE.ABSENT', 'STATE_INLINE.JUSTIFIED']
      .map(key => `SESSION_MODAL.CORRECTION.${key}`),
    ...['apply', 'by', 'count', 'empty', 'fileName', 'from', 'generatedOn', 'loadError', 'noEffect', 'page',
      'period.all', 'period.between', 'period.from', 'period.to', 'periodInvalid', 'print', 'printAria', 'printError',
      'reset', 'title', 'to', 'columns.date', 'columns.correction', 'columns.effect', 'columns.reason', 'columns.by',
      'category.ENCASHMENT', 'category.ENROLMENT', 'category.ATTENDANCE', 'category.JUSTIFICATION', 'category.CATCH_UP']
      .map(key => `journal.${key}`),
    ...['DATA_ENTRY_ERROR', 'DOCUMENT_RECEIVED', 'ARRIVAL_DATE_CORRECTED', 'STUDENT_LEFT', 'WRONG_STUDENT', 'WRONG_AMOUNT',
      'OTHER'].map(key => `correction.reason.${key}`),
    ...['alreadyRefunded', 'amountLabel', 'amountPaid', 'amountPlaceholder', 'amountRequired', 'amountTooSmall', 'back',
      'confirmAmount', 'confirmButton', 'confirmReason', 'confirmTitle', 'exceedsCap', 'loading', 'notReversible',
      'nothingRefundable', 'reasonLabel', 'reasonNote', 'reasonPlaceholder', 'reasonRequired', 'refundableCap',
      'savedAmount', 'submitButton', 'title', 'receiptFailed'].map(key => `refund.dialog.${key}`),
    // Historique : statut « Non payé » (composé à l'exécution), versé net, remboursements listés.
    'studentHistory.seriesStatus.UNPAID', 'studentHistory.badge.UNPAID', 'studentHistory.amounts.paidNetOfRefunds',
    'studentHistory.pdf.amountPaidNetOfTotal', 'studentHistory.pdf.refundLine',
    ...['noReason', 'reprint', 'reprintAria', 'reprintError', 'unavailable'].map(key => `studentHistory.refunds.${key}`),
    // Gestion des paiements : montant net, statut « Remboursé » (composé à l'exécution), historique.
    'payment.admin.table.refundedShare', 'payment.admin.table.refunded', 'payment.admin.table.netAmount',
    'payment.admin.paymentStatus.REFUNDED', 'payment.admin.paymentStatus.refundedHint',
    'payment.admin.audit.changesTitle',
    ...['title', 'empty', 'error', 'noReason', 'reprint', 'reprintAria', 'reprintError']
      .map(key => `payment.admin.audit.refunds.${key}`)
  ];

  it('should define every key used by the correction screens and the refund dialog in both files', () => {
    const missing = correctionScreenKeys.filter((key) => !frKeys.has(key) || !enKeys.has(key));
    expect(missing)
      .withContext(`Correction screen keys missing from fr.json or en.json: ${JSON.stringify(missing)}`)
      .toEqual([]);
  });

  it('should include the school-year feature keys in both files', () => {
    const featurePrefixes = ['schoolYear.', 'parcours.', 'yearEnd.'];
    const frFeatureKeys = [...frKeys].filter((key) =>
      featurePrefixes.some((prefix) => key.startsWith(prefix)),
    );

    // La fonctionnalité doit avoir introduit des clés dédiées.
    expect(frFeatureKeys.length).toBeGreaterThan(0);

    const featureKeysMissingInEn = frFeatureKeys.filter((key) => !enKeys.has(key)).sort();
    expect(featureKeysMissingInEn)
      .withContext(
        `School-year keys present in fr.json but missing in en.json: ${JSON.stringify(
          featureKeysMissingInEn,
        )}`,
      )
      .toEqual([]);
  });
});
