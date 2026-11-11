import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import {
  CashClosing,
  CountPreview,
  MOVEMENT_LABELS,
  categoryLabel,
  countDifference,
} from '../../../shared/models/cash.model';
import { methodLabel } from '../../../shared/models/payment.model';
import { CashService } from '../cash.service';

export interface CountDialogData {
  closingId: number;
}

/**
 * Arqueo de un día cerrado. El esperado congelado no cambia; se compara lo contado con el esperado
 * ajustado por el efectivo que entró o salió después del corte (ya está, o ya no está, en la caja).
 */
@Component({
  selector: 'app-count-dialog',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
  ],
  templateUrl: './count-dialog.html',
  styleUrl: './count-dialog.scss',
})
export class CountDialog {
  readonly data = inject<CountDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<CountDialog, CashClosing>);
  private readonly api = inject(CashService);

  readonly preview = signal<CountPreview | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly counted = new FormControl<number | null>(null, [Validators.required, Validators.min(0)]);
  readonly note = new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) });
  private readonly countedValue = toSignal(this.counted.valueChanges, { initialValue: null });

  /** Diferencia en vivo; null mientras no se digite el contado. */
  readonly difference = computed(() => {
    const p = this.preview();
    const value = this.countedValue();
    return p && value !== null && value !== undefined && `${value}` !== ''
      ? countDifference(Number(value), p.expectedAdjustedCash)
      : null;
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api
      .countPreview(this.data.closingId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => this.preview.set(p),
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  movementLabel(type: keyof typeof MOVEMENT_LABELS): string {
    return MOVEMENT_LABELS[type];
  }

  methodName = methodLabel;
  categoryName = categoryLabel;

  save(): void {
    const p = this.preview();
    const diff = this.difference();
    if (!p || this.counted.invalid || diff === null) {
      this.counted.markAsTouched();
      return;
    }
    const note = this.note.value.trim();
    if (diff !== 0 && !note) {
      this.note.setErrors({ required: true });
      this.note.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .count(this.data.closingId, {
        countedCash: Number(this.counted.value),
        note: note || null,
        expectedAdjustedCash: p.expectedAdjustedCash,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (closing) => this.dialogRef.close(closing),
        error: (err) => {
          this.error.set(errorMessage(err));
          if (err instanceof HttpErrorResponse && err.status === 409) {
            this.load(); // hubo movimientos nuevos: se muestra el esperado actualizado
          }
        },
      });
  }
}
