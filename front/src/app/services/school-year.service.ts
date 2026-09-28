import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';
import { SchoolYear } from '../models/schoolYear/school-year';
import { API_BASE_URL } from '../api-base-url';

/**
 * Service de gestion des années scolaires (School Year)
 *
 * Un service par entité, appels HTTP uniquement.
 * Gestion centralisée des erreurs (voir handleError, pattern payment.service.ts).
 *
 * @see SchoolYearController.java (backend) - /api/school-years
 * @author Frontend Team
 */
@Injectable({
  providedIn: 'root'
})
export class SchoolYearService {

  private readonly baseUrl = `${API_BASE_URL}/api/school-years`;

  constructor(private http: HttpClient) {}

  /**
   * Crée une année scolaire
   *
   * Backend: SchoolYearController.create()
   * Endpoint: POST /api/school-years
   *
   * @param schoolYear Données de l'année scolaire (label, startDate, endDate)
   * @returns Observable<SchoolYear>
   */
  create(schoolYear: SchoolYear): Observable<SchoolYear> {
    return this.http.post<SchoolYear>(this.baseUrl, schoolYear).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Récupère toutes les années scolaires (triées par date de début décroissante)
   *
   * Backend: SchoolYearController.getAll()
   * Endpoint: GET /api/school-years
   *
   * @returns Observable<SchoolYear[]>
   */
  getAll(): Observable<SchoolYear[]> {
    return this.http.get<SchoolYear[]>(this.baseUrl).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Récupère une année scolaire par son identifiant
   *
   * Backend: SchoolYearController.getById()
   * Endpoint: GET /api/school-years/{id}
   *
   * @param id ID de l'année scolaire
   * @returns Observable<SchoolYear>
   */
  getById(id: number): Observable<SchoolYear> {
    return this.http.get<SchoolYear>(`${this.baseUrl}/${id}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Récupère l'année scolaire courante
   *
   * Backend: SchoolYearController.getCurrent()
   * Endpoint: GET /api/school-years/current
   *
   * @returns Observable<SchoolYear>
   */
  getCurrent(): Observable<SchoolYear> {
    return this.http.get<SchoolYear>(`${this.baseUrl}/current`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Marque une année scolaire comme courante
   *
   * Backend: SchoolYearController.setCurrent()
   * Endpoint: PATCH /api/school-years/{id}/set-current
   *
   * @param id ID de l'année scolaire à définir comme courante
   * @returns Observable<SchoolYear>
   */
  setCurrent(id: number): Observable<SchoolYear> {
    return this.http.patch<SchoolYear>(`${this.baseUrl}/${id}/set-current`, {}).pipe(
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
          errorMessage = 'Année scolaire non trouvée';
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

    console.error('SchoolYear Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  }
}
