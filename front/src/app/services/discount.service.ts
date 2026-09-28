import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Discount, DiscountRequest } from '../models/discount/student-discount';

/**
 * Service des réductions (discounts).
 *
 * Aligné sur le backend (`DiscountController`) : listing (GET), création (POST),
 * mise à jour du taux (PUT) et suppression (DELETE).
 * Le taux est un décimal dans [0.00, 1.00]. Gestion d'erreur centralisée.
 *
 * @see DiscountController.java - /api/discounts
 */
@Injectable({
  providedIn: 'root'
})
export class DiscountService {
  private apiUrl = `${API_BASE_URL}/api/discounts`;

  constructor(private http: HttpClient) {}

  /** Liste toutes les réductions. */
  getAllDiscounts(): Observable<Discount[]> {
    return this.http.get<Discount[]>(this.apiUrl).pipe(catchError(this.handleError));
  }

  /**
   * Réductions d'un étudiant donné.
   *
   * Le filtre est appliqué par le serveur : la fiche étudiante doit pouvoir annoncer
   * « 65 % de réduction » sans télécharger toutes les réductions de l'école.
   */
  getDiscountsForStudent(studentId: number): Observable<Discount[]> {
    return this.http.get<Discount[]>(this.apiUrl, { params: { studentId } })
      .pipe(catchError(this.handleError));
  }

  /** Crée une réduction (portée + taux). */
  addDiscount(discount: DiscountRequest): Observable<Discount> {
    return this.http.post<Discount>(this.apiUrl, discount).pipe(catchError(this.handleError));
  }

  /**
   * Met à jour le taux d'une réduction. Seul le taux est modifiable : changer la portée
   * ou la cible revient à une autre réduction (supprimer puis recréer).
   */
  updateRate(id: number, rate: number): Observable<Discount> {
    return this.http.put<Discount>(`${this.apiUrl}/${id}`, { rate }).pipe(catchError(this.handleError));
  }

  /** Supprime définitivement une réduction. */
  deleteDiscount(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(catchError(this.handleError));
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    let message = 'Une erreur est survenue';
    if (error.error instanceof ErrorEvent) {
      message = `Erreur: ${error.error.message}`;
    } else {
      message = error.error?.message || `Code: ${error.status}`;
    }
    console.error('Discount Service Error:', message, error);
    return throwError(() => new Error(message));
  }
}
