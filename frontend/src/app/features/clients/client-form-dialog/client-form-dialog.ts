import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { Client, DOCUMENT_TYPES } from '../../../shared/models/client.model';
import { MOBILE_ERROR, colombianMobileValidator, normalizeColombianMobile } from '../../../shared/utils/phone';
import { ClientsService } from '../clients.service';

export interface ClientFormData {
  /** Si viene un cliente se edita; si no, se registra uno nuevo. */
  client?: Client;
}

const NUMERIC_DOCUMENT_TYPES = ['CC', 'TI'];

/** Misma normalización que aplica el backend antes de validar y guardar. */
export function normalizeDocumentNumber(value: string): string {
  return value.replace(/[\s.-]/g, '').toUpperCase();
}

function isNumericType(type: string | undefined): boolean {
  return NUMERIC_DOCUMENT_TYPES.includes(type ?? '');
}

function documentFormat(control: AbstractControl<string>): ValidationErrors | null {
  if (!control.value?.trim()) {
    return null; // de eso se ocupa Validators.required
  }
  const pattern = isNumericType(control.parent?.get('documentType')?.value) ? /^[0-9]+$/ : /^[A-Z0-9]+$/;
  return pattern.test(normalizeDocumentNumber(control.value)) ? null : { documentFormat: true };
}

function localToday(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function notFuture(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && control.value > localToday() ? { future: true } : null;
}

@Component({
  selector: 'app-client-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './client-form-dialog.html',
  styleUrl: './client-form-dialog.scss',
})
export class ClientFormDialog {
  readonly mobileError = MOBILE_ERROR;
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(ClientsService);
  private readonly dialogRef = inject(MatDialogRef<ClientFormDialog, Client>);
  private readonly data = inject<ClientFormData>(MAT_DIALOG_DATA);

  readonly editing = !!this.data.client;
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly documentTypes = DOCUMENT_TYPES;
  readonly today = localToday();

  readonly form = this.fb.group({
    documentType: [this.data.client?.documentType ?? 'CC', [Validators.required]],
    documentNumber: [
      this.data.client?.documentNumber ?? '',
      [Validators.required, Validators.maxLength(30), documentFormat],
    ],
    firstName: [this.data.client?.firstName ?? '', [Validators.required, Validators.maxLength(80)]],
    lastName: [this.data.client?.lastName ?? '', [Validators.required, Validators.maxLength(80)]],
    email: [this.data.client?.email ?? '', [Validators.email, Validators.maxLength(150)]],
    // Obligatorio también al editar: un cliente antiguo sin teléfono lo completa aquí.
    phone: [this.data.client?.phone ?? '', [Validators.required, colombianMobileValidator]],
    birthDate: [this.data.client?.birthDate ?? '', [notFuture]],
    address: [this.data.client?.address ?? '', [Validators.maxLength(200)]],
    notes: [this.data.client?.notes ?? '', [Validators.maxLength(2000)]],
  });

  constructor() {
    const { documentType, documentNumber } = this.form.controls;
    documentType.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => documentNumber.updateValueAndValidity());
    // Al construir el grupo el control aún no tenía padre: se valida ahora con el tipo real.
    documentNumber.updateValueAndValidity();
  }

  get documentRequiresDigits(): boolean {
    return isNumericType(this.form.controls.documentType.value);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const request = {
      documentType: value.documentType,
      documentNumber: normalizeDocumentNumber(value.documentNumber),
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      email: value.email.trim() || null,
      phone: normalizeColombianMobile(value.phone) ?? value.phone.trim(),
      birthDate: value.birthDate || null,
      address: value.address.trim() || null,
      notes: value.notes.trim() || null,
    };

    const request$ = this.editing ? this.api.update(this.data.client!.id, request) : this.api.create(request);

    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (client) => this.dialogRef.close(client),
      error: (err) => this.showError(err),
    });
  }

  private showError(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 409) {
      const control = this.form.controls.documentNumber;
      control.setErrors({ conflict: errorMessage(err) });
      control.markAsTouched();
      return;
    }
    this.error.set(errorMessage(err));
  }
}
