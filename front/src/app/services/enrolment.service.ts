import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { AttendanceMarks, CorrectionReason, CorrectionResponse } from '../models/correction/correction';
import { CorrectionError } from '../models/correction/correction-error';
import { Enrolment, EnrolmentCorrection, EnrolmentCorrectionReasons } from '../models/enrolment/enrolment';
import { CorrectionStep } from './encashment.service';

/**
 * Service des inscriptions : appels HTTP uniquement, un service par entité.
 *
 * <p>Lecture : les inscriptions d'un élève, ouvertes et closes, avec leur période. Corrections :
 * corriger l'arrivée, enregistrer ou corriger le départ, rouvrir, chacune en Aperçu puis en
 * confirmation (spec admin-corrections, C.6 et C.8 ; exigences 5 et 6). Les corrections sont
 * réservées au rôle ADMIN côté serveur.</p>
 *
 * <p>Une correction refusée garde ce que le serveur y joint — nouvel Aperçu d'un Aperçu périmé —
 * dans une {@link CorrectionError}, comme pour les Encaissements.</p>
 *
 * @see StudentGroupController.java, EnrolmentCorrectionController.java (backend)
 */
@Injectable({ providedIn: 'root' })
export class EnrolmentService {

  private readonly baseUrl = `${API_BASE_URL}/api`;

  constructor(private http: HttpClient) {}

  /**
   * Inscriptions d'un élève, ouvertes et closes, par groupe puis par arrivée ; restreintes à une
   * année scolaire si elle est donnée. `GET /api/student-groups/{studentId}/enrolments`.
   */
  getStudentEnrolments(studentId: number, schoolYearId?: number | null): Observable<Enrolment[]> {
    const options = schoolYearId != null ? { params: { schoolYearId } } : {};
    return this.http.get<Enrolment[]>(`${this.baseUrl}/student-groups/${studentId}/enrolments`, options).pipe(
      catchError(error => this.handleError(error))
    );
  }

  /** Motifs proposés pour chaque correction, dans l'ordre d'affichage. */
  getCorrectionReasons(): Observable<EnrolmentCorrectionReasons> {
    return this.http.get<EnrolmentCorrectionReasons>(`${this.baseUrl}/enrolments/correction-reasons`).pipe(
      catchError(error => this.handleError(error))
    );
  }

  /** Corriger la date d'arrivée (`yyyy-MM-dd`) : Aperçu, puis confirmation de cet Aperçu. */
  correctArrival(id: number, step: CorrectionStep, arrival: string, reason: CorrectionReason,
                 previewToken?: string, marks?: AttendanceMarks): Observable<CorrectionResponse<EnrolmentCorrection>> {
    return this.post(id, 'arrival', step, { arrival }, reason, previewToken, marks);
  }

  /**
   * Enregistrer ou corriger le départ (`yyyy-MM-dd`). `removePresencesAfter` retire aussi les
   * présences ordinaires postérieures ; sinon elles restent, facturées comme séances consommées.
   */
  setDeparture(id: number, step: CorrectionStep, departure: string, removePresencesAfter: boolean,
               reason: CorrectionReason, previewToken?: string,
               marks?: AttendanceMarks): Observable<CorrectionResponse<EnrolmentCorrection>> {
    return this.post(id, 'departure', step, { departure, removePresencesAfter }, reason, previewToken, marks);
  }

  /** Rouvrir une inscription close : son départ avait été saisi à tort. */
  reopen(id: number, step: CorrectionStep, reason: CorrectionReason, previewToken?: string,
         marks?: AttendanceMarks): Observable<CorrectionResponse<EnrolmentCorrection>> {
    return this.post(id, 'reopen', step, {}, reason, previewToken, marks);
  }

  private post(id: number, correction: 'arrival' | 'departure' | 'reopen', step: CorrectionStep,
               body: Record<string, unknown>, reason: CorrectionReason, previewToken?: string,
               marks?: AttendanceMarks): Observable<CorrectionResponse<EnrolmentCorrection>> {
    return this.http.post<CorrectionResponse<EnrolmentCorrection>>(
      `${this.baseUrl}/enrolments/${id}/${correction}/${step}`, {
        ...body,
        attendances: Object.entries(marks ?? {}).map(([sessionId, present]) => ({ sessionId: Number(sessionId), present })),
        reasonType: reason.type,
        reasonText: reason.text ?? null,
        previewToken: previewToken ?? null
      }).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Gestion centralisée des erreurs HTTP : le motif du serveur est conservé quand il en porte un,
   * avec l'Aperçu joint à un refus pour Aperçu périmé.
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue lors de la lecture des inscriptions';

    if (error.error instanceof ErrorEvent) {
      errorMessage = `Erreur : ${error.error.message}`;
    } else {
      switch (error.status) {
        case 400:
        case 409:
          errorMessage = error.error?.message || 'Correction refusée';
          break;
        case 401:
          errorMessage = 'Session expirée : reconnectez-vous';
          break;
        case 403:
          errorMessage = 'Action réservée aux administrateurs';
          break;
        case 404:
          errorMessage = error.error?.message || 'Inscription introuvable';
          break;
        case 0:
          errorMessage = 'Serveur injoignable';
          break;
        case 500:
          errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
          break;
      }
    }

    console.error('Enrolment Service Error:', errorMessage, error);
    const body = error.error ?? {};
    return throwError(() => new CorrectionError(errorMessage, error.status, body.errorCode ?? null,
      body.preview ?? null, body.previewToken ?? null, body.blockingRefunds ?? []));
  }
}
