import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../app.config';
import { CatchUpRequest } from '../models/catchUp/catch-up-request';
import { Session } from '../models/session/session';

@Injectable({
  providedIn: 'root'
})
export class CatchUpService {
  private apiUrl = `${API_BASE_URL}/api/catch-ups`;

  constructor(private http: HttpClient) {}

  createCatchUpRequest(request: Partial<CatchUpRequest>): Observable<CatchUpRequest> {
    return this.http.post<CatchUpRequest>(this.apiUrl, request);
  }

  getPendingRequests(): Observable<CatchUpRequest[]> {
    return this.http.get<CatchUpRequest[]>(`${this.apiUrl}/pending`);
  }

  getRequestsByStudent(studentId: number): Observable<CatchUpRequest[]> {
    return this.http.get<CatchUpRequest[]>(`${this.apiUrl}/student/${studentId}`);
  }

  getAvailableSessions(studentId: number, originalSessionId: number): Observable<Session[]> {
    return this.http.get<Session[]>(`${this.apiUrl}/available-sessions`, {
      params: { studentId: studentId.toString(), originalSessionId: originalSessionId.toString() }
    });
  }

  scheduleCatchUp(requestId: number, catchUpSessionId: number, catchUpGroupId: number): Observable<CatchUpRequest> {
    return this.http.patch<CatchUpRequest>(`${this.apiUrl}/${requestId}/schedule`, {
      catchUpSessionId,
      catchUpGroupId
    });
  }

  completeCatchUp(requestId: number): Observable<CatchUpRequest> {
    return this.http.patch<CatchUpRequest>(`${this.apiUrl}/${requestId}/complete`, {});
  }

  cancelCatchUp(requestId: number, reason?: string): Observable<CatchUpRequest> {
    return this.http.patch<CatchUpRequest>(`${this.apiUrl}/${requestId}/cancel`, { reason });
  }
}
