import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { WITHDRAWAL_CATEGORIES, Withdrawal, WithdrawalCategory } from '../../../shared/models/cash.model';
import { newRequestId } from '../../../shared/utils/request-id';
import { CashService } from '../cash.service';

export interface WithdrawalDialogData {
  /** Efectivo esperado del día en curso: el retiro no puede superarlo (el backend lo vuelve a validar). */
  available: number;
}

/** Retiro de efectivo para gastos del gimnasio. Se cierra con el retiro registrado. */
@Component({
  selector: 'app-withdrawal-dialog',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>Registrar retiro de efectivo</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        <p class="hint">
          Disponible en caja: <b>{{ data.available | currency: 'COP' : 'symbol-narrow' : '1.0-2' }}</b>.
          El retiro baja el efectivo esperado del día.
        </p>
        <mat-form-field appearance="outline">
          <mat-label>Monto</mat-label>
          <input matInput type="number" min="1" step="100" formControlName="amount" />
          @if (form.controls.amount.hasError('required')) {
            <mat-error>El monto es obligatorio</mat-error>
          } @else if (form.controls.amount.hasError('min')) {
            <mat-error>El monto debe ser mayor que cero</mat-error>
          } @else if (form.controls.amount.hasError('max')) {
            <mat-error>Supera el efectivo disponible</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Categoría</mat-label>
          <mat-select formControlName="category">
            @for (c of categories; track c.value) {
              <mat-option [value]="c.value">{{ c.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Concepto</mat-label>
          <input matInput formControlName="concept" maxlength="255" placeholder="Ej.: servilletas" />
          @if (form.controls.concept.invalid) {
            <mat-error>El concepto es obligatorio</mat-error>
          }
        </mat-form-field>
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">Registrar retiro</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    mat-dialog-content {
      display: flex;
      flex-direction: column;
      --dialog-width: 420px;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant);
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class WithdrawalDialog {
  readonly data = inject<WithdrawalDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<WithdrawalDialog, Withdrawal>);
  private readonly api = inject(CashService);

  readonly categories = WITHDRAWAL_CATEGORIES;
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  /** Uno por diálogo: si se reintenta, el backend devuelve el mismo retiro. */
  private readonly requestId = newRequestId();

  readonly form = new FormGroup({
    amount: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(1),
      Validators.max(Math.max(this.data.available, 0)),
    ]),
    category: new FormControl<WithdrawalCategory>('SUPPLIES', { nonNullable: true, validators: Validators.required }),
    concept: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(255), Validators.pattern(/\S/)],
    }),
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.api
      .withdraw({ requestId: this.requestId, amount: v.amount!, category: v.category, concept: v.concept.trim() })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (w) => this.dialogRef.close(w),
        error: (err) => this.error.set(errorMessage(err)),
      });
  }
}
