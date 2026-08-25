import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, catchError, map, tap, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { AuthResponse, AuthUser, LoginRequest, Role } from '../models/auth/auth.model';

const TOKEN_KEY = 'sm_auth_token';

/**
 * Service d'authentification (un service par entité).
 *
 * Gère la connexion/déconnexion, le stockage du jeton (localStorage), l'utilisateur courant
 * ({@link currentUser$}) et la vérification de rôle. Au démarrage, restaure l'utilisateur depuis
 * le jeton tant qu'il n'est pas expiré. Gestion d'erreur centralisée (motif payment.service).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiUrl = `${API_BASE_URL}/api/v1/auth`;

  private readonly currentUserSubject = new BehaviorSubject<AuthUser | null>(this.restoreUser());
  /** Utilisateur courant (null si déconnecté). */
  readonly currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  /** Connexion : stocke le jeton et met à jour l'utilisateur courant. */
  login(request: LoginRequest): Observable<AuthUser> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, request).pipe(
      map(response => {
        localStorage.setItem(TOKEN_KEY, response.token);
        const user: AuthUser = { username: response.username, role: response.role };
        this.currentUserSubject.next(user);
        return user;
      }),
      catchError(this.handleError)
    );
  }

  /** Déconnexion côté client : supprime le jeton et réinitialise l'utilisateur. */
  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.currentUserSubject.next(null);
  }

  /** Jeton courant (ou null). */
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  /** Utilisateur courant (valeur synchrone). */
  get currentUser(): AuthUser | null {
    return this.currentUserSubject.value;
  }

  /** Vrai si un utilisateur est connecté avec un jeton non expiré. */
  isAuthenticated(): boolean {
    return this.currentUserSubject.value !== null && !this.isTokenExpired(this.getToken());
  }

  /** Vrai si l'utilisateur courant possède le rôle demandé. */
  hasRole(role: Role): boolean {
    return this.currentUserSubject.value?.role === role;
  }

  /** Restaure l'utilisateur depuis le jeton stocké s'il est encore valide. */
  private restoreUser(): AuthUser | null {
    const token = this.getToken();
    if (!token || this.isTokenExpired(token)) {
      if (token) {
        localStorage.removeItem(TOKEN_KEY);
      }
      return null;
    }
    const payload = this.decodePayload(token);
    if (!payload?.sub || !payload?.role) {
      return null;
    }
    return { username: payload.sub, role: payload.role as Role };
  }

  /** Décode (sans vérifier la signature) la charge utile du JWT. */
  private decodePayload(token: string): { sub?: string; role?: string; exp?: number } | null {
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(base64));
    } catch {
      return null;
    }
  }

  /** Vrai si le jeton est absent ou expiré (claim exp en secondes). */
  private isTokenExpired(token: string | null): boolean {
    if (!token) {
      return true;
    }
    const payload = this.decodePayload(token);
    if (!payload?.exp) {
      return true;
    }
    return payload.exp * 1000 <= Date.now();
  }

  /** Gestion d'erreur centralisée. */
  private handleError = (error: HttpErrorResponse): Observable<never> => {
    let message = 'Une erreur est survenue';
    if (error.status === 401) {
      message = 'Identifiant ou mot de passe invalide.';
    } else if (error.error?.message) {
      message = error.error.message;
    }
    return throwError(() => new Error(message));
  };
}
