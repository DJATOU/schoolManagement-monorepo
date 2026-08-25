import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { SessionSeries } from '../models/sessionSerie/sessionSerie';

@Injectable({
  providedIn: 'root'
})
export class SeriesService {
    private apiUrl = `${API_BASE_URL}/api/series`;

    constructor(private http: HttpClient) {}
  
    getSeriesByGroupId(groupId: number): Observable<SessionSeries[]> {
      return this.http.get<SessionSeries[]>(`${this.apiUrl}/group/${groupId}`);
    }

    /** Récupère une série par son identifiant. */
    getSeriesById(seriesId: number): Observable<SessionSeries> {
      return this.http.get<SessionSeries>(`${this.apiUrl}/${seriesId}`);
    }

    /**
     * Toutes les séries, tous groupes confondus.
     *
     * <p>À n'utiliser que pour peupler un filtre sans groupe sélectionné : l'endpoint
     * renvoie l'entité brute, qui n'expose pas de {@code groupId} exploitable pour un
     * filtrage local.</p>
     */
    getAllSessionSeries(): Observable<SessionSeries[]> {
      return this.http.get<SessionSeries[]>(this.apiUrl);
    }
  
    createSeries(series: Partial<SessionSeries>): Observable<SessionSeries> {
      return this.http.post<SessionSeries>(this.apiUrl, series);
    }

    /**
     * Renomme une série.
     *
     * <p>Cible le point d'entrée dédié `PATCH /{id}/name` et non le `PATCH /{id}` générique :
     * ce dernier applique au serveur une projection de champs arbitraires sur l'entité, bien
     * au-delà du nom.</p>
     *
     * <p>Le serveur refuse un nom vide, et refuse toute modification sur une série rattachée
     * à une année scolaire passée (historique en lecture seule).</p>
     */
    renameSeries(seriesId: number, name: string): Observable<SessionSeries> {
      return this.http.patch<SessionSeries>(`${this.apiUrl}/${seriesId}/name`, { name });
    }

    
  getSessionSeriesByGroupId(groupId: number): Observable<SessionSeries[]> {
    return this.http.get<SessionSeries[]>(`${this.apiUrl}/group/${groupId}`);
  }
}
