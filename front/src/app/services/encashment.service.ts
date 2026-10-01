import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Encashment } from '../models/payment/encashment';

/**
 * Service des Encaissements (versements reçus) : appels HTTP uniquement, un service par entité.
 *
 * <p>Lecture seule pour l'instant : un reçu, et l'historique des versements d'un élève. Les deux
 * points d'entrée sont réservés au rôle ADMIN côté serveur, comme toute donnée financière.</p>
 *
 * @see EncashmentController.java (backend)
 */
@Injectable({ providedIn: 'root' })
export class EncashmentService {

  private readonly baseUrl = `${API_BASE_URL}/api`;

  constructor(private http: HttpClient) {}

  /** Un Encaissement et sa répartition, pour réimprimer son reçu. `GET /api/encashments/{id}`. */
  getEncashment(id: number): Observable<Encashment> {
    return this.http.get<Encashment>(`${this.baseUrl}/encashments/${id}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Encaissements d'un élève, le plus récent d'abord, annulés compris.
   * `GET /api/students/{studentId}/encashments`.
   */
  getStudentEncashments(studentId: number): Observable<Encashment[]> {
    return this.http.get<Encashment[]>(`${this.baseUrl}/students/${studentId}/encashments`).pipe(
      catchError(this.handleError)
    );
  }

  /** Gestion centralisée des erreurs HTTP, message du serveur conservé quand il en porte un. */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue lors de la lecture des versements';

    if (error.error instanceof ErrorEvent) {
      errorMessage = `Erreur : ${error.error.message}`;
    } else {
      switch (error.status) {
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
    return throwError(() => new Error(errorMessage));
  }
}
