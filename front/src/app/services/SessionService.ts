import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Session } from '../models/session/session';
import { RecurringSessionRequest, RecurringSessionResult } from '../models/session/recurring-session';
import { Student } from '../components/student/domain/student';

@Injectable({
  providedIn: 'root'
})
export class SessionService {
  private apiUrl = `${API_BASE_URL}/api/sessions`;
  private apiUrl2 = `${API_BASE_URL}/api/student-groups`;

  constructor(private http: HttpClient) { }

  // Create a new session
  createSession(session: Session): Observable<Session> {
    return this.http.post<Session>(this.apiUrl, session);
  }

  /**
   * Simule une récurrence : nombre d'occurrences et conflits, sans rien enregistrer.
   *
   * <p>Le calcul est fait par le serveur, seul à connaître les créneaux déjà occupés et
   * les règles de rattachement aux séries.</p>
   */
  previewRecurringSessions(request: RecurringSessionRequest): Observable<RecurringSessionResult> {
    return this.http.post<RecurringSessionResult>(`${this.apiUrl}/recurring/preview`, request);
  }

  /** Crée les séances d'une récurrence en une seule requête (une transaction serveur). */
  createRecurringSessions(request: RecurringSessionRequest): Observable<RecurringSessionResult> {
    return this.http.post<RecurringSessionResult>(`${this.apiUrl}/recurring`, request);
  }

  // Get all sessions
  getAllSessions(): Observable<Session[]> {
    return this.http.get<Session[]>(this.apiUrl);
  }

  getAllSessionsWithDetail(): Observable<Session[]> {
    return this.http.get<Session[]>(`${this.apiUrl}/detail`);
  }

  // Get a single session by ID
  getSessionById(id: number): Observable<Session> {
    return this.http.get<Session>(`${this.apiUrl}/${id}`);
  }

  /**
   * Modification partielle d'une séance.
   *
   * Le serveur n'applique que les clés qu'il connaît : on envoie un patch restreint aux
   * champs modifiables plutôt que l'objet séance complet, qui embarque des champs dérivés
   * et imbriqués.
   */
  updateSession(id: number, session: Partial<Session>): Observable<Session> {
    return this.http.patch<Session>(`${this.apiUrl}/${id}`, session);
  }

  getStudentsByGroupId(groupId: number): Observable<Student[]> {
    return this.http.get<Student[]>(`${this.apiUrl2}/${groupId}/students`);
  }

  getStudentsForSession(groupId: number, sessionDate: Date): Observable<Student[]> {
    return this.http.get<Student[]>(
      `${this.apiUrl2}/${groupId}/studentsForSession?date=${sessionDate}`
    );
  }

  markSessionAsFinished(sessionId: number): Observable<Session> {
    return this.http.patch<Session>(`${this.apiUrl}/${sessionId}/finish`, {});
  }

  markSessionAsUnfinished(sessionId: number): Observable<Session> {
    return this.http.patch<Session>(`${this.apiUrl}/${sessionId}/unfinish`, {});
  }

  /**
   * Supprime une séance de l'application (désactivation).
   *
   * <p>On utilise volontairement `PATCH /{id}/deactivate` et non `DELETE /{id}` : cet
   * endpoint désactive aussi les présences <strong>et les détails de paiement</strong>
   * rattachés à la séance. Une suppression définitive les laisserait actifs, ce qui
   * faussterait les calculs de paiement (« le montant payé dépasse le coût total ») et
   * ferait perdre l'historique.</p>
   */
  deactivateSession(sessionId: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${sessionId}/deactivate`, {});
  }

  // Get sessions by series ID
  getSessionsBySeriesId(sessionSeriesId: number): Observable<Session[]> {
    return this.http.get<Session[]>(`${this.apiUrl}/series/${sessionSeriesId}`);
  }

  getSessionsInDateRange(groupId: number, start: Date, end: Date): Observable<Session[]> {
    const params = new HttpParams()
      .set('groupId', groupId.toString())
      .set('start', start.toISOString())
      .set('end', end.toISOString());

    return this.http.get<Session[]>(`${this.apiUrl}/sessions`, { params }).pipe(
      catchError(error => throwError(() => error))
    );
  }
}
