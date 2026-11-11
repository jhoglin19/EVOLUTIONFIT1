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
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { debounceTime, finalize } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/utils/error-message';
import { ReasonDialog, ReasonDialogData } from '../../shared/components/reason-dialog/reason-dialog';
import {
  PAYMENT_METHODS,
  PAYMENT_STATUS_LABELS,
  Payment,
  PaymentMethod,
  PaymentStatus,
  methodLabel,
} from '../../shared/models/payment.model';
import { downloadPdf } from '../../shared/utils/pdf';
import { receiptWhatsappUrl } from '../../shared/utils/whatsapp';
import { PaymentsService } from './payments.service';

@Component({
  selector: 'app-payments',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './payments.html',
  styleUrl: './payments.scss',
})
export class Payments {
  private readonly api = inject(PaymentsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  /** Solo comodidad visual: el backend exige ADMIN para anular. */
  readonly isAdmin = this.auth.hasRole('ADMIN');
  readonly methods = PAYMENT_METHODS;
  readonly columns = ['paidAt', 'receipt', 'client', 'plan', 'method', 'amount', 'status', 'actions'];

  readonly rows = signal<Payment[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly filters = new FormGroup({
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
    method: new FormControl<PaymentMethod | null>(null),
    status: new FormControl<PaymentStatus | null>(null),
    query: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
    this.load();
  }

  load(): void {
    const f = this.filters.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(
        { from: f.from || null, to: f.to || null, method: f.method, status: f.status, query: f.query },
        this.pageIndex(),
        this.pageSize(),
      )
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  methodName(method: PaymentMethod): string {
    return methodLabel(method);
  }

  statusName(status: PaymentStatus): string {
    return PAYMENT_STATUS_LABELS[status];
  }

  receipt(payment: Payment): void {
    this.api.receipt(payment.id).subscribe({
      next: (blob) => downloadPdf(blob, `${payment.receiptNumber}.pdf`),
      error: (err) => this.notify(errorMessage(err)),
    });
  }

  /** Solo pagos vigentes con comprobante y cliente con teléfono. */
  whatsappUrl(payment: Payment): string | null {
    return payment.status === 'COMPLETED' ? receiptWhatsappUrl(payment) : null;
  }

  voidPayment(payment: Payment): void {
    this.dialog
      .open<ReasonDialog, ReasonDialogData, string>(ReasonDialog, {
        data: {
          title: `Anular pago ${payment.receiptNumber ?? ''}`.trim(),
          message:
            'El pago no se borra: queda anulado con el motivo y su comprobante se marca como ANULADO. La membresía también se anula con el mismo motivo.',
          confirmLabel: 'Anular pago',
        },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((reason) => {
        if (reason) {
          this.api.voidPayment(payment.id, reason).subscribe({
            next: () => {
              this.notify('Pago anulado');
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
