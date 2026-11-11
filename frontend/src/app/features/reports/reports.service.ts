import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import {
  DiscountsReport,
  ExpirationsReport,
  IncomeGrouping,
  IncomeReport,
  NewClientsReport,
  PlanSalesReport,
  ReportName,
  VoidedPaymentsReport,
} from '../../shared/models/report.model';

/** Reportes del ADMIN. Fechas yyyy-MM-dd en hora de Bogotá, con los dos extremos incluidos. */
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/reports`;

  income(from: string, to: string, groupBy: IncomeGrouping): Observable<IncomeReport> {
    return this.http.get<IncomeReport>(`${this.url}/income`, { params: this.params(from, to, groupBy) });
  }

  newClients(from: string, to: string): Observable<NewClientsReport> {
    return this.http.get<NewClientsReport>(`${this.url}/new-clients`, { params: this.params(from, to) });
  }

  plans(from: string, to: string): Observable<PlanSalesReport> {
    return this.http.get<PlanSalesReport>(`${this.url}/plans`, { params: this.params(from, to) });
  }

  voidedPayments(from: string, to: string): Observable<VoidedPaymentsReport> {
    return this.http.get<VoidedPaymentsReport>(`${this.url}/voided-payments`, { params: this.params(from, to) });
  }

  discounts(from: string, to: string): Observable<DiscountsReport> {
    return this.http.get<DiscountsReport>(`${this.url}/discounts`, { params: this.params(from, to) });
  }

  expirations(from: string, to: string): Observable<ExpirationsReport> {
    return this.http.get<ExpirationsReport>(`${this.url}/expirations`, { params: this.params(from, to) });
  }

  /** CSV (UTF-8 con BOM, separador ;). La descarga va con el token de sesión. */
  export(report: ReportName, from: string, to: string, groupBy?: IncomeGrouping): Observable<Blob> {
    return this.http.get(`${this.url}/${report}/export`, {
      params: this.params(from, to, groupBy),
      responseType: 'blob',
    });
  }

  private params(from: string, to: string, groupBy?: IncomeGrouping): HttpParams {
    let params = new HttpParams().set('from', from).set('to', to);
    if (groupBy) {
      params = params.set('groupBy', groupBy);
    }
    return params;
  }
}
