import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';

import { AlertCountService } from '../../core/services/alert-count.service';
import { errorMessage } from '../../core/utils/error-message';
import { ExpirationAlert, ExpirationAlertKind, daysLabel } from '../../shared/models/dashboard.model';
import { MembershipStatusChip } from '../memberships/membership-status-chip';
import { AlertActions } from './alert-actions';
import { ExpirationsService } from './expirations.service';

/**
 * Vencimientos: alertas solo dentro del sistema (sin seguimiento). "Por vencer" son las vigentes que vencen
 * en los próximos días del aviso sin una renovación pagada; "Vencidas recientes", las que vencieron hace poco
 * y no renovaron. No aparecen los clientes inactivos. Un cliente sale del listado cuando paga la renovación.
 */
@Component({
  selector: 'app-expirations',
  imports: [
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTabsModule,
    MembershipStatusChip,
    AlertActions,
  ],
  templateUrl: './expirations.html',
  styleUrl: './expirations.scss',
})
export class Expirations {
  private readonly api = inject(ExpirationsService);
  private readonly alertCount = inject(AlertCountService);

  /** ?tab=expired abre "Vencidas recientes" (enlace desde el panel). */
  readonly tab = input<string | undefined>();

  readonly kinds: ExpirationAlertKind[] = ['EXPIRING', 'EXPIRED'];
  readonly kind = signal<ExpirationAlertKind>('EXPIRING');
  readonly rows = signal<ExpirationAlert[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly counts = this.alertCount.value;

  readonly search = new FormControl('', { nonNullable: true });
  protected readonly daysLabel = daysLabel;

  constructor() {
    this.alertCount.refresh(true);
    effect(() => {
      const kind: ExpirationAlertKind = this.tab() === 'expired' ? 'EXPIRED' : 'EXPIRING';
      untracked(() => this.select(kind));
    });
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.pageIndex.set(0);
        this.load();
      });
  }

  select(kind: ExpirationAlertKind): void {
    this.kind.set(kind);
    this.pageIndex.set(0);
    this.load();
  }

  tabChanged(index: number): void {
    if (this.kinds[index] !== this.kind()) {
      this.select(this.kinds[index]);
    }
  }

  page(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(this.kind(), this.search.value, this.pageIndex(), this.pageSize())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }
}
