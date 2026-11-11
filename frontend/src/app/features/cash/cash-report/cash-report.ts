import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { CLOSING_STATUS_LABELS, MonthlyReport } from '../../../shared/models/cash.model';
import { methodLabel } from '../../../shared/models/payment.model';
import { todayInGym } from '../../../shared/utils/dates';
import { CashService } from '../cash.service';

/** Reporte mensual (solo ADMIN): suma de los cierres diarios del mes. Es solo informe. */
@Component({
  selector: 'app-cash-report',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  templateUrl: './cash-report.html',
  styleUrl: './cash-report.scss',
})
export class CashReportPage {
  private readonly api = inject(CashService);

  readonly month = new FormControl(todayInGym().slice(0, 7), { nonNullable: true });
  readonly report = signal<MonthlyReport | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly statusLabels = CLOSING_STATUS_LABELS;
  readonly methodName = methodLabel;

  constructor() {
    this.month.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.load());
    this.load();
  }

  load(): void {
    if (!/^\d{4}-\d{2}$/.test(this.month.value)) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api
      .monthly(this.month.value)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (r) => this.report.set(r),
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  /** Total recibido menos anulado de todos los métodos. */
  totalNet(r: MonthlyReport): number {
    return r.methods.reduce((sum, m) => sum + m.receivedAmount - m.voidedAmount, 0);
  }
}
