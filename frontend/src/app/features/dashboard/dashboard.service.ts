import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import { AdminDashboard, Dashboard } from '../../shared/models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  /** Datos operativos del día (recepción y ADMIN). */
  today(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${API_URL}/dashboard`);
  }

  /** Ingresos de hoy y del mes (solo ADMIN; el backend lo exige). */
  admin(): Observable<AdminDashboard> {
    return this.http.get<AdminDashboard>(`${API_URL}/dashboard/admin`);
  }
}
