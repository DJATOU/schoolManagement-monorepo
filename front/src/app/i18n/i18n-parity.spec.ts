import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';
import arTranslations from '../../assets/i18n/ar.json';

/**
 * Test de parité des clés de traduction FR / EN / AR.
 *
 * Garantit que chaque clé de traduction définie dans `fr.json` possède une
 * contrepartie dans `en.json` et `ar.json`, et inversement. Toute clé introduite (notamment
 * par la fonctionnalité "année scolaire" : `schoolYear.*`, `parcours.*`,
 * `yearEnd.*`) doit exister dans les trois fichiers.
 *
 * <p>Garde-fou des traductions : il échoue aussi sur une valeur vide, des paramètres
 * `{{…}}` différents d'une langue à l'autre, une valeur arabe restée non traduite (sans lettre
 * arabe, ou avec des lettres accentuées du français) et une feuille qui n'est pas une chaîne.</p>
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

/** Feuilles d'un arbre de traductions, avec leur chemin pointé ; tableaux et scalaires inclus. */
function flattenEntries(tree: TranslationTree, prefix = ''): [string, unknown][] {
  const entries: [string, unknown][] = [];
  for (const key of Object.keys(tree)) {
    const value: unknown = tree[key];
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      entries.push(...flattenEntries(value as TranslationTree, path));
    } else {
      entries.push([path, value]);
    }
  }
  return entries;
}

/** Noms des paramètres `{{nom}}` d'une valeur, triés et dédoublonnés (syntaxe de ngx-translate). */
function placeholders(value: unknown): string[] {
  const names = [...String(value).matchAll(/\{\{\s?([^{}\s]*)\s?\}\}/g)].map(match => match[1]);
  return [...new Set(names)].sort();
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

describe('i18n FR/EN/AR key parity', () => {
  const frKeys = new Set(flattenKeys(frTranslations as unknown as TranslationTree));
  const enKeys = new Set(flattenKeys(enTranslations as unknown as TranslationTree));
  const arKeys = new Set(flattenKeys(arTranslations as unknown as TranslationTree));

  const files: Record<'fr' | 'en' | 'ar', TranslationTree> = {
    fr: frTranslations as unknown as TranslationTree,
    en: enTranslations as unknown as TranslationTree,
    ar: arTranslations as unknown as TranslationTree
  };
  const languages = Object.keys(files) as ('fr' | 'en' | 'ar')[];
  const leaves = Object.fromEntries(
    languages.map(lang => [lang, new Map(flattenEntries(files[lang]))])
  ) as Record<'fr' | 'en' | 'ar', Map<string, unknown>>;

  /**
   * Valeurs arabes admises sans lettre arabe : compositions de paramètres, d'unités (« % ») et de
   * ponctuation, qui n'ont rien à traduire. À garder aussi courte que possible : toute autre valeur
   * sans lettre arabe est une valeur laissée non traduite. Le dinar s'écrit « دج » en arabe : les
   * montants « {{amount}} دج » portent donc une lettre arabe et n'ont pas leur place ici.
   */
  const AR_WITHOUT_ARABIC_LETTER_ALLOWLIST = new Set([
    'teacherPayroll.slip.rateValue', // « {{label}} ({{percent}} %) »
    'teacherPayroll.dialog.rateOption', // « {{label}} ({{percent}} %) »
    'SESSION_MODAL.CORRECTION.SUBJECT', // « {{session}} — {{student}}: {{change}} »
    'enrolment.correction.subject' // « {{student}} — «{{group}}»، {{period}} » : virgule arabe seule
  ]);

  it('should have identical flattened key sets in fr.json, en.json and ar.json', () => {
    const all = new Set(languages.flatMap(lang => [...leaves[lang].keys()]));
    const report = languages
      .map(lang => {
        const missing = [...all].filter(key => !leaves[lang].has(key)).sort();
        const extra = [...leaves[lang].keys()]
          .filter(key => languages.some(other => other !== lang && !leaves[other].has(key))).sort();
        return { lang, missing, extra };
      })
      .filter(({ missing, extra }) => missing.length > 0 || extra.length > 0)
      .map(({ lang, missing, extra }) =>
        `${lang}.json — missing: ${JSON.stringify(missing)}, present here but absent elsewhere: ${JSON.stringify(extra)}`);
    expect(report).withContext(report.join('\n')).toEqual([]);
  });

  it('should only have string leaves in the three files', () => {
    const offenders = languages.flatMap(lang => [...leaves[lang]]
      .filter(([, value]) => typeof value !== 'string')
      .map(([key, value]) => `${lang}.json ${key} = ${JSON.stringify(value)}`));
    expect(offenders).withContext(`Non-string translation leaves: ${offenders.join(' ; ')}`).toEqual([]);
  });

  it('should have no empty or whitespace-only value in the three files', () => {
    const offenders = languages.flatMap(lang => [...leaves[lang]]
      .filter(([, value]) => typeof value === 'string' && value.trim() === '')
      .map(([key]) => `${lang}.json ${key}`));
    expect(offenders).withContext(`Empty translation values: ${offenders.join(' ; ')}`).toEqual([]);
  });

  it('should use the same {{placeholder}} names for a key in fr, en and ar', () => {
    const offenders = [...leaves.fr.keys()]
      .filter(key => leaves.en.has(key) && leaves.ar.has(key))
      .filter(key => {
        const expected = placeholders(leaves.fr.get(key)).join(',');
        return placeholders(leaves.en.get(key)).join(',') !== expected
          || placeholders(leaves.ar.get(key)).join(',') !== expected;
      })
      .map(key => `${key}: fr [${placeholders(leaves.fr.get(key))}] en [${placeholders(leaves.en.get(key))}] `
        + `ar [${placeholders(leaves.ar.get(key))}]`);
    expect(offenders).withContext(`Placeholder mismatch: ${offenders.join(' ; ')}`).toEqual([]);
  });

  it('should translate every Arabic value: at least one Arabic letter, outside the allowlist', () => {
    const untranslated = [...leaves.ar]
      .filter(([key]) => !AR_WITHOUT_ARABIC_LETTER_ALLOWLIST.has(key))
      .filter(([, value]) => !/[\u0621-\u064A]/.test(String(value).replace(/\{\{[^{}]*\}\}/g, '')))
      .map(([key, value]) => `${key} = ${JSON.stringify(value)}`);
    expect(untranslated).withContext(`Arabic values without any Arabic letter: ${untranslated.join(' ; ')}`)
      .toEqual([]);
  });

  it('should keep the Arabic allowlist free of stale or translatable entries', () => {
    const stale = [...AR_WITHOUT_ARABIC_LETTER_ALLOWLIST].filter(key => !leaves.ar.has(key)
      || /[\u0621-\u064A]/.test(String(leaves.ar.get(key)).replace(/\{\{[^{}]*\}\}/g, '')));
    expect(stale).withContext(`Allowlist entries to remove: ${JSON.stringify(stale)}`).toEqual([]);
  });

  it('should leave no French accented letter in an Arabic value', () => {
    const offenders = [...leaves.ar]
      .filter(([, value]) => /[àâçéèêëîïôûùüÿœ]/i.test(String(value)))
      .map(([key, value]) => `${key} = ${JSON.stringify(value)}`);
    expect(offenders).withContext(`Arabic values containing French text: ${offenders.join(' ; ')}`).toEqual([]);
  });

  it('should have exactly the French key set in the Arabic translation file', () => {
    const missingInAr = [...frKeys].filter((key) => !arKeys.has(key)).sort();
    const extraInAr = [...arKeys].filter((key) => !frKeys.has(key)).sort();
    expect(missingInAr)
      .withContext(`Keys present in fr.json but missing in ar.json: ${JSON.stringify(missingInAr)}`)
      .toEqual([]);
    expect(extraInAr)
      .withContext(`Keys present in ar.json but missing in fr.json: ${JSON.stringify(extraInAr)}`)
      .toEqual([]);
  });

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

  /**
   * Clés de la Paie des enseignants (spec teacher-payroll, S.7), y compris celles composées à
   * l'exécution : état d'une série, statut et nature d'une paie, sujet d'une correction, titre du
   * bordereau. Une clé oubliée dans les deux fichiers s'afficherait brute.
   */
  const teacherPayrollKeys = [
    'SIDE_MENU.TEACHER_PAYROLL',
    ...['title', 'subtitle', 'tabs.payable', 'tabs.paid', 'tabs.rates'].map(key => `teacherPayroll.${key}`),
    ...['PAYABLE', 'NOT_FINISHED', 'NO_TEACHER', 'NOTHING_COLLECTED', 'TO_REGULARIZE'].map(key => `teacherPayroll.state.${key}`),
    ...['ACTIVE', 'CANCELLED'].map(key => `teacherPayroll.status.${key}`),
    ...['INITIAL', 'COMPLEMENT', 'DEDUCTION'].flatMap(key => [
      `teacherPayroll.nature.${key}`, `teacherPayroll.correction.subject.${key}`, `teacherPayroll.slip.title.${key}`]),
    ...['teacher', 'group', 'all', 'allGroups', 'status', 'allStatuses', 'from', 'to', 'reset']
      .map(key => `teacherPayroll.filters.${key}`),
    ...['group', 'series', 'teacher', 'sessions', 'collectedNet', 'state', 'number', 'paidAt', 'groupSeries', 'nature',
      'rate', 'base', 'teacherAmount', 'schoolAmount', 'status'].map(key => `teacherPayroll.columns.${key}`),
    ...['hint', 'empty', 'noTeacher', 'grossMinusRefunded', 'remaining', 'noTeacherHint', 'nothingHint', 'deductionHint',
      'complementHint', 'pay', 'payAria', 'regularize', 'regularizeAria', 'recorded'].map(key => `teacherPayroll.payable.${key}`),
    ...['empty', 'ofInitial', 'replaces', 'by', 'netNow', 'cancelledOn', 'replacedBy', 'teacherTotal', 'schoolTotal',
      'totalsHint'].map(key => `teacherPayroll.paid.${key}`),
    ...['payTitle', 'regularizeTitle', 'subject', 'sessions', 'initialPayout', 'rate', 'rateOption', 'noRate', 'rateRequired',
      'note', 'stale', 'previewTitle', 'composition', 'calculation', 'regularizationBase', 'regularizationShare', 'complement',
      'deduction', 'school', 'frozen', 'preview', 'confirm', 'confirmDeduction', 'error'].map(key => `teacherPayroll.dialog.${key}`),
    ...['cancelTitle', 'replaceTitle', 'replaceSubject', 'replaceHint', 'newRate', 'noOtherRate', 'noteKept', 'next',
      'cancelled', 'replaced', 'replaceAction', 'replaceAria', 'cancelAction', 'cancelAria']
      .map(key => `teacherPayroll.correction.${key}`),
    ...['createTitle', 'editTitle', 'label', 'labelRequired', 'teacherPercent', 'schoolPercent', 'schoolShare',
      'percentInvalid', 'save', 'create', 'frozenHint', 'empty', 'active', 'inactive', 'edit', 'editAria', 'disable',
      'disableAria', 'created', 'updated', 'disabled'].map(key => `teacherPayroll.rates.${key}`),
    ...['print', 'printError', 'reprint', 'reprintAria', 'cancelledStamp', 'number', 'paidOn', 'duplicate', 'cancelledOn',
      'replacedBy', 'partiesHeading', 'calculationHeading', 'recordedBy', 'footer', 'teacher', 'group', 'series', 'rate',
      'rateValue', 'initialPayout', 'replaces', 'collectedGross', 'refunded', 'collectedNet', 'calculation',
      'calculationValue', 'baseDelta', 'deductionAmount', 'teacherAmount', 'schoolAmount', 'signatureTeacher',
      'signatureSchool', 'money'].map(key => `teacherPayroll.slip.${key}`),
    ...['title', 'empty', 'total'].map(key => `teacherPayroll.profile.${key}`),
    // Séances prévues, série incomplète, séries payées sur demande (retours de tests).
    'teacherPayroll.state.PAID', 'teacherPayroll.dialog.incomplete',
    ...['includePaid', 'planned', 'missing', 'paidHint'].map(key => `teacherPayroll.payable.${key}`)
  ];

  it('should say from when a session that has not started can be validated, in both files', () => {
    for (const translations of [frTranslations, enTranslations]) {
      const hint = readKey(translations, 'SESSION_MODAL.NOT_STARTED_HINT');
      expect(hint).toContain('{{day}}');
      expect(hint).toContain('{{time}}');
    }
  });

  it('should define every teacher payroll key in both files', () => {
    const missing = teacherPayrollKeys.filter((key) => !frKeys.has(key) || !enKeys.has(key));
    expect(missing)
      .withContext(`Teacher payroll keys missing from fr.json or en.json: ${JSON.stringify(missing)}`)
      .toEqual([]);
  });

  it('should define the dashboard active-groups card and its total in both files', () => {
    // La carte Groupes affiche les groupes ayant des élèves ; la précision interpole le total créé.
    expect(readKey(frTranslations, 'dashboard.kpi.activeGroups')).toBe('Groupes en activité');
    expect(readKey(enTranslations, 'dashboard.kpi.activeGroups')).not.toBe('');
    expect(readKey(frTranslations, 'dashboard.kpi.groupsTotal')).toContain('{{count}}');
    expect(readKey(enTranslations, 'dashboard.kpi.groupsTotal')).toContain('{{count}}');
  });

  it('should print DUPLICATA with its rank and mark a cancelled slip', () => {
    // Exigence 5.3 et 5.4 : la réimpression porte son rang, la paie annulée son tampon.
    expect(readKey(frTranslations, 'teacherPayroll.slip.duplicate')).toContain('DUPLICATA');
    expect(readKey(frTranslations, 'teacherPayroll.slip.duplicate')).toContain('{{rank}}');
    expect(readKey(enTranslations, 'teacherPayroll.slip.duplicate')).toContain('{{rank}}');
    expect(readKey(frTranslations, 'teacherPayroll.slip.cancelledStamp')).toBe('ANNULÉE');
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
