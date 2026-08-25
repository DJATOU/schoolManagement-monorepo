import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Role, UserAccount } from '../models/auth/auth.model';

/**
 * Service de gestion des comptes utilisateurs (un service par entité, appels HTTP uniquement).
 * Réservé à l'ADMIN par la chaîne de sécurité backend. Gestion d'erreur centralisée.
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly apiUrl = `${API_BASE_URL}/api/v1/users`;

  constructor(private http: HttpClient) {}

  /** Liste les comptes (sans mot de passe). */
  getUsers(): Observable<UserAccount[]> {
    return this.http.get<UserAccount[]>(this.apiUrl).pipe(catchError(this.handleError));
  }

  /** Crée un compte. */
  createUser(payload: { username: string; password: string; role: Role }): Observable<UserAccount> {
    return this.http.post<UserAccount>(this.apiUrl, payload).pipe(catchError(this.handleError));
  }

  /** Désactive un compte. */
  disableUser(id: number): Observable<UserAccount> {
    return this.http.patch<UserAccount>(`${this.apiUrl}/${id}/disable`, {}).pipe(catchError(this.handleError));
  }

  /** Réactive un compte. */
  enableUser(id: number): Observable<UserAccount> {
    return this.http.patch<UserAccount>(`${this.apiUrl}/${id}/enable`, {}).pipe(catchError(this.handleError));
  }

  /** Réinitialise le mot de passe d'un compte. */
  resetPassword(id: number, newPassword: string): Observable<UserAccount> {
    return this.http.patch<UserAccount>(`${this.apiUrl}/${id}/reset-password`, { newPassword })
      .pipe(catchError(this.handleError));
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    let message = 'Une erreur est survenue';
    if (error.status === 409) {
      message = error.error?.message || 'Cet identifiant est déjà utilisé.';
    } else if (error.error?.message) {
      message = error.error.message;
    }
    return throwError(() => new Error(message));
  }
}
