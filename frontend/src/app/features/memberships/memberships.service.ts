import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import {
  CreateFreezeRequest,
  CreateMembershipRequest,
  CurrentMembership,
  ExtendFreezeRequest,
  Membership,
  MembershipPreview,
  MembershipStatus,
  MigratedMembershipRequest,
} from '../../shared/models/membership.model';
import { Page } from '../../shared/models/page.model';

export interface MembershipFilters {
  state: MembershipStatus | null;
  expiringSoon: boolean;
  query: string;
}

@Injectable({ providedIn: 'root' })
export class MembershipsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/memberships`;

  list(filters: MembershipFilters, page: number, size: number): Observable<Page<Membership>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.state) {
      params = params.set('state', filters.state);
    }
    if (filters.expiringSoon) {
      params = params.set('expiringSoon', true);
    }
    if (filters.query.trim()) {
      params = params.set('q', filters.query.trim());
    }
    return this.http.get<Page<Membership>>(this.url, { params });
  }

  history(clientId: number): Observable<Membership[]> {
    return this.http.get<Membership[]>(`${API_URL}/clients/${clientId}/memberships`);
  }

  current(clientId: number): Observable<CurrentMembership> {
    return this.http.get<CurrentMembership>(`${API_URL}/clients/${clientId}/memberships/current`);
  }

  preview(clientId: number, planId: number, startDate: string | null): Observable<MembershipPreview> {
    let params = new HttpParams().set('clientId', clientId).set('planId', planId);
    if (startDate) {
      params = params.set('startDate', startDate);
    }
    return this.http.get<MembershipPreview>(`${this.url}/preview`, { params });
  }

  create(request: CreateMembershipRequest): Observable<Membership> {
    return this.http.post<Membership>(this.url, request);
  }

  createMigrated(request: MigratedMembershipRequest): Observable<Membership> {
    return this.http.post<Membership>(`${this.url}/migrated`, request);
  }

  cancel(id: number, reason: string): Observable<Membership> {
    return this.http.post<Membership>(`${this.url}/${id}/cancel`, { reason });
  }

  freeze(membershipId: number, request: CreateFreezeRequest): Observable<Membership> {
    return this.http.post<Membership>(`${this.url}/${membershipId}/freezes`, request);
  }

  extendFreeze(freezeId: number, request: ExtendFreezeRequest): Observable<Membership> {
    return this.http.patch<Membership>(`${API_URL}/freezes/${freezeId}/extend`, request);
  }

  cancelFreeze(freezeId: number, reason: string): Observable<Membership> {
    return this.http.post<Membership>(`${API_URL}/freezes/${freezeId}/cancel`, { reason });
  }
}
