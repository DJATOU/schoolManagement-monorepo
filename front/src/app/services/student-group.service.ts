import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../app.config';
import { StudentGroup } from '../models/studentGroup/studentGroup';

@Injectable({
  providedIn: 'root'
})
export class StudentGroupService {
  private apiUrl = `${API_BASE_URL}/api/student-groups`;

  constructor(private http: HttpClient) {}

  getStudentGroupHistory(studentId: number): Observable<StudentGroup[]> {
    return this.http.get<StudentGroup[]>(`${this.apiUrl}/student/${studentId}/history`);
  }

  getActiveGroups(studentId: number): Observable<StudentGroup[]> {
    return this.http.get<StudentGroup[]>(`${this.apiUrl}/student/${studentId}/active`);
  }

  addStudentToGroup(studentId: number, groupId: number, enrollmentDate?: Date): Observable<StudentGroup> {
    return this.http.post<StudentGroup>(this.apiUrl, {
      studentId,
      groupId,
      enrollmentDate: enrollmentDate || new Date(),
      isActive: true
    });
  }

  removeStudentFromGroup(
    studentId: number,
    groupId: number,
    exitReason: 'TRANSFER' | 'DROPOUT' | 'COMPLETED' | 'OTHER'
  ): Observable<StudentGroup> {
    return this.http.patch<StudentGroup>(`${this.apiUrl}/${studentId}/groups/${groupId}/exit`, {
      exitDate: new Date(),
      exitReason,
      isActive: false
    });
  }
}
