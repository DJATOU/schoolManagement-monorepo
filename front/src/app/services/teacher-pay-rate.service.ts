import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { API_BASE_URL } from '../api-base-url';
import { TeacherPayRate, TeacherPayRateRequest } from '../models/payroll/payroll';

/**
 * Catalogue des taux de rémunération des enseignants : appels HTTP uniquement (spec teacher-payroll,
 * exigence 1). Réservé au rôle ADMIN côté serveur, lecture comprise.
 *
 * @see TeacherPayRateController.java (backend)
 */
@Injectable({ providedIn: 'root' })
export class TeacherPayRateService {

  private readonly baseUrl = `${API_BASE_URL}/api/teacher-pay-rates`;

  constructor(private http: HttpClient) {}

  /** Tout le catalogue : actifs d'abord, par pourcentage croissant. */
  getRates(): Observable<TeacherPayRate[]> {
    return this.http.get<TeacherPayRate[]>(this.baseUrl).pipe(catchError(error => this.handleError(error)));
  }

  createRate(request: TeacherPayRateRequest): Observable<TeacherPayRate> {
    return this.http.post<TeacherPayRate>(this.baseUrl, request).pipe(catchError(error => this.handleError(error)));
  }

  /** Les paies déjà versées gardent leur copie du libellé et du pourcentage. */
  updateRate(id: number, request: TeacherPayRateRequest): Observable<TeacherPayRate> {
    return this.http.put<TeacherPayRate>(`${this.baseUrl}/${id}`, request)
      .pipe(catchError(error => this.handleError(error)));
  }

  /** Le taux n'est plus proposé pour une nouvelle paie. */
  disableRate(id: number): Observable<TeacherPayRate> {
    return this.http.patch<TeacherPayRate>(`${this.baseUrl}/${id}/disable`, {})
      .pipe(catchError(error => this.handleError(error)));
  }

  /** Gestion centralisée des erreurs HTTP : le motif du serveur est conservé quand il en porte un. */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue sur le catalogue des taux';

    if (error.error instanceof ErrorEvent) {
      errorMessage = `Erreur : ${error.error.message}`;
    } else {
      switch (error.status) {
        case 400:
        case 409:
          errorMessage = error.error?.message || 'Taux refusé';
          break;
        case 401:
          errorMessage = 'Session expirée : reconnectez-vous';
          break;
        case 403:
          errorMessage = 'Action réservée aux administrateurs';
          break;
        case 404:
          errorMessage = error.error?.message || 'Taux introuvable';
          break;
        case 0:
          errorMessage = 'Serveur injoignable';
          break;
        case 500:
          errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
          break;
      }
    }

    console.error('Teacher Pay Rate Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  }
}
