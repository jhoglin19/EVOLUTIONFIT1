import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, finalize } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { ReasonDialog, ReasonDialogData } from '../../../shared/components/reason-dialog/reason-dialog';
import {
  CLOSING_STATUS_LABELS,
  CashClosing,
  CashClosingStatus,
  WITHDRAWAL_CATEGORIES,
  Withdrawal,
  WithdrawalCategory,
} from '../../../shared/models/cash.model';
import { todayInGym } from '../../../shared/utils/dates';
import { CashService } from '../cash.service';
import { ClosingDetailDialog } from '../closing-detail-dialog/closing-detail-dialog';
import { CountDialog, CountDialogData } from '../count-dialog/count-dialog';

/** Historial de cierres y todos los retiros (solo ADMIN; el backend lo exige). */
@Component({
  selector: 'app-cash-closings',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
    MatTooltipModule,
  ],
  templateUrl: './cash-closings.html',
  styleUrl: './cash-closings.scss',
})
export class CashClosingsPage {
  private readonly api = inject(CashService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly statusLabels = CLOSING_STATUS_LABELS;
  readonly statuses = Object.entries(CLOSING_STATUS_LABELS) as [CashClosingStatus, string][];
  readonly categories = WITHDRAWAL_CATEGORIES;
  readonly columns = ['cashDate', 'status', 'expected', 'counted', 'difference', 'note', 'actions'];
  readonly withdrawalColumns = ['occurredAt', 'cashDate', 'category', 'concept', 'amount', 'registeredBy', 'state'];

  readonly rows = signal<CashClosing[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(31);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly withdrawals = signal<Withdrawal[]>([]);

  readonly filters = new FormGroup({
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
    status: new FormControl<CashClosingStatus | null>(null),
  });

  readonly withdrawalFilters = new FormGroup({
    from: new FormControl(todayInGym().slice(0, 8) + '01', { nonNullable: true }),
    to: new FormControl(todayInGym(), { nonNullable: true }),
    category: new FormControl<WithdrawalCategory | null>(null),
  });

  constructor() {
    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
    this.withdrawalFilters.valueChanges
      .pipe(debounceTime(300), takeUntilDestroyed())
      .subscribe(() => this.loadWithdrawals());
    this.load();
    this.loadWithdrawals();
  }

  load(): void {
    const f = this.filters.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.api
      .closings({ from: f.from || null, to: f.to || null, status: f.status }, this.pageIndex(), this.pageSize())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  loadWithdrawals(): void {
    const f = this.withdrawalFilters.getRawValue();
    this.api.withdrawals(f.from || null, f.to || null, f.category).subscribe({
      next: (list) => this.withdrawals.set(list),
      error: (err) => this.notify(errorMessage(err)),
    });
  }

  statusName(status: CashClosingStatus): string {
    return CLOSING_STATUS_LABELS[status];
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  open(c: CashClosing): void {
    this.dialog.open(ClosingDetailDialog, { data: c.id, autoFocus: false });
  }

  count(c: CashClosing): void {
    this.dialog
      .open<CountDialog, CountDialogData>(CountDialog, { data: { closingId: c.id }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.notify('Arqueo registrado');
          this.load();
        }
      });
  }

  review(c: CashClosing): void {
    this.ask('Revisar diferencia', `Diferencia de ${c.difference} el ${c.cashDate}. Deja una nota de la revisión.`, 'Marcar revisada', (note) =>
      this.api.review(c.id, note),
    'Diferencia revisada');
  }

  reopen(c: CashClosing): void {
    this.ask(
      'Reabrir arqueo',
      'El arqueo queda sin registrar para volver a contarlo. El efectivo esperado del cierre no cambia.',
      'Reabrir arqueo',
      (reason) => this.api.reopenCount(c.id, reason),
      'Arqueo reabierto',
    );
  }

  private ask(title: string, message: string, confirmLabel: string,
              action: (text: string) => ReturnType<CashService['review']>, done: string): void {
    this.dialog
      .open<ReasonDialog, ReasonDialogData, string>(ReasonDialog, {
        data: { title, message, confirmLabel },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((text) => {
        if (text) {
          action(text).subscribe({
            next: () => {
              this.notify(done);
              this.load();
            },
            error: (err) => this.notify(errorMessage(err)),
          });
        }
      });
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'Cerrar', { duration: 4000 });
  }
}
