import { TranslateService } from '@ngx-translate/core';
import { StudentPaymentStatus } from '../models/student-payment-status';
import { resolveLocale } from '../shared/locale';

/**
 * Compose l'infobulle détaillant les retards de paiement d'un étudiant.
 *
 * <p>Ce texte était construit à l'identique dans deux composants (la carte de profil et la
 * ligne de liste), avec des libellés codés en dur en français. Il est centralisé ici pour que
 * les deux affichages restent alignés et traduits.</p>
 *
 * <p>Lisibilité : le format précédent concaténait tout sur une seule ligne et donnait
 * « Reste 6000.00 DA (12000.00/18000.00 DA) », où les deux montants entre parenthèses
 * n'étaient identifiés par rien. On nomme désormais chaque montant, on met le nom du groupe
 * sur sa propre ligne, et on formate les nombres selon la locale (séparateur de milliers,
 * décimales inutiles retirées).</p>
 *
 * @param status    statut de paiement de l'étudiant
 * @param translate service de traduction, pour les libellés et le pluriel
 * @returns le texte de l'infobulle, ou une chaîne vide si l'étudiant n'est pas en retard
 */
export function buildLatePaymentTooltip(
  status: StudentPaymentStatus | null | undefined,
  translate: TranslateService
): string {
  if (!status || status.paymentStatus !== 'LATE' || !status.lateGroups?.length) {
    return '';
  }

  const locale = resolveLocale(translate.currentLang);
  const money = (value: number) => formatMoney(value, locale);
  const t = (key: string, params?: Record<string, unknown>) => translate.instant(key, params);

  const blocks = status.lateGroups.map(group => {
    const remaining = group.dueAmount - group.paidAmount;
    const sessions = group.unpaidSessionsCount;

    // Pluriel porté par deux clés distinctes : « 1 séance impayée » / « 2 séances impayées ».
    const sessionsLabel = t(
      sessions === 1
        ? 'PAYMENT_STATUS.tooltip.unpaidSession'
        : 'PAYMENT_STATUS.tooltip.unpaidSessions',
      { count: sessions }
    );

    return [
      group.groupName,
      `  ${sessionsLabel}`,
      `  ${t('PAYMENT_STATUS.tooltip.remaining', { amount: money(remaining) })}`,
      // Le montant versé est laissé sans devise : elle n'est portée que par le montant dû,
      // sinon la ligne répétait « DA » deux fois.
      `  ${t('PAYMENT_STATUS.tooltip.paidOfDue', {
        paid: formatAmount(group.paidAmount, locale),
        due: money(group.dueAmount)
      })}`
    ].join('\n');
  });

  // Une ligne vide entre les groupes : sans elle, les blocs de plusieurs lignes se
  // confondaient et on ne voyait plus où commençait le groupe suivant.
  return [t('PAYMENT_STATUS.tooltip.title'), ...blocks].join('\n\n');
}

/**
 * Formate un montant avec le suffixe monétaire.
 *
 * <p>Les décimales ne sont affichées que si elles portent une information : « 6 000 DA »
 * plutôt que « 6000.00 DA », mais « 6 000,5 DA » reste intact.</p>
 */
function formatMoney(value: number, locale: string): string {
  return `${formatAmount(value, locale)} DA`;
}

/** Formate un nombre selon la locale, sans suffixe monétaire. */
function formatAmount(value: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  }).format(value);
}
