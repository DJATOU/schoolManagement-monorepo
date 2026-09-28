import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';
import { Student } from '../domain/student';
import { API_BASE_URL } from '../../../api-base-url';
import { Group } from '../../../models/group/group';
import { StudentFullHistoryDTO } from '../domain/StudentFullHistoryDTO';
import { Parcours } from '../../../models/parcours/parcours';
import { GroupChange } from '../../../models/group/group-change';

@Injectable({
  providedIn: 'root'
})
export class StudentService {
  private apiUrl = `${API_BASE_URL}/api/students`;
  private apiUrl2 = `${API_BASE_URL}/api/student-groups`;
  constructor(private http: HttpClient) { }

  /**
   * Liste les étudiants, éventuellement situés dans une année scolaire.
   *
   * - Année courante (ou `schoolYearId` omis) : filtrage par statut (actifs par défaut).
   * - Année passée : étudiants inscrits dans les groupes de cette année (historique figé).
   *
   * @param schoolYearId année scolaire sélectionnée (optionnel)
   * @param includeInactive inclure les étudiants inactifs (année courante)
   */
  getStudents(schoolYearId?: number | null, includeInactive = false): Observable<Student[]> {
    let params = new HttpParams();
    if (schoolYearId != null) {
      params = params.set('schoolYearId', schoolYearId);
    }
    if (includeInactive) {
      params = params.set('includeInactive', true);
    }
    return this.http.get<Student[]>(this.apiUrl, { params });
  }
  
  /**
   * Groupes de l'étudiant, éventuellement restreints à une année scolaire.
   *
   * Sans `schoolYearId`, toutes les années sont renvoyées. Les écrans qui suivent le
   * sélecteur d'année doivent passer l'identifiant, sinon ils affichent des groupes d'années
   * révolues absents de la liste des groupes.
   */
  getGroupsForStudent(id: number, schoolYearId?: number): Observable<Group[]> {
    const params = schoolYearId != null ? { params: { schoolYearId } } : {};
    return this.http.get<Group[]>(`${this.apiUrl2}/${id}/groups`, params);
  }
  
  getStudentById(id: number): Observable<Student> {
    return this.http.get<Student>(`${this.apiUrl}/id/${id}`);
  }
  

  createStudent(studentData: FormData): Observable<Student> {
    return this.http.post<Student>(`${this.apiUrl}/createStudent`, studentData);
  }

  updateStudent1(id: number, student: Student): Observable<Student> {
    return this.http.put<Student>(`${this.apiUrl}/${id}`, student);
  }

  updateStudent(student: Student): Observable<Student> {
    if (!student.id) {
      throw new Error('Student ID is required for update.');
    }
    return this.http.put<Student>(`${this.apiUrl}/${student.id}`, student);
  }

  searchStudents(firstName: string, lastName: string, level: number, groupId: string, establishment: string): Observable<Student[]> {
    return this.http.get<Student[]>(`${this.apiUrl}/search`, {
      params: new HttpParams()
        .set('firstName', firstName)
        .set('lastName', lastName)
        .set('level', level)
        .set('groupId', groupId)
        .set('establishment', establishment)
    });
  }

  getStudentsByFirstNameAndLastName(firstName?: string, lastName?: string): Observable<Student[]> {
    let params = new HttpParams();
    if (firstName) {
      params = params.set('firstName', firstName);
    }
    if (lastName) {
      params = params.set('lastName', lastName);
    }

    return this.http.get<Student[]>(`${this.apiUrl}/searchByNames`, { params });
  }

  searchStudentsByNameStartingWith(searchTerm: string): Observable<Student[]> {
    return this.http.get<Student[]>(`${this.apiUrl}/searchByNames`, {
      params: new HttpParams().set('search', searchTerm)
    });
  }

  addGroupsToStudent(studentId: number, groupIds: number[]): Observable<any> {
    return this.http.post(`${this.apiUrl2}/${studentId}/addGroups`, { groupIds });
  }
  
  disableStudent(id: number): Observable<boolean> {
    return this.http.delete<boolean>(`${this.apiUrl}/disable/${id}`);
  }

  /**
   * Désactive un étudiant (statut INACTIVE / départ). L'étudiant est exclu des listes
   * courantes par défaut mais son historique est conservé (exigence 7.1).
   */
  /**
   * Rattache un tuteur à l'étudiant, ou le détache avec `tutorId = null`.
   *
   * Point d'entrée dédié : la mise à jour générale de l'étudiant ignore les valeurs nulles
   * côté backend, elle ne permet donc pas de retirer un tuteur déjà rattaché.
   */
  setTutor(studentId: number, tutorId: number | null): Observable<Student> {
    return this.http.patch<Student>(`${this.apiUrl}/${studentId}/tutor`, { tutorId });
  }

  deactivateStudent(id: number): Observable<Student> {
    return this.http.patch<Student>(`${this.apiUrl}/${id}/deactivate`, {});
  }

  /** Réactive un étudiant (statut ACTIVE, exigence 7.5). */
  reactivateStudent(id: number): Observable<Student> {
    return this.http.patch<Student>(`${this.apiUrl}/${id}/reactivate`, {});
  }

// student.service.ts
  getStudentsByLevel(levelId: number): Observable<Student[]> {
    return this.http.get<Student[]>(`${this.apiUrl}/levels/${levelId}`);
  }

  removeStudentFromGroup(groupId: number | undefined, studentId: number | undefined): Observable<any> {
    if (groupId === undefined || studentId === undefined) {
      throw new Error('Group ID and Student ID must be defined');
    }
    return this.http.delete(`${this.apiUrl2}/${groupId}/students/${studentId}`);
  }
  


  getStudentFullHistory(studentId: number): Observable<StudentFullHistoryDTO> {
    return this.http.get<StudentFullHistoryDTO>(`${this.apiUrl}/${studentId}/full-history`)
      .pipe(catchError(this.handleError));
  }

  /**
   * Récupère le parcours d'un étudiant : pour chaque année scolaire fréquentée,
   * les niveaux distincts et les groupes suivis, triés par date de début d'année
   * décroissante (exigence 11.1, 11.2, 11.3, 11.5).
   * @param studentId ID de l'étudiant
   * @returns le parcours de l'étudiant
   */
  getParcours(studentId: number): Observable<Parcours> {
    return this.http.get<Parcours>(`${this.apiUrl}/${studentId}/parcours`)
      .pipe(catchError(this.handleError));
  }

  /**
   * Signalements de changement de groupe de l'étudiant, du plus ancien au plus récent
   * (exigences 10.2, 10.5).
   *
   * Le serveur renvoie un tableau vide quand il n'y a rien à signaler — cas de très loin le
   * plus fréquent, et non une erreur : un étudiant inconnu produit également un tableau vide.
   * L'erreur HTTP n'est donc volontairement pas transformée ici : l'appelant la journalise et
   * n'affiche rien, un signalement informatif indisponible ne devant jamais empêcher la
   * consultation d'une fiche ni la saisie d'un versement (exigence 10.7).
   */
  getGroupChanges(studentId: number): Observable<GroupChange[]> {
    return this.http.get<GroupChange[]>(`${this.apiUrl}/${studentId}/group-changes`);
  }

  /**
   * PHASE 3A: Upload photo pour un étudiant
   * @param studentId ID de l'étudiant
   * @param file Fichier photo à uploader
   * @returns Observable avec le nom du fichier uploadé
   */
  uploadStudentPhoto(studentId: number, file: File): Observable<string> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<string>(`${this.apiUrl}/${studentId}/photo`, formData, {
      responseType: 'text' as 'json' // Le backend retourne un string, pas du JSON
    });
  }

  /**
   * PHASE 3A: Récupère l'URL de la photo d'un étudiant
   * @param studentId ID de l'étudiant
   * @returns URL de la photo
   */
  getStudentPhotoUrl(studentId: number): string {
    return `${this.apiUrl}/${studentId}/photo`;
  }

  // =========================================================================
  // ERROR HANDLING
  // =========================================================================

  /**
   * Gestion centralisée des erreurs HTTP (même schéma que PaymentService.handleError).
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Une erreur est survenue';

    if (error.error instanceof ErrorEvent) {
      // Erreur côté client
      errorMessage = `Erreur: ${error.error.message}`;
    } else {
      // Erreur côté serveur
      errorMessage = `Code: ${error.status}\nMessage: ${error.message}`;

      // Messages spécifiques selon le code HTTP
      switch (error.status) {
        case 404:
          errorMessage = 'Étudiant non trouvé';
          break;
        case 400:
          errorMessage = error.error?.message || 'Données invalides';
          break;
        case 500:
          errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
          break;
      }
    }

    console.error('Student Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  }
}
