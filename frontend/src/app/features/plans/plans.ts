import { CurrencyPipe } from '@angular/common';
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
import { Plan } from '../../shared/models/plan.model';
import { PlanFormData, PlanFormDialog } from './plan-form-dialog/plan-form-dialog';
import { PlansService } from './plans.service';

@Component({
  selector: 'app-plans',
  imports: [
    CurrencyPipe,
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
  templateUrl: './plans.html',
  styleUrl: './plans.scss',
})
export class Plans {
  private readonly api = inject(PlansService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  /** Solo es comodidad visual: el backend exige el rol ADMIN para modificar planes. */
  readonly canEdit = this.auth.hasRole('ADMIN');
  readonly columns = this.canEdit
    ? ['name', 'description', 'duration', 'price', 'active', 'actions']
    : ['name', 'description', 'duration', 'price', 'active'];

  readonly rows = signal<Plan[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly search = new FormControl('', { nonNullable: true });

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.pageIndex.set(0);
        this.load();
      });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(this.search.value, null, this.pageIndex(), this.pageSize())
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
    this.openForm({}, 'Plan creado');
  }

  edit(plan: Plan): void {
    this.openForm({ plan }, 'Plan actualizado');
  }

  toggleActive(plan: Plan, active: boolean): void {
    this.api.setActive(plan.id, active).subscribe({
      next: () => {
        this.notify(active ? 'Plan activado' : 'Plan desactivado');
        this.load();
      },
      error: (err) => {
        this.notify(errorMessage(err));
        this.load(); // restaura el interruptor al estado real
      },
    });
  }

  private openForm(data: PlanFormData, successMessage: string): void {
    this.dialog
      .open<PlanFormDialog, PlanFormData, Plan>(PlanFormDialog, { data, autoFocus: 'first-tabbable' })
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
