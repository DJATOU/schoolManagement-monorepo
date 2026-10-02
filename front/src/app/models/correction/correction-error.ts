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
