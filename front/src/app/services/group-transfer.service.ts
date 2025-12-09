import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../app.config';
import { GroupTransfer, TransferImpactCalculation } from '../models/transfer/group-transfer';

@Injectable({
  providedIn: 'root'
})
export class GroupTransferService {
  private apiUrl = `${API_BASE_URL}/api/transfers`;

  constructor(private http: HttpClient) {}

  calculateTransferImpact(studentId: number, fromGroupId: number, toGroupId: number): Observable<TransferImpactCalculation> {
    return this.http.get<TransferImpactCalculation>(`${this.apiUrl}/calculate-impact`, {
      params: {
        studentId: studentId.toString(),
        fromGroupId: fromGroupId.toString(),
        toGroupId: toGroupId.toString()
      }
    });
  }

  transferStudent(transfer: Partial<GroupTransfer>): Observable<GroupTransfer> {
    return this.http.post<GroupTransfer>(this.apiUrl, transfer);
  }

  getStudentTransfers(studentId: number): Observable<GroupTransfer[]> {
    return this.http.get<GroupTransfer[]>(`${this.apiUrl}/student/${studentId}`);
  }

  cancelTransfer(transferId: number): Observable<GroupTransfer> {
    return this.http.patch<GroupTransfer>(`${this.apiUrl}/${transferId}/cancel`, {});
  }
}
