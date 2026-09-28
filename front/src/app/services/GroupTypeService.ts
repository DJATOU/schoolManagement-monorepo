import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GroupType } from '../models/GroupType/groupType';
import { API_BASE_URL } from '../api-base-url';


@Injectable({
  providedIn: 'root'
})
export class GroupTypeService {
  private apiUrl = `${API_BASE_URL}/api/grouptypes`;

  constructor(private http: HttpClient) { }

  createGroupType(groupType: GroupType): Observable<GroupType> {
    return this.http.post<GroupType>(this.apiUrl, groupType);
  }

  getAllGroupTypes(): Observable<GroupType[]> {
    return this.http.get<GroupType[]>(this.apiUrl);
  }

  /** Récupère un type de groupe par identifiant (mode modification du formulaire). */
  getGroupTypeById(id: number): Observable<GroupType> {
    return this.http.get<GroupType>(`${this.apiUrl}/${id}`);
  }

  /** Met à jour un type de groupe existant. */
  updateGroupType(id: number, groupType: GroupType): Observable<GroupType> {
    return this.http.put<GroupType>(`${this.apiUrl}/${id}`, groupType);
  }

  
  disableGroupType(id_list: Number[]): Observable<boolean> {
    return this.http.delete<boolean>(`${this.apiUrl}/disable/${id_list}`);
  }

}
