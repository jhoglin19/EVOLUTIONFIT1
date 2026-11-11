import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import {
  CashClosing,
  CashClosingDetail,
  CashSettings,
  CashSettingsRequest,
  CashToday,
  ClosingFilters,
  CountPreview,
  CountRequest,
  GymCalendar,
  CalendarDay,
  MonthlyReport,
  Withdrawal,
  WithdrawalCategory,
  WithdrawalRequest,
} from '../../shared/models/cash.model';
import { Page } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class CashService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/cash`;

  today(): Observable<CashToday> {
    return this.http.get<CashToday>(`${this.url}/today`);
  }

  withdraw(request: WithdrawalRequest): Observable<Withdrawal> {
    return this.http.post<Withdrawal>(`${this.url}/withdrawals`, request);
  }

  withdrawals(from: string | null, to: string | null, category: WithdrawalCategory | null): Observable<Withdrawal[]> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    if (category) {
      params = params.set('category', category);
    }
    return this.http.get<Withdrawal[]>(`${this.url}/withdrawals`, { params });
  }

  voidWithdrawal(id: number, reason: string): Observable<Withdrawal> {
    return this.http.post<Withdrawal>(`${this.url}/withdrawals/${id}/void`, { reason });
  }

  pendingCounts(): Observable<CashClosing[]> {
    return this.http.get<CashClosing[]>(`${this.url}/closings/pending`);
  }

  countPreview(closingId: number): Observable<CountPreview> {
    return this.http.get<CountPreview>(`${this.url}/closings/${closingId}/count-preview`);
  }

  count(closingId: number, request: CountRequest): Observable<CashClosing> {
    return this.http.post<CashClosing>(`${this.url}/closings/${closingId}/count`, request);
  }

  closings(filters: ClosingFilters, page: number, size: number): Observable<Page<CashClosing>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    return this.http.get<Page<CashClosing>>(`${this.url}/closings`, { params });
  }

  closing(id: number): Observable<CashClosingDetail> {
    return this.http.get<CashClosingDetail>(`${this.url}/closings/${id}`);
  }

  review(id: number, note: string): Observable<CashClosing> {
    return this.http.post<CashClosing>(`${this.url}/closings/${id}/review`, { note });
  }

  reopenCount(id: number, reason: string): Observable<CashClosing> {
    return this.http.post<CashClosing>(`${this.url}/closings/${id}/reopen-count`, { reason });
  }

  monthly(month: string): Observable<MonthlyReport> {
    return this.http.get<MonthlyReport>(`${this.url}/reports/monthly`, { params: { month } });
  }

  settings(): Observable<CashSettings> {
    return this.http.get<CashSettings>(`${this.url}/settings`);
  }

  updateSettings(request: CashSettingsRequest): Observable<CashSettings> {
    return this.http.put<CashSettings>(`${this.url}/settings`, request);
  }

  calendar(year: number): Observable<GymCalendar> {
    return this.http.get<GymCalendar>(`${this.url}/calendar`, { params: { year } });
  }

  setDay(date: string, open: boolean, note: string | null): Observable<CalendarDay> {
    return this.http.put<CalendarDay>(`${this.url}/calendar/${date}`, { open, note });
  }

  clearDay(date: string): Observable<CalendarDay> {
    return this.http.delete<CalendarDay>(`${this.url}/calendar/${date}`);
  }
}
