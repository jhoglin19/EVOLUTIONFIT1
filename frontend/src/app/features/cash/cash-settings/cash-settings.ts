import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { CashSettings, DAYS, DayOfWeek } from '../../../shared/models/cash.model';
import { todayInGym } from '../../../shared/utils/dates';
import { CashService } from '../cash.service';

/** Configuración de caja (solo ADMIN): base, fecha de inicio y días que abre. */
@Component({
  selector: 'app-cash-settings',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './cash-settings.html',
  styleUrl: './cash-settings.scss',
})
export class CashSettingsPage {
  private readonly api = inject(CashService);
  private readonly snackBar = inject(MatSnackBar);

  readonly days = DAYS;
  readonly today = todayInGym();
  readonly settings = signal<CashSettings | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly openDays = signal<Set<DayOfWeek>>(new Set());

  readonly form = new FormGroup({
    openingBase: new FormControl<number | null>(null, [Validators.required, Validators.min(0)]),
    startDate: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    this.loading.set(true);
    this.api
      .settings()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (s) => this.apply(s),
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  toggleDay(day: DayOfWeek, checked: boolean): void {
    const next = new Set(this.openDays());
    if (checked) {
      next.add(day);
    } else {
      next.delete(day);
    }
    this.openDays.set(next);
  }

  save(): void {
    if (this.form.invalid || this.openDays().size === 0) {
      this.form.markAllAsTouched();
      if (this.openDays().size === 0) {
        this.error.set('Selecciona al menos un día de apertura');
      }
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.api
      .updateSettings({
        openingBase: Number(v.openingBase),
        startDate: v.startDate || null,
        openDays: this.days.map((d) => d.value).filter((d) => this.openDays().has(d)),
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (s) => {
          this.apply(s);
          this.snackBar.open('Configuración de caja guardada', 'Cerrar', { duration: 4000 });
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  private apply(s: CashSettings): void {
    this.settings.set(s);
    this.openDays.set(new Set(s.openDays));
    this.form.setValue({ openingBase: s.openingBase, startDate: s.startDate ?? '' });
    if (s.startDateLocked) {
      this.form.controls.startDate.disable();
    }
  }
}
