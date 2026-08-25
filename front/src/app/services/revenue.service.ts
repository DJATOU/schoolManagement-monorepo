import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-base-url';
import { GroupRevenue } from '../models/revenue/group-revenue';
import { RevenueFilters, RevenueReport } from '../models/revenue/revenue-report';

/**
 * Lecture des encaissements.
 *
 * <p>Les endpoints sont réservés au rôle ADMIN côté serveur ({@code SecurityConfig}) :
 * un VIEWER reçoit un 403, le masquage dans l'interface n'est qu'un confort.</p>
 */
@Injectable({ providedIn: 'root' })
export class RevenueService {

  private readonly baseUrl = `${API_BASE_URL}/api/groups`;

  constructor(private http: HttpClient) {}

  /** Relevé d'encaissements d'un groupe (total, par série, par séance, par mois). */
  getGroupRevenue(groupId: number): Observable<GroupRevenue> {
    return this.http.get<GroupRevenue>(`${this.baseUrl}/${groupId}/revenue`);
  }

  /**
   * Rapport de recettes transversal, agrégé côté serveur sur l'axe demandé.
   *
   * @param filters axe et filtres du périmètre
   * @param lang    langue utilisée pour les libellés de mois
   */
  getReport(filters: RevenueFilters, lang: string): Observable<RevenueReport> {
    let params = new HttpParams()
      .set('groupBy', filters.groupBy)
      .set('lang', lang);

    const optional: Record<string, unknown> = {
      groupId: filters.groupId,
      levelId: filters.levelId,
      seriesId: filters.seriesId,
      schoolYearId: filters.schoolYearId
    };
    Object.entries(optional).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    });

    if (filters.dateFrom) {
      params = params.set('dateFrom', this.isoDate(filters.dateFrom));
    }
    if (filters.dateTo) {
      params = params.set('dateTo', this.isoDate(filters.dateTo));
    }

    return this.http.get<RevenueReport>(`${API_BASE_URL}/api/revenue`, { params });
  }

  /** Date au format attendu par le backend (yyyy-MM-dd), sans décalage de fuseau. */
  private isoDate(date: Date): string {
    const value = new Date(date);
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${value.getFullYear()}-${month}-${day}`;
  }
}
