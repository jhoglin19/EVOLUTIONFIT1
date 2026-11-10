import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, finalize, merge } from 'rxjs';

import { errorMessage } from '../../core/utils/error-message';
import {
  EXPIRY_WARNING_DAYS,
  MEMBERSHIP_STATUS_LABELS,
  Membership,
  MembershipStatus,
} from '../../shared/models/membership.model';
import { MembershipStatusChip } from './membership-status-chip';
import { MembershipsService } from './memberships.service';

@Component({
  selector: 'app-memberships',
  imports: [
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MembershipStatusChip,
  ],
  templateUrl: './memberships.html',
  styleUrl: './memberships.scss',
})
export class Memberships {
  private readonly api = inject(MembershipsService);

  readonly warningDays = EXPIRY_WARNING_DAYS;
  readonly states = Object.entries(MEMBERSHIP_STATUS_LABELS) as [MembershipStatus, string][];
  readonly columns = ['client', 'plan', 'startDate', 'endDate', 'status'];

  readonly rows = signal<Membership[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly search = new FormControl('', { nonNullable: true });
  readonly state = new FormControl<MembershipStatus | null>(null);
  readonly expiringSoon = new FormControl(false, { nonNullable: true });

  constructor() {
    merge(
      this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
      this.state.valueChanges,
      this.expiringSoon.valueChanges,
    )
      .pipe(takeUntilDestroyed())
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
      .list(
        { state: this.state.value, expiringSoon: this.expiringSoon.value, query: this.search.value },
        this.pageIndex(),
        this.pageSize(),
      )
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
}
