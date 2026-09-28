import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
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
  errors: ImportError[];
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
  | 'pricing';

/**
 * Service d'import CSV (élèves, enseignants, groupes).
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

  private upload(kind: ImportKind, file: File): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportResult>(`${this.baseUrl}/${kind}`, formData)
      .pipe(catchError(this.handleError));
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue';
    if (error.error instanceof ErrorEvent) {
      errorMessage = `Erreur: ${error.error.message}`;
    } else {
      errorMessage = error.error?.message || `Code: ${error.status}`;
      if (error.status === 500) {
        errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
      }
    }
    console.error('Import Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  }
}
