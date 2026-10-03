import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';

import { API_BASE_URL } from '../api-base-url';
import { CorrectionJournal } from '../models/correction/journal';

/**
 * Journal des corrections d'un élève : appels HTTP uniquement (spec admin-corrections, D.4 et D.5 ;
 * exigence 12).
 *
 * <p>Réservé au rôle ADMIN côté serveur : le Journal nomme les reçus et leurs montants.</p>
 *
 * @see CorrectionJournalController.java (backend)
 */
@Injectable({ providedIn: 'root' })
export class CorrectionJournalService {

  private readonly baseUrl = `${API_BASE_URL}/api/students`;

  constructor(private http: HttpClient) {}

  /**
   * Journal d'un élève, bornes incluses, chacune facultative (`yyyy-MM-dd`).
   * `GET /api/students/{id}/journal?from&to`.
   */
  getJournal(studentId: number, from?: string | null, to?: string | null): Observable<CorrectionJournal> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<CorrectionJournal>(`${this.baseUrl}/${studentId}/journal`, { params }).pipe(
      catchError((error: HttpErrorResponse) => this.handleError(error))
    );
  }

  /** Le motif du serveur est gardé : une période à l'envers est dite telle quelle. */
  private handleError(error: HttpErrorResponse): Observable<never> {
    const serverMessage = typeof error.error?.message === 'string' && error.error.message.trim().length > 0
      ? error.error.message as string : null;
    let message: string;
    switch (error.status) {
      case 400:
      case 404:
        message = serverMessage ?? 'Le journal n\'a pas pu être chargé.';
        break;
      case 401:
        message = 'Session expirée : reconnectez-vous';
        break;
      case 403:
        message = 'Action réservée aux administrateurs';
        break;
      case 0:
        message = 'Serveur injoignable';
        break;
      default:
        message = 'Le journal n\'a pas pu être chargé.';
    }
    console.error('Correction Journal Service Error:', message, error);
    return throwError(() => new Error(message));
  }
}
