import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../app.config';
import { Tutor } from '../models/tutor/tutor';

@Injectable({
  providedIn: 'root'
})
export class TutorService {
  private apiUrl = `${API_BASE_URL}/api/tutors`;

  constructor(private http: HttpClient) {}

  getTutors(): Observable<Tutor[]> {
    return this.http.get<Tutor[]>(this.apiUrl);
  }

  getTutorById(id: number): Observable<Tutor> {
    return this.http.get<Tutor>(`${this.apiUrl}/${id}`);
  }

  /** Crée un tuteur et retourne le tuteur sauvegardé (avec son id). */
  createTutor(tutor: Tutor): Observable<Tutor> {
    return this.http.post<Tutor>(this.apiUrl, tutor);
  }
}
