import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { API_BASE_URL } from '../api-base-url';
import { CorrectionReason, CorrectionReasonType, CorrectionResponse } from '../models/correction/correction';
import { correctionErrorOf } from '../models/correction/correction-error';
import {
  PayableSeries,
  Payout,
  PayoutCorrection,
  PayoutError,
  PayoutFilters,
  PayoutList,
  PayoutPreview,
  PayoutSlip
} from '../models/payroll/payroll';
import { CorrectionStep } from './encashment.service';

/**
 * Paie des enseignants : appels HTTP uniquement (spec teacher-payroll, exigences 2 à 7 et 9).
 *
 * <p>Payer et régulariser suivent le protocole Aperçu / confirmation : l'Aperçu calcule les deux
 * parts et les scelle d'un jeton, la confirmation le renvoie. Si un montant a changé entre-temps, le
 * serveur répond 409 avec le nouvel Aperçu, conservé dans une {@link PayoutError}. Annuler et
 * remplacer passent par le moteur des corrections : leurs refus sont des `CorrectionError`, comme
 * pour un versement. Tout est réservé au rôle ADMIN côté serveur.</p>
 *
 * @see TeacherPayoutController.java, PayoutCorrectionController.java (backend)
 */
@Injectable({ providedIn: 'root' })
export class TeacherPayoutService {

  private readonly baseUrl = `${API_BASE_URL}/api`;

  constructor(private http: HttpClient) {}

  // ------------------------------------------------------------------
  // Lecture
  // ------------------------------------------------------------------

  /** Séries à payer, en cours ou à régulariser ; filtres facultatifs. */
  getPayable(teacherId?: number | null, groupId?: number | null): Observable<PayableSeries[]> {
    let params = new HttpParams();
    if (teacherId != null) {
      params = params.set('teacherId', teacherId);
    }
    if (groupId != null) {
      params = params.set('groupId', groupId);
    }
    return this.http.get<PayableSeries[]>(`${this.baseUrl}/teacher-payouts/payable`, { params })
      .pipe(catchError(error => this.handleError(error)));
  }

  /** Paies versées selon les filtres, la plus récente d'abord, avec les totaux des paies actives. */
  searchPayouts(filters: PayoutFilters = {}): Observable<PayoutList> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<PayoutList>(`${this.baseUrl}/teacher-payouts`, { params })
      .pipe(catchError(error => this.handleError(error)));
  }

  getPayout(id: number): Observable<Payout> {
    return this.http.get<Payout>(`${this.baseUrl}/teacher-payouts/${id}`)
      .pipe(catchError(error => this.handleError(error)));
  }

  /** Paies d'un enseignant, pour sa fiche. */
  getTeacherPayouts(teacherId: number): Observable<PayoutList> {
    return this.http.get<PayoutList>(`${this.baseUrl}/teachers/${teacherId}/payouts`)
      .pipe(catchError(error => this.handleError(error)));
  }

  // ------------------------------------------------------------------
  // Payer, régulariser
  // ------------------------------------------------------------------

  /** Aperçu de la paie d'une série terminée, au taux choisi. */
  previewPay(seriesId: number, rateId: number, note?: string | null): Observable<PayoutPreview> {
    return this.http.post<PayoutPreview>(`${this.baseUrl}/teacher-payouts/series/${seriesId}/pay/preview`,
      { rateId, note: note ?? null, previewToken: null }).pipe(catchError(error => this.handleError(error)));
  }

  /** Enregistre la paie lue dans l'Aperçu. */
  confirmPay(seriesId: number, rateId: number, note: string | null, previewToken: string): Observable<Payout> {
    return this.http.post<Payout>(`${this.baseUrl}/teacher-payouts/series/${seriesId}/pay/confirm`,
      { rateId, note, previewToken }).pipe(catchError(error => this.handleError(error)));
  }

  /** Aperçu de la régularisation d'une série payée : complément ou retenue. */
  previewRegularize(seriesId: number): Observable<PayoutPreview> {
    return this.http.post<PayoutPreview>(`${this.baseUrl}/teacher-payouts/series/${seriesId}/regularize/preview`, {})
      .pipe(catchError(error => this.handleError(error)));
  }

  /** Enregistre la régularisation lue dans l'Aperçu. */
  confirmRegularize(seriesId: number, note: string | null, previewToken: string): Observable<Payout> {
    return this.http.post<Payout>(`${this.baseUrl}/teacher-payouts/series/${seriesId}/regularize/confirm`,
      { rateId: null, note, previewToken }).pipe(catchError(error => this.handleError(error)));
  }

  /** Enregistre une impression du bordereau : la deuxième et les suivantes portent « DUPLICATA ». */
  issueSlip(payoutId: number): Observable<PayoutSlip> {
    return this.http.post<PayoutSlip>(`${this.baseUrl}/teacher-payouts/${payoutId}/slips`, {})
      .pipe(catchError(error => this.handleError(error)));
  }

  // ------------------------------------------------------------------
  // Corriger
  // ------------------------------------------------------------------

  /** Motifs proposés pour corriger une paie, dans l'ordre d'affichage. */
  getCorrectionReasons(): Observable<CorrectionReasonType[]> {
    return this.http.get<CorrectionReasonType[]>(`${this.baseUrl}/teacher-payouts/correction-reasons`)
      .pipe(catchError(error => this.correctionError(error)));
  }

  /** Annuler une paie : Aperçu, puis confirmation avec le jeton de l'Aperçu lu. */
  cancel(payoutId: number, step: CorrectionStep, reason: CorrectionReason,
         previewToken?: string): Observable<CorrectionResponse<Payout>> {
    return this.http.post<CorrectionResponse<Payout>>(`${this.baseUrl}/teacher-payouts/${payoutId}/cancel/${step}`, {
      reasonType: reason.type,
      reasonText: reason.text ?? null,
      previewToken: previewToken ?? null
    }).pipe(catchError(error => this.correctionError(error)));
  }

  /** Remplacer une paie initiale par une paie à un autre taux : Aperçu, puis confirmation. */
  replace(payoutId: number, step: CorrectionStep, rateId: number, note: string | null, reason: CorrectionReason,
          previewToken?: string): Observable<CorrectionResponse<PayoutCorrection>> {
    return this.http.post<CorrectionResponse<PayoutCorrection>>(
      `${this.baseUrl}/teacher-payouts/${payoutId}/replace/${step}`, {
        rateId,
        note,
        reasonType: reason.type,
        reasonText: reason.text ?? null,
        previewToken: previewToken ?? null
      }).pipe(catchError(error => this.correctionError(error)));
  }

  // ------------------------------------------------------------------
  // Erreurs
  // ------------------------------------------------------------------

  /**
   * Gestion centralisée des erreurs HTTP : le motif du serveur est conservé quand il en porte un, et
   * le nouvel Aperçu joint à une confirmation périmée.
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue sur la paie des enseignants';

    if (error.error instanceof ErrorEvent) {
      errorMessage = `Erreur : ${error.error.message}`;
    } else {
      switch (error.status) {
        case 400:
        case 409:
          errorMessage = error.error?.message || 'Paie refusée';
          break;
        case 401:
          errorMessage = 'Session expirée : reconnectez-vous';
          break;
        case 403:
          errorMessage = 'Action réservée aux administrateurs';
          break;
        case 404:
          errorMessage = error.error?.message || 'Paie introuvable';
          break;
        case 0:
          errorMessage = 'Serveur injoignable : le résultat de l\'opération est inconnu';
          break;
        case 500:
          errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
          break;
      }
    }

    console.error('Teacher Payout Service Error:', errorMessage, error);
    const body = error.error ?? {};
    return throwError(() => new PayoutError(errorMessage, error.status, body.errorCode ?? null,
      body.preview ?? null, body.previewToken ?? null));
  }

  private correctionError(error: HttpErrorResponse): Observable<never> {
    return throwError(() => correctionErrorOf(error, {
      notFound: 'Paie introuvable',
      fallback: 'Correction de la paie refusée'
    }));
  }
}
