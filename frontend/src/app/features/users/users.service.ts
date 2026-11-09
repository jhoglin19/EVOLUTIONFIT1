import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import { Page } from '../../shared/models/page.model';
import { CreateUserRequest, Role, UpdateUserRequest, User } from '../../shared/models/user.model';

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/users`;

  list(query: string, page: number, size: number): Observable<Page<User>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http.get<Page<User>>(this.url, { params });
  }

  create(request: CreateUserRequest): Observable<User> {
    return this.http.post<User>(this.url, request);
  }

  update(id: number, request: UpdateUserRequest): Observable<User> {
    return this.http.put<User>(`${this.url}/${id}`, request);
  }

  setActive(id: number, active: boolean): Observable<User> {
    return this.http.patch<User>(`${this.url}/${id}/status`, { active });
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/password`, { newPassword });
  }

  roles(): Observable<Role[]> {
    return this.http.get<Role[]>(`${API_URL}/roles`);
  }
}
