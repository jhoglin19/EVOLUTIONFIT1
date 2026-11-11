import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';

import { errorMessage } from '../../core/utils/error-message';
import { MAX_LOGO_BYTES, ReceiptSettings } from '../../shared/models/receipt-settings.model';
import { openPdf } from '../../shared/utils/pdf';
import { ReceiptSettingsService } from './receipt-settings.service';

/** Datos del encabezado del comprobante, prefijo, número inicial y logo. Solo ADMIN (lo exige el backend). */
@Component({
  selector: 'app-receipt-settings',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './receipt-settings.html',
  styleUrl: './receipt-settings.scss',
})
export class ReceiptSettingsPage {
  private readonly api = inject(ReceiptSettingsService);
  private readonly snackBar = inject(MatSnackBar);

  readonly maxLogoKb = MAX_LOGO_BYTES / 1024;
  readonly settings = signal<ReceiptSettings | null>(null);
  readonly logoUrl = signal<string | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly logoError = signal<string | null>(null);

  readonly form = new FormGroup({
    tradeName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(150)] }),
    legalName: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(150)] }),
    taxId: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(30)] }),
    commercialRegistration: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(50)] }),
    address: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] }),
    city: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(80)] }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(50)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email, Validators.maxLength(150)] }),
    receiptPrefix: new FormControl('EF-', {
      nonNullable: true,
      validators: [Validators.pattern(/^[A-Za-z0-9-]{0,10}$/)],
    }),
    initialNumber: new FormControl<number | null>(1, {
      validators: [Validators.required, Validators.min(1), Validators.max(999999), Validators.pattern(/^\d+$/)],
    }),
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api
      .get()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({ next: (s) => this.apply(s), error: (err) => this.error.set(errorMessage(err)) });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const clean = (value: string) => value.trim() || null;
    this.saving.set(true);
    this.error.set(null);
    this.api
      .update({
        tradeName: clean(v.tradeName),
        legalName: clean(v.legalName),
        taxId: clean(v.taxId),
        commercialRegistration: clean(v.commercialRegistration),
        address: clean(v.address),
        city: clean(v.city),
        phone: clean(v.phone),
        email: clean(v.email),
        receiptPrefix: v.receiptPrefix.trim(),
        initialNumber: this.settings()?.initialNumberLocked ? null : v.initialNumber,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (s) => {
          this.apply(s);
          this.snackBar.open('Datos del comprobante guardados', 'Cerrar', { duration: 4000 });
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.logoError.set(null);
    if (file.size > MAX_LOGO_BYTES) {
      this.logoError.set(
        `El logo supera el tamaño máximo permitido de ${this.maxLogoKb} KB (el archivo pesa ${Math.ceil(file.size / 1024)} KB).`,
      );
      return;
    }
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      this.logoError.set('El logo debe ser una imagen PNG o JPG.');
      return;
    }
    this.api.uploadLogo(file).subscribe({
      next: (s) => this.apply(s),
      error: (err) => this.logoError.set(errorMessage(err)),
    });
  }

  removeLogo(): void {
    this.api.deleteLogo().subscribe({ next: (s) => this.apply(s), error: (err) => this.logoError.set(errorMessage(err)) });
  }

  preview(): void {
    this.api.preview().subscribe({
      next: (blob) => openPdf(blob, 'vista-previa-comprobante.pdf'),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  private apply(s: ReceiptSettings): void {
    this.settings.set(s);
    this.form.reset({
      tradeName: s.tradeName ?? '',
      legalName: s.legalName ?? '',
      taxId: s.taxId ?? '',
      commercialRegistration: s.commercialRegistration ?? '',
      address: s.address ?? '',
      city: s.city ?? '',
      phone: s.phone ?? '',
      email: s.email ?? '',
      receiptPrefix: s.receiptPrefix,
      initialNumber: s.nextNumber,
    });
    if (s.initialNumberLocked) {
      this.form.controls.initialNumber.disable();
    } else {
      this.form.controls.initialNumber.enable();
    }
    this.loadLogo(s.hasLogo);
  }

  private loadLogo(hasLogo: boolean): void {
    const previous = this.logoUrl();
    if (previous) {
      URL.revokeObjectURL(previous);
    }
    this.logoUrl.set(null);
    if (hasLogo) {
      this.api.logo().subscribe({ next: (blob) => this.logoUrl.set(URL.createObjectURL(blob)) });
    }
  }
}
