import { TranslateService } from '@ngx-translate/core';
import { ApiError } from '../models/response';

/**
 * Le refus d'une inscription, tel que l'écran doit le dire (spec admin-corrections, C.8).
 *
 * Le serveur nomme ce qui bloque — l'année scolaire et ses bornes, le départ que l'arrivée
 * recouvrirait, le niveau du groupe — : son message est rendu tel quel. Un message générique
 * laissait l'administratrice deviner. Les inscriptions en double sont nommées une à une.
 */
export function enrolmentRefusalMessage(error: ApiError | null | undefined, translate: TranslateService): string {
  const body = error?.error ?? null;
  const already = body?.alreadyAssociatedEntities ?? [];
  if (error?.status === 409 && already.length > 0) {
    return translate.instant('enrolment.add.alreadyEnrolled', { names: already.join(', ') });
  }
  const message = typeof body?.message === 'string' && body.message.trim().length > 0 ? body.message
    : typeof body?.error === 'string' && body.error.trim().length > 0 ? body.error
      : null;
  if (message) {
    return message;
  }
  return translate.instant(error?.status === 0 ? 'enrolment.add.unreachable' : 'enrolment.add.error');
}
