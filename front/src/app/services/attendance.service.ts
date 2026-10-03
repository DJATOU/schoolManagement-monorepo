import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, tap, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { Attendance } from '../models/Attendance/attendance';
import {
  JustificationAudit,
  JustificationUpdateResult
} from '../models/Attendance/justification';
import { AttendanceSubmissionError } from '../models/Attendance/rejected-absence';


@Injectable({
  providedIn: 'root'
})
export class AttendanceService {
    private apiUrl = `${API_BASE_URL}/api/attendances`;  // Adjust based on your API URL structure

    constructor(private http: HttpClient) {}
    
    getAttendanceBySessionId(sessionId: number): Observable<Attendance[]> {
      console.log(`Fetching attendances for session ID: ${sessionId}`);
    
      return this.http.get<Attendance[]>(`${this.apiUrl}/session/${sessionId}`).pipe(
        tap({
          next: (attendances: Attendance[]) => {
            console.log('Attendance data retrieved successfully:', attendances);
          },
          error: (error: Error) => {
            console.error('Failed to retrieve attendance data:', error);
          }
        })
      );
    }
    
    
   
    /**
     * Valide une feuille de présence. Un refus garde ce que le serveur y joint : pour des absences
     * hors période (409 `ABSENCE_OUTSIDE_WINDOW`), chaque ligne refusée, que l'écran retire avant
     * de revalider (exigence 7.5). Tout autre 409 était lu comme un doublon : il l'est encore
     * quand le serveur ne dit rien de plus.
     */
    submitAttendance(attendances: Attendance[]): Observable<Attendance[]> {
      return this.http.post<Attendance[]>(`${this.apiUrl}/bulk`, attendances).pipe(
        catchError((error: HttpErrorResponse) => throwError(() => this.submissionError(error)))
      );
    }

    private submissionError(error: HttpErrorResponse): AttendanceSubmissionError {
      const body = error.error ?? {};
      const serverMessage = typeof body.message === 'string' && body.message.trim().length > 0 ? body.message : null;
      let message: string;
      switch (error.status) {
        case 409:
          message = serverMessage ?? 'Une présence existe déjà pour un ou plusieurs étudiants de cette séance.';
          break;
        case 400:
        case 404:
          message = serverMessage ?? 'Feuille de présence refusée.';
          break;
        case 403:
          message = 'Action réservée aux administrateurs';
          break;
        case 0:
          message = 'Serveur injoignable';
          break;
        default:
          message = 'La feuille de présence n\'a pas pu être enregistrée.';
      }
      return new AttendanceSubmissionError(message, error.status, body.errorCode ?? null,
        Array.isArray(body.rejected) ? body.rejected : []);
    }

    // deleteAttendanceBySessionId et deactivateAttendanceBySessionId retirés (D.3) : leurs adresses
    // n'existent plus côté serveur. Une feuille se désactive par la dévalidation de sa séance
    // (SessionService.unvalidate), avec Motif, Aperçu et Trace.


  getAttendanceByStudentAndSeries(studentId: number, sessionSeriesId : number): Observable<Attendance[]> {
    console.log(`Fetching attendances for student ID: ${studentId} and series ID: ${sessionSeriesId }`);

    return this.http.get<Attendance[]>(`${this.apiUrl}/student/${studentId}/series/${sessionSeriesId }`).pipe(
      tap({
        next: (attendances: Attendance[]) => {
          console.log('Attendance data for student and series retrieved successfully:', attendances);
        },
        error: (error: Error) => {
          console.error('Failed to retrieve attendance data for student and series:', error);
        }
      }),
      catchError(() => {
        return throwError(() => new Error('Failed to retrieve attendance data.'));
      })
    );
  }

  /**
   * Modifie la justification d'une absence.
   *
   * <p>Backend : `PATCH /api/attendances/{id}/justification`, réservé à ADMIN. Le corps est
   * volontairement limité à deux champs — un PATCH générique existait auparavant et permettait
   * d'écraser n'importe quel champ d'une présence.</p>
   *
   * <p>La justification n'a **aucun effet financier** : elle sert au suivi disciplinaire et au droit
   * au rattrapage. Aucun montant n'est à recharger après un appel réussi.</p>
   *
   * <p>Le message d'erreur du serveur est conservé tel quel : il nomme la cause exacte du refus —
   * présence marquée présent, présence désactivée, année scolaire close — et un message générique
   * obligerait l'administrateur à deviner.</p>
   */
  updateJustification(
    attendanceId: number,
    justified: boolean,
    comment?: string
  ): Observable<JustificationUpdateResult> {
    return this.http.patch<JustificationUpdateResult>(
      `${this.apiUrl}/${attendanceId}/justification`,
      { justified, comment: comment?.trim() || null }
    ).pipe(
      catchError((error: HttpErrorResponse) => throwError(() =>
        new Error(this.justificationErrorMessage(error))))
    );
  }

  /**
   * Piste d'audit de la justification d'une absence.
   *
   * <p>Backend : `GET /api/attendances/{id}/justification-audit`, ouvert aux deux rôles — constater
   * qui a modifié quoi n'est pas une écriture. Renvoie un tableau vide si la justification n'a jamais
   * été modifiée.</p>
   */
  getJustificationAudit(attendanceId: number): Observable<JustificationAudit[]> {
    return this.http.get<JustificationAudit[]>(
      `${this.apiUrl}/${attendanceId}/justification-audit`
    ).pipe(
      catchError(() => throwError(() =>
        new Error("L'historique des modifications n'a pas pu être chargé")))
    );
  }

  /** Message d'erreur d'une modification de justification, cause du serveur préservée. */
  private justificationErrorMessage(error: HttpErrorResponse): string {
    switch (error.status) {
      case 400:
      case 409:
        return error.error?.message || 'Modification refusée';
      case 403:
        return 'Action réservée aux administrateurs';
      case 404:
        return 'Présence introuvable';
      case 0:
        // Requête jamais parvenue : distinct d'un refus, la modification a peut-être abouti.
        return "Serveur injoignable : le résultat de l'opération est inconnu";
      default:
        return 'Une erreur est survenue lors de la modification';
    }
  }
}
