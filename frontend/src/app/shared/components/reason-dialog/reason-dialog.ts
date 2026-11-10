import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ReasonDialogData {
  title: string;
  message: string;
  confirmLabel: string;
}

/** Pide un motivo obligatorio. Se cierra con el motivo (sin espacios sobrantes) o con undefined. */
@Component({
  selector: 'app-reason-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <form (ngSubmit)="confirm()" novalidate>
      <mat-dialog-content>
        <p>{{ data.message }}</p>
        <mat-form-field appearance="outline">
          <mat-label>Motivo</mat-label>
          <textarea matInput [formControl]="reason" rows="3" maxlength="255"></textarea>
          @if (reason.hasError('required')) {
            <mat-error>El motivo es obligatorio</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Volver</button>
        <button mat-flat-button type="submit">{{ data.confirmLabel }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    mat-dialog-content {
      display: flex;
      flex-direction: column;
      min-width: min(440px, 80vw);
    }
  `,
})
export class ReasonDialog {
  readonly data = inject<ReasonDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ReasonDialog, string>);

  readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(255), Validators.pattern(/\S/)],
  });

  confirm(): void {
    if (this.reason.invalid) {
      this.reason.setErrors({ required: true });
      this.reason.markAsTouched();
      return;
    }
    this.dialogRef.close(this.reason.value.trim());
  }
}
