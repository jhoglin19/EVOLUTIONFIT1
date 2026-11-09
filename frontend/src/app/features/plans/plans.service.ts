import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import { Page } from '../../shared/models/page.model';
import { Plan, PlanRequest } from '../../shared/models/plan.model';

@Injectable({ providedIn: 'root' })
export class PlansService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/plans`;

  list(query: string, active: boolean | null, page: number, size: number): Observable<Page<Plan>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    if (active !== null) {
      params = params.set('active', active);
    }
    return this.http.get<Page<Plan>>(this.url, { params });
  }

  create(request: PlanRequest): Observable<Plan> {
    return this.http.post<Plan>(this.url, request);
  }

  update(id: number, request: PlanRequest): Observable<Plan> {
    return this.http.put<Plan>(`${this.url}/${id}`, request);
  }

  setActive(id: number, active: boolean): Observable<Plan> {
    return this.http.patch<Plan>(`${this.url}/${id}/status`, { active });
  }
}
