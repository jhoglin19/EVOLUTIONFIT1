import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { EMPTY, catchError, debounceTime, finalize, merge, switchMap, tap } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { Client } from '../../../shared/models/client.model';
import { Membership, MembershipPreview } from '../../../shared/models/membership.model';
import { Plan } from '../../../shared/models/plan.model';
import { todayInGym } from '../../../shared/utils/dates';
import { PlansService } from '../../plans/plans.service';
import { MembershipsService } from '../memberships.service';

export interface MembershipFormData {
  client: Client;
}

/** Membresía nueva o renovación. Queda pendiente de pago: el pago total la activa (módulo de pagos). */
@Component({
  selector: 'app-membership-form-dialog',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './membership-form-dialog.html',
  styleUrl: '../membership-dialogs.scss',
})
export class MembershipFormDialog {
  private readonly api = inject(MembershipsService);
  private readonly plansApi = inject(PlansService);
  private readonly dialogRef = inject(MatDialogRef<MembershipFormDialog, Membership>);
  readonly data = inject<MembershipFormData>(MAT_DIALOG_DATA);

  readonly today = todayInGym();
  readonly plans = signal<Plan[]>([]);
  readonly preview = signal<MembershipPreview | null>(null);
  readonly previewError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup({
    planId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    startDate: new FormControl('', { nonNullable: true }),
    notes: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(2000)] }),
  });

  constructor() {
    this.plansApi.list('', true, 0, 100).subscribe({
      next: (page) => this.plans.set(page.content),
      error: (err) => this.error.set(errorMessage(err)),
    });

    const { planId, startDate } = this.form.controls;
    merge(planId.valueChanges, startDate.valueChanges)
      .pipe(
        debounceTime(250),
        tap(() => {
          this.preview.set(null);
          this.previewError.set(null);
        }),
        switchMap(() => {
          if (!planId.value) {
            return EMPTY;
          }
          return this.api.preview(this.data.client.id, planId.value, startDate.value || null).pipe(
            catchError((err) => {
              this.previewError.set(errorMessage(err));
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((preview) => this.preview.set(preview));
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.api
      .create({
        clientId: this.data.client.id,
        planId: value.planId!,
        startDate: value.startDate || null,
        notes: value.notes.trim() || null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (membership) => this.dialogRef.close(membership),
        error: (err) => this.error.set(errorMessage(err)),
      });
  }
}
