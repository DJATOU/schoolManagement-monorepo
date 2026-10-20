import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
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

  private readonly translate = inject(TranslateService);

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
  private handleError = (error: HttpErrorResponse): Observable<never> => {
    let errorMessage: string = this.translate.instant('HTTP_ERRORS.GENERIC');

    if (error.error instanceof ErrorEvent) {
      // Erreur côté client
      errorMessage = this.translate.instant('HTTP_ERRORS.WITH_MESSAGE_COMPACT', { message: error.error.message });
    } else {
      // Erreur côté serveur
      errorMessage = this.translate.instant('HTTP_ERRORS.CODE_MESSAGE', { status: error.status, message: error.message });

      // Messages spécifiques selon le code HTTP
      switch (error.status) {
        case 404:
          errorMessage = this.translate.instant('HTTP_ERRORS.YEAR_END.NOT_FOUND');
          break;
        case 400:
          errorMessage = error.error?.message || this.translate.instant('HTTP_ERRORS.INVALID_DATA');
          break;
        case 409:
          errorMessage = error.error?.message || this.translate.instant('HTTP_ERRORS.SCHOOL_YEAR.CONFLICT');
          break;
        case 500:
          errorMessage = this.translate.instant('HTTP_ERRORS.SERVER');
          break;
      }
    }

    console.error('YearEnd Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  };
}
