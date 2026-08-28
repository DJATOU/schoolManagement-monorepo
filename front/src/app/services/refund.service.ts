import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Refund, RefundCap, RefundReceipt, RefundRequest } from '../models/refund/refund';

/**
 * Service des remboursements : appels HTTP uniquement, un service par entité.
 *
 * <p>La gestion d'erreur suit le modèle centralisé du projet, avec une nuance importante : le
 * message renvoyé par le serveur est <strong>préservé tel quel</strong> sur les erreurs 400. Le
 * backend y nomme les trois montants en jeu — versé, déjà remboursé, plafond restant — et le
 * remplacer par un « Données invalides » générique priverait l'administrateur de l'information dont
 * il a besoin face à une famille.</p>
 *
 * @see RefundController.java (backend)
 */
@Injectable({ providedIn: 'root' })
export class RefundService {

  private readonly baseUrl = `${API_BASE_URL}/api/refunds`;

  constructor(private http: HttpClient) {}

  /**
   * Plafond de remboursement d'un versement.
   *
   * <p>Backend : `GET /api/refunds/payment/{paymentId}/cap`, ouvert aux deux rôles — constater ce
   * qui reste remboursable n'est pas une écriture.</p>
   */
  getRefundableCap(paymentId: number): Observable<RefundCap> {
    return this.http.get<RefundCap>(`${this.baseUrl}/payment/${paymentId}/cap`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Enregistre un remboursement.
   *
   * <p>Backend : `POST /api/refunds`, réservé à ADMIN. Le serveur revalide montant, motif et
   * plafond : le blocage côté client ne le dispense pas, le plafond ayant pu changer depuis
   * l'ouverture du formulaire.</p>
   */
  createRefund(request: RefundRequest): Observable<Refund> {
    return this.http.post<Refund>(this.baseUrl, request).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Émet le reçu d'un remboursement.
   *
   * <p>Backend : `POST /api/refunds/{id}/receipts`. C'est bien un POST : chaque émission est
   * enregistrée pour que les réimpressions portent la mention « Duplicata » — un reçu de caisse
   * réimprimé sans mention peut servir deux fois.</p>
   */
  issueReceipt(refundId: number): Observable<RefundReceipt> {
    return this.http.post<RefundReceipt>(`${this.baseUrl}/${refundId}/receipts`, {}).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gestion centralisée des erreurs HTTP.
   *
   * <p>Le message du serveur est conservé sur 400 et 409 : ces réponses portent la cause précise
   * (plafond dépassé avec ses montants, motif manquant, numéro de pièce non attribué), et un
   * message générique obligerait l'administrateur à deviner.</p>
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue lors du remboursement';

    if (error.error instanceof ErrorEvent) {
      errorMessage = `Erreur : ${error.error.message}`;
    } else {
      switch (error.status) {
        case 400:
        case 409:
          errorMessage = error.error?.message || 'Remboursement refusé';
          break;
        case 403:
          errorMessage = 'Action réservée aux administrateurs';
          break;
        case 404:
          errorMessage = error.error?.message || 'Versement ou remboursement introuvable';
          break;
        case 0:
          // Requête jamais parvenue au serveur : l'appelant doit pouvoir distinguer ce cas d'un
          // refus, car la demande n'a peut-être pas abouti.
          errorMessage = 'Serveur injoignable : le résultat de l\'opération est inconnu';
          break;
        case 500:
          errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
          break;
      }
    }

    console.error('Refund Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  }
}
