import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Encashment } from '../models/payment/encashment';
import {
  CorrectionReason,
  CorrectionReasonType,
  CorrectionResponse,
  EncashmentChanges,
  EncashmentCorrection
} from '../models/correction/correction';
import { CorrectionError } from '../models/correction/correction-error';

/** Mode d'une correction : l'Aperçu, puis la confirmation de cet Aperçu. */
export type CorrectionStep = 'preview' | 'confirm';

/**
 * Service des Encaissements (versements reçus) : appels HTTP uniquement, un service par entité.
 *
 * <p>Lecture : un reçu, l'historique des versements d'un élève. Corrections : annuler et corriger,
 * chacune en Aperçu puis en confirmation (spec admin-corrections, B.5). Tout est réservé au rôle
 * ADMIN côté serveur, comme toute donnée financière.</p>
 *
 * <p>Les erreurs sont centralisées comme ailleurs, mais une correction refusée garde ce que le
 * serveur y joint — nouvel Aperçu, remboursements en cause — dans une {@link CorrectionError}.</p>
 *
 * @see EncashmentController.java, EncashmentCorrectionController.java (backend)
 */
@Injectable({ providedIn: 'root' })
export class EncashmentService {

  private readonly baseUrl = `${API_BASE_URL}/api`;

  constructor(private http: HttpClient) {}

  /** Un Encaissement et sa répartition, pour réimprimer son reçu. `GET /api/encashments/{id}`. */
  getEncashment(id: number): Observable<Encashment> {
    return this.http.get<Encashment>(`${this.baseUrl}/encashments/${id}`).pipe(
      catchError(error => this.handleError(error))
    );
  }

  /**
   * Encaissements d'un élève, le plus récent d'abord, annulés compris.
   * `GET /api/students/{studentId}/encashments`.
   */
  getStudentEncashments(studentId: number): Observable<Encashment[]> {
    return this.http.get<Encashment[]>(`${this.baseUrl}/students/${studentId}/encashments`).pipe(
      catchError(error => this.handleError(error))
    );
  }

  /** Motifs proposés pour annuler ou corriger un versement, dans l'ordre d'affichage. */
  getCorrectionReasons(): Observable<CorrectionReasonType[]> {
    return this.http.get<CorrectionReasonType[]>(`${this.baseUrl}/encashments/correction-reasons`).pipe(
      catchError(error => this.handleError(error))
    );
  }

  /** Annuler un versement : Aperçu, puis confirmation avec le jeton de l'Aperçu lu. */
  cancel(id: number, step: CorrectionStep, reason: CorrectionReason,
         previewToken?: string): Observable<CorrectionResponse<Encashment>> {
    return this.http.post<CorrectionResponse<Encashment>>(`${this.baseUrl}/encashments/${id}/cancel/${step}`, {
      reasonType: reason.type,
      reasonText: reason.text ?? null,
      previewToken: previewToken ?? null
    }).pipe(catchError(error => this.handleError(error)));
  }

  /** Corriger un versement vers l'état décrit : Aperçu, puis confirmation. */
  correct(id: number, step: CorrectionStep, changes: EncashmentChanges, reason: CorrectionReason,
          previewToken?: string): Observable<CorrectionResponse<EncashmentCorrection>> {
    return this.http.post<CorrectionResponse<EncashmentCorrection>>(
      `${this.baseUrl}/encashments/${id}/correct/${step}`, {
        ...changes,
        reasonType: reason.type,
        reasonText: reason.text ?? null,
        previewToken: previewToken ?? null
      }).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Gestion centralisée des erreurs HTTP : le motif du serveur est conservé quand il en porte un,
   * avec l'Aperçu et les remboursements joints à un refus de correction.
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue lors de la lecture des versements';

    if (error.error instanceof ErrorEvent) {
      errorMessage = `Erreur : ${error.error.message}`;
    } else {
      switch (error.status) {
        case 400:
        case 409:
          errorMessage = error.error?.message || 'Correction refusée';
          break;
        case 401:
          errorMessage = 'Session expirée : reconnectez-vous';
          break;
        case 403:
          errorMessage = 'Action réservée aux administrateurs';
          break;
        case 404:
          errorMessage = error.error?.message || 'Versement introuvable';
          break;
        case 0:
          errorMessage = 'Serveur injoignable';
          break;
        case 500:
          errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
          break;
      }
    }

    console.error('Encashment Service Error:', errorMessage, error);
    const body = error.error ?? {};
    return throwError(() => new CorrectionError(errorMessage, error.status, body.errorCode ?? null,
      body.preview ?? null, body.previewToken ?? null, body.blockingRefunds ?? []));
  }
}
