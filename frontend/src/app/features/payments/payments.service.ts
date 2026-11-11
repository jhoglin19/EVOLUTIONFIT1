import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import { Page } from '../../shared/models/page.model';
import { EnrollmentRequest, Payment, PaymentFilters, PaymentRequest } from '../../shared/models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/payments`;

  /** Cliente (nuevo o existente) + plan + pago total, en una sola transacción. */
  enroll(request: EnrollmentRequest): Observable<Payment> {
    return this.http.post<Payment>(`${API_URL}/enrollments`, request);
  }

  /** Cobrar una membresía pendiente existente. */
  pay(membershipId: number, request: PaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(`${API_URL}/memberships/${membershipId}/payments`, request);
  }

  list(filters: PaymentFilters, page: number, size: number): Observable<Page<Payment>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    if (filters.method) {
      params = params.set('method', filters.method);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.query.trim()) {
      params = params.set('q', filters.query.trim());
    }
    return this.http.get<Page<Payment>>(this.url, { params });
  }

  clientPayments(clientId: number): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${API_URL}/clients/${clientId}/payments`);
  }

  voidPayment(id: number, reason: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.url}/${id}/void`, { reason });
  }

  receipt(id: number): Observable<Blob> {
    return this.http.get(`${this.url}/${id}/receipt.pdf`, { responseType: 'blob' });
  }
}
