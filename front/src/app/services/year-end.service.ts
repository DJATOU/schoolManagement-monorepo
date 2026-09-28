import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { YearEndPreview, YearEndRequest, YearEndResult } from '../models/yearEnd/year-end';

/**
 * Service du workflow de fin d'année (Year_End_Workflow)
 *
 * Un service par entité, appels HTTP uniquement.
 * Gestion centralisée des erreurs (voir handleError, pattern payment.service.ts).
 *
 * @see YearEndWorkflowController.java (backend) - /api/year-end
 * @author Frontend Team
 */
@Injectable({
  providedIn: 'root'
})
export class YearEndService {

  private readonly baseUrl = `${API_BASE_URL}/api/year-end`;

  constructor(private http: HttpClient) {}

  /**
   * Prépare un aperçu du workflow de fin d'année sans rien modifier.
   *
   * Backend: YearEndWorkflowController.preview()
   * Endpoint: GET /api/year-end/preview
   *
   * @returns Observable<YearEndPreview> (libellé proposé + décisions par défaut par étudiant actif)
   */
  preview(): Observable<YearEndPreview> {
    return this.http.get<YearEndPreview>(`${this.baseUrl}/preview`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Exécute le workflow de fin d'année : clôture l'année courante, ouvre l'année
   * suivante et applique la décision de chaque étudiant (PROMOTION par défaut).
   *
   * Backend: YearEndWorkflowController.run()
   * Endpoint: POST /api/year-end/run
   *
   * @param request La requête (libellé/dates optionnels, décisions par étudiant).
   * @returns Observable<YearEndResult> (nouvelle année, liste de revue, nombre traité)
   */
  run(request: YearEndRequest): Observable<YearEndResult> {
    return this.http.post<YearEndResult>(`${this.baseUrl}/run`, request).pipe(
      catchError(this.handleError)
    );
  }

  // =========================================================================
  // ERROR HANDLING
  // =========================================================================

  /**
   * Gestion centralisée des erreurs HTTP
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue';

    if (error.error instanceof ErrorEvent) {
      // Erreur côté client
      errorMessage = `Erreur: ${error.error.message}`;
    } else {
      // Erreur côté serveur
      errorMessage = `Code: ${error.status}\nMessage: ${error.message}`;

      // Messages spécifiques selon le code HTTP
      switch (error.status) {
        case 404:
          errorMessage = 'Ressource de fin d\'année non trouvée';
          break;
        case 400:
          errorMessage = error.error?.message || 'Données invalides';
          break;
        case 409:
          errorMessage = error.error?.message || 'Conflit avec l\'état actuel des années scolaires';
          break;
        case 500:
          errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
          break;
      }
    }

    console.error('YearEnd Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  }
}
