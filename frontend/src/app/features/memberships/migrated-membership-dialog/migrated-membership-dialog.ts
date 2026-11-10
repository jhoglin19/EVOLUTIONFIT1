import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize, merge } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { Client } from '../../../shared/models/client.model';
import { Membership } from '../../../shared/models/membership.model';
import { Plan } from '../../../shared/models/plan.model';
import { planEndDate, todayInGym } from '../../../shared/utils/dates';
import { PlansService } from '../../plans/plans.service';
import { MembershipsService } from '../memberships.service';

export interface MigratedMembershipData {
  client: Client;
}

/** Fechas ISO (yyyy-MM-dd): se comparan como texto. */
const notAfter =
  (max: string): ValidatorFn =>
  (c: AbstractControl): ValidationErrors | null =>
    c.value && c.value > max ? { notAfter: true } : null;

const notBefore =
  (min: string): ValidatorFn =>
  (c: AbstractControl): ValidationErrors | null =>
    c.value && c.value < min ? { notBefore: true } : null;

const endAfterStart: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const start = group.get('startDate')?.value;
  const end = group.get('endDate')?.value;
  return start && end && end < start ? { endBeforeStart: true } : null;
};

/** Solo ADMIN (el backend lo exige). Conserva el vencimiento real del sistema anterior y no pide pago. */
@Component({
  selector: 'app-migrated-membership-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './migrated-membership-dialog.html',
  styleUrl: '../membership-dialogs.scss',
})
export class MigratedMembershipDialog {
  private readonly api = inject(MembershipsService);
  private readonly plansApi = inject(PlansService);
  private readonly dialogRef = inject(MatDialogRef<MigratedMembershipDialog, Membership>);
  readonly data = inject<MigratedMembershipData>(MAT_DIALOG_DATA);

  readonly today = todayInGym();
  readonly plans = signal<Plan[]>([]);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup(
    {
      planId: new FormControl<number | null>(null, { validators: [Validators.required] }),
      startDate: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, notAfter(this.today)],
      }),
      endDate: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, notBefore(this.today)],
      }),
      price: new FormControl<number | null>(null, {
        validators: [Validators.required, Validators.min(0), Validators.pattern(/^\d{1,10}(\.\d{1,2})?$/)],
      }),
      migrationNote: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(255), Validators.pattern(/\S/)],
      }),
      freezeUsedInPreviousSystem: new FormControl(false, { nonNullable: true }),
      notes: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(2000)] }),
    },
    { validators: endAfterStart },
  );

  constructor() {
    // Incluye planes inactivos: el cliente pudo comprar en el sistema anterior un plan que ya no se vende.
    this.plansApi.list('', null, 0, 100).subscribe({
      next: (page) => this.plans.set(page.content),
      error: (err) => this.error.set(errorMessage(err)),
    });

    const { planId, startDate, endDate, price } = this.form.controls;
    planId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      const plan = this.selectedPlan();
      if (plan && !price.dirty) {
        price.setValue(plan.price);
      }
    });
    // Sugiere el vencimiento calculado mientras el administrador no escriba el real.
    merge(planId.valueChanges, startDate.valueChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        const plan = this.selectedPlan();
        if (plan && startDate.value && !endDate.dirty) {
          endDate.setValue(planEndDate(startDate.value, plan.durationDays));
          endDate.markAsTouched();
        }
      });
  }

  selectedPlan(): Plan | undefined {
    return this.plans().find((p) => p.id === this.form.controls.planId.value);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.api
      .createMigrated({
        clientId: this.data.client.id,
        planId: v.planId!,
        startDate: v.startDate,
        endDate: v.endDate,
        price: v.price,
        migrationNote: v.migrationNote.trim(),
        freezeUsedInPreviousSystem: v.freezeUsedInPreviousSystem,
        notes: v.notes.trim() || null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (membership) => this.dialogRef.close(membership),
        error: (err) => this.error.set(errorMessage(err)),
      });
  }
}
