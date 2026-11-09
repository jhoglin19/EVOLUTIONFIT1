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
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/utils/error-message';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog';
import { Client } from '../../shared/models/client.model';
import { ClientFormData, ClientFormDialog } from './client-form-dialog/client-form-dialog';
import { ClientsService } from './clients.service';

type StatusFilter = 'all' | 'active' | 'inactive';

@Component({
  selector: 'app-clients',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './clients.html',
  styleUrl: './clients.scss',
})
export class Clients {
  private readonly api = inject(ClientsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  readonly columns = ['document', 'name', 'phone', 'email', 'createdAt', 'active', 'actions'];

  readonly rows = signal<Client[]>([]);
  readonly total = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly search = new FormControl('', { nonNullable: true });
  readonly status = new FormControl<StatusFilter>('all', { nonNullable: true });

  /** Solo es comodidad visual: el backend exige el rol ADMIN en este cambio. */
  readonly canChangeStatus = this.auth.hasRole('ADMIN');

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.reload());
    this.status.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.reload());
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(this.search.value, this.activeFilter(), this.pageIndex(), this.pageSize())
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
    this.openForm({}, 'Cliente registrado');
  }

  edit(client: Client): void {
    this.openForm({ client }, 'Cliente actualizado');
  }

  toggleActive(client: Client, active: boolean): void {
    if (active) {
      this.applyStatus(client, true);
      return;
    }
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
        data: {
          title: 'Desactivar cliente',
          message: `${client.firstName} ${client.lastName} no podrá ingresar al gimnasio mientras esté inactivo, aunque tenga una membresía vigente.`,
          confirmLabel: 'Desactivar',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.applyStatus(client, false);
        } else {
          this.load(); // restaura el interruptor al estado real
        }
      });
  }

  private applyStatus(client: Client, active: boolean): void {
    this.api.setActive(client.id, active).subscribe({
      next: () => {
        this.notify(active ? 'Cliente activado' : 'Cliente desactivado');
        this.load();
      },
      error: (err) => {
        this.notify(errorMessage(err));
        this.load();
      },
    });
  }

  private activeFilter(): boolean | null {
    const status = this.status.value;
    return status === 'all' ? null : status === 'active';
  }

  private reload(): void {
    this.pageIndex.set(0);
    this.load();
  }

  private openForm(data: ClientFormData, successMessage: string): void {
    this.dialog
      .open<ClientFormDialog, ClientFormData, Client>(ClientFormDialog, { data, autoFocus: 'first-tabbable' })
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
