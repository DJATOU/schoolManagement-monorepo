import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { catchError, Observable, throwError } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';

/** Erreur d'import associée à une ligne du fichier CSV. */
export interface ImportError {
  line: number;
  message: string;
}

/** Résumé d'un import CSV renvoyé par le backend. */
export interface ImportResult {
  imported: number;
  /** Lignes NON importées. */
  errors: ImportError[];
  /**
   * Lignes importées avec une réserve (ex. groupe créé sans tarif, donc sans encaissement
   * possible). Facultatif : un backend antérieur ne l'envoie pas.
   */
  warnings?: ImportError[];
}

/** Types d'import CSV pris en charge. */
export type ImportKind =
  | 'students'
  | 'teachers'
  | 'groups'
  | 'levels'
  | 'subjects'
  | 'rooms'
  | 'group-types'
  | 'pricing'
  | 'enrolments';

/**
 * Service d'import CSV (élèves, enseignants, groupes, inscriptions).
 *
 * Un service par domaine d'appel HTTP, gestion d'erreur centralisée (schéma
 * payment.service.ts). Les fichiers sont envoyés en multipart/form-data.
 *
 * @see CsvImportController.java (backend) - /api/import
 */
@Injectable({
  providedIn: 'root'
})
export class ImportService {

  private readonly baseUrl = `${API_BASE_URL}/api/import`;

  private readonly translate = inject(TranslateService);

  constructor(private http: HttpClient) {}

  importStudents(file: File): Observable<ImportResult> {
    return this.upload('students', file);
  }

  importTeachers(file: File): Observable<ImportResult> {
    return this.upload('teachers', file);
  }

  importGroups(file: File): Observable<ImportResult> {
    return this.upload('groups', file);
  }

  importLevels(file: File): Observable<ImportResult> {
    return this.upload('levels', file);
  }

  importSubjects(file: File): Observable<ImportResult> {
    return this.upload('subjects', file);
  }

  importRooms(file: File): Observable<ImportResult> {
    return this.upload('rooms', file);
  }

  importGroupTypes(file: File): Observable<ImportResult> {
    return this.upload('group-types', file);
  }

  importPricing(file: File): Observable<ImportResult> {
    return this.upload('pricing', file);
  }

  /** Inscriptions (élève → groupe, date d'arrivée) : après les élèves et les groupes. */
  importEnrolments(file: File): Observable<ImportResult> {
    return this.upload('enrolments', file);
  }

  private upload(kind: ImportKind, file: File): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportResult>(`${this.baseUrl}/${kind}`, formData)
      .pipe(catchError(this.handleError));
  }

  private handleError = (error: HttpErrorResponse): Observable<never> => {
    let errorMessage: string = this.translate.instant('HTTP_ERRORS.GENERIC');
    if (error.error instanceof ErrorEvent) {
      errorMessage = this.translate.instant('HTTP_ERRORS.WITH_MESSAGE_COMPACT', { message: error.error.message });
    } else {
      errorMessage = error.error?.message || this.translate.instant('HTTP_ERRORS.CODE', { status: error.status });
      if (error.status === 500) {
        errorMessage = this.translate.instant('HTTP_ERRORS.SERVER');
      }
    }
    console.error('Import Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  };
}
