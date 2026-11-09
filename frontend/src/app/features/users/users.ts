import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/utils/error-message';
import { User } from '../../shared/models/user.model';
import { roleLabel } from '../../shared/utils/role-label';
import { UserFormDialog, UserFormData } from './user-form-dialog/user-form-dialog';
import { UsersService } from './users.service';

@Component({
  selector: 'app-users',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './users.html',
  styleUrl: './users.scss',
})
export class Users {
  private readonly api = inject(UsersService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  readonly columns = ['name', 'username', 'email', 'role', 'lastLogin', 'active', 'actions'];

  readonly rows = signal<User[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly search = new FormControl('', { nonNullable: true });
  protected readonly roleLabel = roleLabel;

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.pageIndex.set(0);
        this.load();
      });
    this.load();
  }

  isSelf(user: User): boolean {
    return user.id === this.auth.user()?.id;
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(this.search.value, this.pageIndex(), this.pageSize())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  create(): void {
    this.openForm({}, 'Usuario creado');
  }

  edit(user: User): void {
    this.openForm({ user }, 'Usuario actualizado');
  }

  toggleActive(user: User, active: boolean): void {
    this.api.setActive(user.id, active).subscribe({
      next: () => {
        this.notify(active ? 'Usuario activado' : 'Usuario desactivado');
        this.load();
      },
      error: (err) => {
        this.notify(errorMessage(err));
        this.load(); // restaura el interruptor al estado real
      },
    });
  }

  private openForm(data: UserFormData, successMessage: string): void {
    this.dialog
      .open<UserFormDialog, UserFormData, User>(UserFormDialog, { data, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.notify(successMessage);
          this.load();
        }
      });
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'Cerrar', { duration: 4000 });
  }
}
