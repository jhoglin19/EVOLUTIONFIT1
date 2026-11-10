import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Observable, finalize } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { Freeze, MAX_FREEZE_DAYS, Membership } from '../../../shared/models/membership.model';
import { addDays, todayInGym } from '../../../shared/utils/dates';
import { MembershipsService } from '../memberships.service';

/** Sin `freeze` se registra una congelación; con `freeze` se amplía (solo ADMIN). */
export interface FreezeDialogData {
  membership: Membership;
  freeze?: Freeze;
}

@Component({
  selector: 'app-freeze-dialog',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './freeze-dialog.html',
  styleUrl: '../membership-dialogs.scss',
})
export class FreezeDialog {
  private readonly api = inject(MembershipsService);
  private readonly dialogRef = inject(MatDialogRef<FreezeDialog, Membership>);
  readonly data = inject<FreezeDialogData>(MAT_DIALOG_DATA);

  readonly extending = !!this.data.freeze;
  readonly maxDays = MAX_FREEZE_DAYS;
  readonly minDays = this.extending ? this.data.freeze!.days + 1 : 1;
  readonly today = todayInGym();
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup({
    startDate: new FormControl(this.data.freeze?.startDate ?? this.today, {
      nonNullable: true,
      validators: this.extending ? [] : [Validators.required],
    }),
    days: new FormControl<number | null>(this.extending ? this.minDays : null, {
      validators: [
        Validators.required,
        Validators.min(this.minDays),
        Validators.max(MAX_FREEZE_DAYS),
        Validators.pattern(/^\d+$/),
      ],
    }),
    reason: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(255), Validators.pattern(/\S/)],
    }),
  });

  private readonly days = toSignal(this.form.controls.days.valueChanges, {
    initialValue: this.form.controls.days.value,
  });

  /** Vencimiento resultante. Solo informativo: lo calcula y lo guarda el backend. */
  readonly newEndDate = computed(() => {
    const days = Number(this.days());
    if (!Number.isInteger(days) || days < this.minDays || days > MAX_FREEZE_DAYS) {
      return null;
    }
    const added = this.extending ? days - this.data.freeze!.days : days;
    return addDays(this.data.membership.endDate, added);
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const request$: Observable<Membership> = this.extending
      ? this.api.extendFreeze(this.data.freeze!.id, { totalDays: v.days!, reason: v.reason.trim() })
      : this.api.freeze(this.data.membership.id, {
          startDate: v.startDate,
          days: v.days!,
          reason: v.reason.trim(),
        });

    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (membership) => this.dialogRef.close(membership),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
