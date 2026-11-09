import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { catchError, finalize, of } from 'rxjs';

import { errorMessage } from '../../../core/utils/error-message';
import { User } from '../../../shared/models/user.model';
import { roleLabel } from '../../../shared/utils/role-label';
import { UsersService } from '../users.service';

export interface UserFormData {
  /** Si viene un usuario se edita; si no, se crea uno nuevo. */
  user?: User;
}

@Component({
  selector: 'app-user-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './user-form-dialog.html',
  styleUrl: './user-form-dialog.scss',
})
export class UserFormDialog {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(UsersService);
  private readonly dialogRef = inject(MatDialogRef<UserFormDialog, User>);
  private readonly data = inject<UserFormData>(MAT_DIALOG_DATA);

  readonly editing = !!this.data.user;
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  protected readonly roleLabel = roleLabel;

  readonly roles = toSignal(this.api.roles().pipe(catchError(() => of([]))), { initialValue: [] });

  readonly form = this.fb.group({
    username: [
      { value: this.data.user?.username ?? '', disabled: this.editing },
      [Validators.required, Validators.minLength(3), Validators.maxLength(60), Validators.pattern(/^[A-Za-z0-9._-]+$/)],
    ],
    email: [this.data.user?.email ?? '', [Validators.email, Validators.maxLength(150)]],
    firstName: [this.data.user?.firstName ?? '', [Validators.required, Validators.maxLength(80)]],
    lastName: [this.data.user?.lastName ?? '', [Validators.required, Validators.maxLength(80)]],
    role: [this.data.user?.role ?? '', [Validators.required]],
    password: [
      '',
      this.editing ? [] : [Validators.required, Validators.minLength(8), Validators.maxLength(72)],
    ],
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const email = value.email.trim() || null;

    const request$ = this.editing
      ? this.api.update(this.data.user!.id, {
          email,
          firstName: value.firstName.trim(),
          lastName: value.lastName.trim(),
          role: value.role,
        })
      : this.api.create({
          username: value.username.trim(),
          email,
          password: value.password,
          firstName: value.firstName.trim(),
          lastName: value.lastName.trim(),
          role: value.role,
        });

    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (user) => this.dialogRef.close(user),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
