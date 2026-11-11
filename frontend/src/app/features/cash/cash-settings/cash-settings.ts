import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { CalendarDay, CashSettings, DAYS, DayOfWeek, GymCalendar } from '../../../shared/models/cash.model';
import { todayInGym } from '../../../shared/utils/dates';
import { CashService } from '../cash.service';

/** Configuración de caja (solo ADMIN): base, fecha de inicio y días que abre. */
@Component({
  selector: 'app-cash-settings',
  imports: [
    DatePipe,
    MatSelectModule,
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
    closedOnHolidays: new FormControl(true, { nonNullable: true }),
  });

  // ---- calendario de atención ----
  readonly years = [Number(this.today.slice(0, 4)), Number(this.today.slice(0, 4)) + 1];
  readonly year = new FormControl(this.years[0], { nonNullable: true });
  readonly calendar = signal<GymCalendar | null>(null);
  readonly calendarError = signal<string | null>(null);
  readonly exception = new FormGroup({
    date: new FormControl('', { nonNullable: true, validators: Validators.required }),
    open: new FormControl(false, { nonNullable: true }),
    note: new FormControl('', { nonNullable: true, validators: Validators.maxLength(255) }),
  });

  constructor() {
    this.year.valueChanges.subscribe(() => this.loadCalendar());
    this.loadCalendar();
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
        closedOnHolidays: v.closedOnHolidays,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (s) => {
          this.apply(s);
          this.loadCalendar();
          this.snackBar.open('Configuración de caja guardada', 'Cerrar', { duration: 4000 });
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  loadCalendar(): void {
    this.calendarError.set(null);
    this.api.calendar(this.year.value).subscribe({
      next: (c) => this.calendar.set(c),
      error: (err) => this.calendarError.set(errorMessage(err)),
    });
  }

  /** Invierte lo que diría la regla general para ese día (abrir un festivo o cerrar un día normal). */
  toggle(day: CalendarDay): void {
    this.applyException(day.date, !day.open, day.note);
  }

  clear(day: CalendarDay): void {
    this.api.clearDay(day.date).subscribe({
      next: () => this.afterCalendarChange('Excepción quitada'),
      error: (err) => this.calendarError.set(errorMessage(err)),
    });
  }

  saveException(): void {
    if (this.exception.invalid) {
      this.exception.markAllAsTouched();
      return;
    }
    const v = this.exception.getRawValue();
    this.applyException(v.date, v.open, v.note.trim() || null);
  }

  private applyException(date: string, open: boolean, note: string | null): void {
    this.api.setDay(date, open, note).subscribe({
      next: (d) => {
        this.exception.reset({ date: '', open: false, note: '' });
        this.afterCalendarChange(d.open ? 'Ese día queda abierto' : 'Ese día queda cerrado');
      },
      error: (err) => this.calendarError.set(errorMessage(err)),
    });
  }

  private afterCalendarChange(message: string): void {
    this.snackBar.open(message, 'Cerrar', { duration: 4000 });
    this.loadCalendar();
  }

  private apply(s: CashSettings): void {
    this.settings.set(s);
    this.openDays.set(new Set(s.openDays));
    this.form.setValue({ openingBase: s.openingBase, startDate: s.startDate ?? '', closedOnHolidays: s.closedOnHolidays });
    if (s.startDateLocked) {
      this.form.controls.startDate.disable();
    }
  }
}
