import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { errorMessage } from '../../../core/utils/error-message';
import { ReasonDialog, ReasonDialogData } from '../../../shared/components/reason-dialog/reason-dialog';
import {
  CashClosing,
  CashMovement,
  CashToday,
  MOVEMENT_LABELS,
  MovementType,
  categoryLabel,
} from '../../../shared/models/cash.model';
import { PaymentMethod, methodLabel } from '../../../shared/models/payment.model';
import { CashService } from '../cash.service';
import { CountDialog, CountDialogData } from '../count-dialog/count-dialog';
import { WithdrawalDialog, WithdrawalDialogData } from '../withdrawal-dialog/withdrawal-dialog';

/** Caja del día en curso (recepción y ADMIN) y arqueos pendientes. */
@Component({
  selector: 'app-cash-today',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './cash-today.html',
  styleUrl: './cash-today.scss',
})
export class CashTodayPage {
  private readonly api = inject(CashService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Solo comodidad visual: el backend exige ADMIN para anular retiros. */
  readonly isAdmin = inject(AuthService).hasRole('ADMIN');
  readonly columns = ['at', 'type', 'description', 'method', 'amount', 'cash', 'actions'];

  readonly today = signal<CashToday | null>(null);
  readonly pending = signal<CashClosing[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ today: this.api.today(), pending: this.api.pendingCounts() })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ today, pending }) => {
          this.today.set(today);
          this.pending.set(pending);
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  typeName(type: MovementType): string {
    return MOVEMENT_LABELS[type];
  }

  methodName(method: PaymentMethod): string {
    return methodLabel(method);
  }

  detail(m: CashMovement): string {
    if (m.type === 'WITHDRAWAL') {
      return `${categoryLabel(m.category)} · ${m.description ?? ''}`;
    }
    return [m.receiptNumber, m.description].filter(Boolean).join(' · ');
  }

  withdraw(): void {
    const available = this.today()?.summary?.expectedCash ?? 0;
    this.dialog
      .open<WithdrawalDialog, WithdrawalDialogData>(WithdrawalDialog, { data: { available }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((w) => {
        if (w) {
          this.notify('Retiro registrado');
          this.load();
        }
      });
  }

  count(closing: CashClosing): void {
    this.dialog
      .open<CountDialog, CountDialogData>(CountDialog, { data: { closingId: closing.id }, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.notify(result.status === 'COUNTED' ? 'Arqueo registrado: cuadra' : 'Arqueo registrado con diferencia');
          this.load();
        }
      });
  }

  voidWithdrawal(m: CashMovement): void {
    this.dialog
      .open<ReasonDialog, ReasonDialogData, string>(ReasonDialog, {
        data: {
          title: 'Anular retiro',
          message: 'El retiro no se borra: queda anulado con el motivo y deja de restar del efectivo. Solo antes del corte.',
          confirmLabel: 'Anular retiro',
        },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((reason) => {
        if (reason) {
          this.api.voidWithdrawal(m.id, reason).subscribe({
            next: () => {
              this.notify('Retiro anulado');
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
