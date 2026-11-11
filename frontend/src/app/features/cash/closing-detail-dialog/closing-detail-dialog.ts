import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { errorMessage } from '../../../core/utils/error-message';
import {
  CLOSING_STATUS_LABELS,
  CashClosingDetail,
  CashMovement,
  MOVEMENT_LABELS,
  categoryLabel,
} from '../../../shared/models/cash.model';
import { methodLabel } from '../../../shared/models/payment.model';
import { CashService } from '../cash.service';

/** Detalle de un cierre (ADMIN): lo congelado, el informe por método, los movimientos y el arqueo. */
@Component({
  selector: 'app-closing-detail-dialog',
  imports: [CurrencyPipe, DatePipe, MatDialogModule, MatButtonModule, MatProgressBarModule],
  templateUrl: './closing-detail-dialog.html',
  styleUrl: './closing-detail-dialog.scss',
})
export class ClosingDetailDialog {
  private readonly api = inject(CashService);
  readonly closingId = inject<number>(MAT_DIALOG_DATA);

  readonly detail = signal<CashClosingDetail | null>(null);
  readonly error = signal<string | null>(null);
  readonly statusLabels = CLOSING_STATUS_LABELS;
  readonly methodName = methodLabel;

  constructor() {
    this.api.closing(this.closingId).subscribe({
      next: (d) => this.detail.set(d),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  describe(m: CashMovement): string {
    const what = m.type === 'WITHDRAWAL' ? `${categoryLabel(m.category)} · ${m.description ?? ''}` : [m.receiptNumber, m.description].filter(Boolean).join(' · ');
    return `${MOVEMENT_LABELS[m.type]} · ${what}${m.voided ? ' (anulado)' : ''}`;
  }
}
