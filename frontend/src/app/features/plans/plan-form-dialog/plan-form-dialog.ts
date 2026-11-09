import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { Plan } from '../../../shared/models/plan.model';
import { PlansService } from '../plans.service';

export interface PlanFormData {
  /** Si viene un plan se edita; si no, se crea uno nuevo. */
  plan?: Plan;
}

@Component({
  selector: 'app-plan-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './plan-form-dialog.html',
  styleUrl: './plan-form-dialog.scss',
})
export class PlanFormDialog {
  private readonly api = inject(PlansService);
  private readonly dialogRef = inject(MatDialogRef<PlanFormDialog, Plan>);
  private readonly data = inject<PlanFormData>(MAT_DIALOG_DATA);

  readonly editing = !!this.data.plan;
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup({
    name: new FormControl(this.data.plan?.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    description: new FormControl(this.data.plan?.description ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(255)],
    }),
    durationDays: new FormControl<number | null>(this.data.plan?.durationDays ?? null, {
      validators: [Validators.required, Validators.min(1), Validators.max(3650), Validators.pattern(/^\d+$/)],
    }),
    price: new FormControl<number | null>(this.data.plan?.price ?? null, {
      validators: [Validators.required, Validators.min(0), Validators.pattern(/^\d{1,10}(\.\d{1,2})?$/)],
    }),
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const request = {
      name: value.name.trim(),
      description: value.description.trim() || null,
      durationDays: value.durationDays!,
      price: value.price!,
    };

    const request$ = this.editing ? this.api.update(this.data.plan!.id, request) : this.api.create(request);

    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (plan) => this.dialogRef.close(plan),
      error: (err) => this.showError(err),
    });
  }

  private showError(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 409) {
      const control = this.form.controls.name;
      control.setErrors({ conflict: errorMessage(err) });
      control.markAsTouched();
      return;
    }
    this.error.set(errorMessage(err));
  }
}
