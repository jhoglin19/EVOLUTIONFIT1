import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AlertCountService } from '../../core/services/alert-count.service';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/utils/error-message';
import { AdminDashboard, Dashboard as DashboardData, daysLabel } from '../../shared/models/dashboard.model';
import { PAYMENT_STATUS_LABELS, methodLabel } from '../../shared/models/payment.model';
import { roleLabel } from '../../shared/utils/role-label';
import { MethodTotals } from '../cash/method-totals/method-totals';
import { AlertActions } from '../expirations/alert-actions';
import { DashboardService } from './dashboard.service';

/**
 * Inicio: lo que recepción y el ADMIN necesitan ver cada día. "Hoy" es el día calendario de Bogotá; la caja
 * tiene su propio día, con corte a las 10 p.m. Los ingresos solo se piden (y solo los entrega el backend)
 * al ADMIN.
 */
@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule, RouterLink, MethodTotals,
    AlertActions],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly auth = inject(AuthService);
  private readonly api = inject(DashboardService);
  private readonly alertCount = inject(AlertCountService);

  readonly user = this.auth.user;
  readonly isAdmin = this.auth.isAdmin;
  protected readonly roleLabel = roleLabel;
  protected readonly daysLabel = daysLabel;
  protected readonly methodName = methodLabel;
  protected readonly statusName = PAYMENT_STATUS_LABELS;

  readonly data = signal<DashboardData | null>(null);
  readonly income = signal<AdminDashboard | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .today()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (d) => {
          this.data.set(d);
          this.alertCount.set({
            expiring: d.expiringCount,
            recentlyExpired: d.recentlyExpiredCount,
            warningDays: d.warningDays,
            recentlyExpiredDays: d.recentlyExpiredDays,
          });
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
    if (this.isAdmin()) {
      this.api.admin().subscribe({
        next: (a) => this.income.set(a),
        error: (err) => this.error.set(errorMessage(err)),
      });
    }
  }
}
