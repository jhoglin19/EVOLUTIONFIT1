import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import { Client, ClientRequest } from '../../shared/models/client.model';
import { Page } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class ClientsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/clients`;

  list(query: string, active: boolean | null, page: number, size: number): Observable<Page<Client>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    if (active !== null) {
      params = params.set('active', active);
    }
    return this.http.get<Page<Client>>(this.url, { params });
  }

  create(request: ClientRequest): Observable<Client> {
    return this.http.post<Client>(this.url, request);
  }

  update(id: number, request: ClientRequest): Observable<Client> {
    return this.http.put<Client>(`${this.url}/${id}`, request);
  }

  setActive(id: number, active: boolean): Observable<Client> {
    return this.http.patch<Client>(`${this.url}/${id}/status`, { active });
  }
}
