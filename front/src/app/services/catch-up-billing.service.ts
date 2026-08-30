import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';

import { API_BASE_URL } from '../api-base-url';
import { StudentAbsence } from '../models/catchUp/student-absence';
import {
  CatchUpBillingAudit,
  CorrectCatchUpRequest,
  PendingCatchUp,
  ResolveCatchUpRequest
} from '../models/catchUp/catch-up-billing';

/**
 * Facturation des rattrapages à préciser : liste, résolution, correction, piste d'audit.
 *
 * <p>Un service par entité, appels HTTP uniquement : aucune règle de facturation ici. Le serveur
 * décide du routage (même niveau + même matière) et de l'effet des décisions ; recalculer quoi que
 * ce soit côté navigateur ferait diverger l'écran du montant réellement facturé.</p>
 *
 * @see CatchUpBillingController.java (backend)
 */
@Injectable({
  providedIn: 'root'
})
export class CatchUpBillingService {

  private readonly baseUrl = `${API_BASE_URL}/api/catch-up-billing`;

  constructor(private http: HttpClient) {}

  /**
   * Rattrapages en attente de décision, du plus ancien au plus récent.
   *
   * Endpoint : GET /api/catch-up-billing/pending
   */
  getPending(): Observable<PendingCatchUp[]> {
    return this.http.get<PendingCatchUp[]>(`${this.baseUrl}/pending`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Séances manquées proposables pour un rattrapage : absences de l'étudiant dans les groupes de
   * même niveau et même matière que le groupe d'accueil.
   *
   * Endpoint : GET /api/catch-up-billing/eligible-missed-sessions
   */
  getEligibleMissedSessions(studentId: number, hostGroupId: number): Observable<StudentAbsence[]> {
    const params = new HttpParams()
      .set('studentId', studentId.toString())
      .set('hostGroupId', hostGroupId.toString());

    return this.http.get<StudentAbsence[]>(`${this.baseUrl}/eligible-missed-sessions`, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Résout un rattrapage : séance manquée et décision « déjà payée ».
   *
   * Endpoint : PATCH /api/catch-up-billing/{attendanceId}/resolve
   */
  resolve(attendanceId: number, body: ResolveCatchUpRequest): Observable<PendingCatchUp> {
    return this.http.patch<PendingCatchUp>(`${this.baseUrl}/${attendanceId}/resolve`, body).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Corrige la séance manquée et/ou la décision d'un rattrapage résolu.
   *
   * Endpoint : PATCH /api/catch-up-billing/{attendanceId}/correct
   */
  correct(attendanceId: number, body: CorrectCatchUpRequest): Observable<PendingCatchUp> {
    return this.http.patch<PendingCatchUp>(`${this.baseUrl}/${attendanceId}/correct`, body).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Piste d'audit d'un rattrapage, de la correction la plus récente à la plus ancienne.
   *
   * Endpoint : GET /api/catch-up-billing/{attendanceId}/audit
   */
  getAuditTrail(attendanceId: number): Observable<CatchUpBillingAudit[]> {
    return this.http.get<CatchUpBillingAudit[]>(`${this.baseUrl}/${attendanceId}/audit`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gestion centralisée des erreurs HTTP.
   *
   * <p>Le message du serveur est privilégié sur 400 et 409 : il nomme la cause exacte — décision
   * manquante, séance manquée sans série, rattrapage déjà enregistré — là où un message générique
   * laisserait l'administrateur sans action corrective.</p>
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue';

    if (error.error instanceof ErrorEvent) {
      errorMessage = `Erreur: ${error.error.message}`;
    } else {
      errorMessage = `Code: ${error.status}\nMessage: ${error.message}`;

      switch (error.status) {
        case 404:
          errorMessage = error.error?.message || 'Rattrapage ou séance non trouvé';
          break;
        case 400:
          errorMessage = error.error?.message || 'Données invalides';
          break;
        case 409:
          errorMessage = error.error?.message
            || 'Un rattrapage est déjà enregistré pour cette séance manquée';
          break;
        case 500:
          errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
          break;
      }
    }

    console.error('CatchUpBilling Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  }
}
