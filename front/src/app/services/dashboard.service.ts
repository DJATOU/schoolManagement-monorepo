import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { DashboardStats } from '../models/dashboard/dashboard-stats';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private apiUrl = `${API_BASE_URL}/api/dashboard`;

  constructor(private http: HttpClient) {}

  /**
   * Récupère les stats du tableau de bord, éventuellement filtrées par période
   * et par année scolaire (les stats rattachables à une année — groupes, sessions,
   * présences, rattrapages, élèves inscrits — sont alors restreintes à cette année).
   */
  getStats(from?: string, to?: string, schoolYearId?: number | null): Observable<DashboardStats> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (schoolYearId != null) params = params.set('schoolYearId', schoolYearId);
    return this.http.get<DashboardStats>(`${this.apiUrl}/stats`, { params });
  }
}
