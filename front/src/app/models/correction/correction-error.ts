import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

import { BlockingRefund, CorrectionPreview } from './correction';

/**
 * Refus d'une correction, avec ce que le serveur joint au motif (spec admin-corrections, B.5).
 *
 * <p>Le motif seul ne suffit pas toujours : un Aperçu périmé (409 `STALE_PREVIEW`) porte le nouvel
 * Aperçu et son jeton, à présenter à la place de l'ancien ; un versé qui passerait sous le remboursé
 * (409 `REFUND_FLOOR`) porte les remboursements en cause. Le service HTTP conserve donc ces données
 * au lieu de réduire l'erreur à un message.</p>
 */
export class CorrectionError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly errorCode: string | null = null,
    readonly preview: CorrectionPreview | null = null,
    readonly previewToken: string | null = null,
    readonly blockingRefunds: BlockingRefund[] = []
  ) {
    super(message);
    this.name = 'CorrectionError';
  }

  /** Les données ont changé depuis l'Aperçu : un nouvel Aperçu est joint. */
  get stale(): boolean {
    return this.errorCode === 'STALE_PREVIEW' && this.preview !== null && this.previewToken !== null;
  }
}

/**
 * Ce qu'un refus dit quand le serveur n'a rien rédigé : propre à ce qui est corrigé. Ce sont des
 * clés de traduction, résolues dans la langue de l'interface.
 */
export interface CorrectionErrorMessages {
  /** 404 sans motif : clé de « Séance introuvable ». */
  notFound: string;
  /** Statut sans message dédié. */
  fallback: string;
}

/**
 * Refus HTTP d'une correction, tel que l'écran l'affiche : le motif du serveur quand il en porte un
 * (400, 404, 409), sinon un message par statut ; l'Aperçu, son jeton et les remboursements joints
 * sont conservés. Le motif du serveur est transmis tel quel ; les messages propres au navigateur
 * sont traduits.
 */
export function correctionErrorOf(error: HttpErrorResponse, messages: CorrectionErrorMessages,
                                  translate: TranslateService): CorrectionError {
  const body = error.error ?? {};
  const serverMessage: string | null = typeof body.message === 'string' && body.message.trim().length > 0
    ? body.message : null;
  let message: string;
  if (error.error instanceof ErrorEvent) {
    message = translate.instant('HTTP_ERRORS.WITH_MESSAGE', { message: error.error.message });
  } else {
    switch (error.status) {
      case 400:
      case 409:
        message = serverMessage ?? translate.instant('HTTP_ERRORS.CORRECTION_REFUSED');
        break;
      case 401:
        message = translate.instant('HTTP_ERRORS.SESSION_EXPIRED');
        break;
      case 403:
        message = translate.instant('HTTP_ERRORS.ADMIN_ONLY');
        break;
      case 404:
        message = serverMessage ?? translate.instant(messages.notFound);
        break;
      case 0:
        message = translate.instant('HTTP_ERRORS.UNREACHABLE');
        break;
      case 500:
        message = translate.instant('HTTP_ERRORS.SERVER');
        break;
      default:
        message = translate.instant(messages.fallback);
    }
  }
  return new CorrectionError(message, error.status, body.errorCode ?? null, body.preview ?? null,
    body.previewToken ?? null, body.blockingRefunds ?? []);
}
