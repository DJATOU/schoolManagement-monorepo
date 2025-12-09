import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../app.config';
import { PriceCalculation, StudentDiscount } from '../models/discount/student-discount';

@Injectable({
  providedIn: 'root'
})
export class DiscountService {
  private apiUrl = `${API_BASE_URL}/api/discounts`;

  constructor(private http: HttpClient) {}

  getAllDiscounts(): Observable<StudentDiscount[]> {
    return this.http.get<StudentDiscount[]>(this.apiUrl);
  }

  getStudentDiscounts(studentId: number): Observable<StudentDiscount[]> {
    return this.http.get<StudentDiscount[]>(`${this.apiUrl}/student/${studentId}`);
  }

  addDiscount(discount: Partial<StudentDiscount>): Observable<StudentDiscount> {
    return this.http.post<StudentDiscount>(this.apiUrl, discount);
  }

  updateDiscount(id: number, discount: Partial<StudentDiscount>): Observable<StudentDiscount> {
    return this.http.put<StudentDiscount>(`${this.apiUrl}/${id}`, discount);
  }

  deactivateDiscount(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/deactivate`, {});
  }

  calculateFinalPrice(studentId: number, groupId: number, basePrice: number): Observable<PriceCalculation> {
    return this.http.post<PriceCalculation>(`${this.apiUrl}/calculate`, {
      studentId,
      groupId,
      basePrice
    });
  }
}
