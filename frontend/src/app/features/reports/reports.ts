import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, WritableSignal, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { Observable, finalize } from 'rxjs';

import { errorMessage } from '../../core/utils/error-message';
import { summarizeMethods } from '../../shared/models/cash.model';
import { PAYMENT_STATUS_LABELS, methodLabel } from '../../shared/models/payment.model';
import {
  DiscountsReport,
  ExpirationsReport,
  GROUPING_LABELS,
  IncomeGrouping,
  IncomeReport,
  NewClientsReport,
  PlanSalesReport,
  RENEWAL_LABELS,
  ReportName,
  VoidedPaymentsReport,
} from '../../shared/models/report.model';
import { downloadFile } from '../../shared/utils/pdf';
import { MethodTotals } from '../cash/method-totals/method-totals';
import { PRESETS, Preset, presetRange } from './report-range';
import { ReportsService } from './reports.service';

interface Tab {
  name: ReportName;
  label: string;
}

/**
 * Reportes del ADMIN con filtro de fechas (días calendario de Bogotá, ambos incluidos) y exportación CSV.
 * El backend exige el rol ADMIN; el guard de la ruta es solo comodidad.
 */
@Component({
  selector: 'app-reports',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTabsModule,
    MethodTotals,
  ],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class Reports {
  private readonly api = inject(ReportsService);

  readonly tabs: Tab[] = [
    { name: 'income', label: 'Ingresos' },
    { name: 'expirations', label: 'Vencimientos' },
    { name: 'new-clients', label: 'Clientes nuevos' },
    { name: 'plans', label: 'Planes más vendidos' },
    { name: 'voided-payments', label: 'Pagos anulados' },
    { name: 'discounts', label: 'Descuentos' },
  ];
  readonly presets = PRESETS;
  readonly groupings = Object.entries(GROUPING_LABELS) as [IncomeGrouping, string][];
  readonly groupingLabel = GROUPING_LABELS;
  readonly renewalLabel = RENEWAL_LABELS;
  readonly statusLabel = PAYMENT_STATUS_LABELS;
  readonly methodName = methodLabel;

  readonly range = new FormGroup({
    from: new FormControl(presetRange('month').from, { nonNullable: true }),
    to: new FormControl(presetRange('month').to, { nonNullable: true }),
  });
  readonly grouping = new FormControl<IncomeGrouping>('DAY', { nonNullable: true });

  readonly active = signal<ReportName>('income');
  readonly loading = signal(false);
  readonly exporting = signal(false);
  readonly error = signal<string | null>(null);

  readonly income = signal<IncomeReport | null>(null);
  readonly expirations = signal<ExpirationsReport | null>(null);
  readonly newClients = signal<NewClientsReport | null>(null);
  readonly plans = signal<PlanSalesReport | null>(null);
  readonly voided = signal<VoidedPaymentsReport | null>(null);
  readonly discounts = signal<DiscountsReport | null>(null);

  readonly incomeTotals = computed(() => {
    const r = this.income();
    return r ? summarizeMethods(r.methods) : null;
  });

  constructor() {
    this.load();
  }

  applyPreset(preset: Preset): void {
    this.range.setValue(presetRange(preset));
    this.load();
  }

  tabChanged(index: number): void {
    this.active.set(this.tabs[index].name);
    this.load();
  }

  validRange(): boolean {
    const { from, to } = this.range.getRawValue();
    return !!from && !!to && from <= to;
  }

  load(): void {
    if (!this.validRange()) {
      this.error.set('Revisa las fechas: la final no puede ser anterior a la inicial.');
      return;
    }
    const { from, to } = this.range.getRawValue();
    this.error.set(null);
    switch (this.active()) {
      case 'income':
        return this.fetch(this.api.income(from, to, this.grouping.value), this.income);
      case 'expirations':
        return this.fetch(this.api.expirations(from, to), this.expirations);
      case 'new-clients':
        return this.fetch(this.api.newClients(from, to), this.newClients);
      case 'plans':
        return this.fetch(this.api.plans(from, to), this.plans);
      case 'voided-payments':
        return this.fetch(this.api.voidedPayments(from, to), this.voided);
      case 'discounts':
        return this.fetch(this.api.discounts(from, to), this.discounts);
    }
  }

  export(): void {
    if (!this.validRange()) {
      return;
    }
    const { from, to } = this.range.getRawValue();
    const report = this.active();
    this.exporting.set(true);
    this.api
      .export(report, from, to, report === 'income' ? this.grouping.value : undefined)
      .pipe(finalize(() => this.exporting.set(false)))
      .subscribe({
        next: (blob) => downloadFile(blob, `${report}_${from}_${to}.csv`),
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  private fetch<T>(source: Observable<T>, target: WritableSignal<T | null>): void {
    this.loading.set(true);
    source.pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (r) => target.set(r),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
