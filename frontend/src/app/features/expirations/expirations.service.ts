import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import { ExpirationAlert, ExpirationAlertKind } from '../../shared/models/dashboard.model';
import { Page } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class ExpirationsService {
  private readonly http = inject(HttpClient);

  list(kind: ExpirationAlertKind, query: string, page: number, size: number): Observable<Page<ExpirationAlert>> {
    let params = new HttpParams().set('kind', kind).set('page', page).set('size', size);
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http.get<Page<ExpirationAlert>>(`${API_URL}/alerts/expirations`, { params });
  }
}
