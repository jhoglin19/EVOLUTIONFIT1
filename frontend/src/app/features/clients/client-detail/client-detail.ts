import { CurrencyPipe, DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { errorMessage } from '../../../core/utils/error-message';
import { ReasonDialog, ReasonDialogData } from '../../../shared/components/reason-dialog/reason-dialog';
import { Client } from '../../../shared/models/client.model';
import {
  CurrentMembership,
  FREEZE_STATUS_LABELS,
  Freeze,
  Membership,
} from '../../../shared/models/membership.model';
import { FreezeDialog, FreezeDialogData } from '../../memberships/freeze-dialog/freeze-dialog';
import { MembershipFormData, MembershipFormDialog } from '../../memberships/membership-form-dialog/membership-form-dialog';
import { MembershipStatusChip } from '../../memberships/membership-status-chip';
import { MembershipsService } from '../../memberships/memberships.service';
import {
  MigratedMembershipData,
  MigratedMembershipDialog,
} from '../../memberships/migrated-membership-dialog/migrated-membership-dialog';
import { ClientsService } from '../clients.service';

/** Ficha del cliente con sus membresías. Los botones de ADMIN son comodidad: el backend valida el rol. */
@Component({
  selector: 'app-client-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    MembershipStatusChip,
  ],
  templateUrl: './client-detail.html',
  styleUrl: './client-detail.scss',
})
export class ClientDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly clientsApi = inject(ClientsService);
  private readonly api = inject(MembershipsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  readonly clientId = Number(this.route.snapshot.paramMap.get('id'));
  readonly isAdmin = this.auth.hasRole('ADMIN');
  readonly columns = this.isAdmin
    ? ['plan', 'startDate', 'endDate', 'status', 'price', 'actions']
    : ['plan', 'startDate', 'endDate', 'status', 'price'];

  readonly client = signal<Client | null>(null);
  readonly summary = signal<CurrentMembership>({});
  readonly history = signal<Membership[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      client: this.clientsApi.get(this.clientId),
      summary: this.api.current(this.clientId),
      history: this.api.history(this.clientId),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ client, summary, history }) => {
          this.client.set(client);
          this.summary.set(summary);
          this.history.set(history);
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  /** Solo una siguiente por cliente (pendiente de pago o programada). */
  canSell(): boolean {
    return !!this.client()?.active && !this.summary().next;
  }

  canMigrate(): boolean {
    return this.isAdmin && !!this.client()?.active && !this.summary().current && !this.summary().next;
  }

  freezeLabel(freeze: Freeze): string {
    return FREEZE_STATUS_LABELS[freeze.status];
  }

  /** Congelación programada o en curso, que el ADMIN puede ampliar o cancelar. */
  openFreeze(m: Membership): Freeze | undefined {
    return m.freezes.find((f) => f.status === 'PENDING' || f.status === 'ACTIVE');
  }

  sell(): void {
    const client = this.client();
    if (!client) {
      return;
    }
    this.dialog
      .open<MembershipFormDialog, MembershipFormData, Membership>(MembershipFormDialog, {
        data: { client },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((saved) => this.afterChange(saved, 'Membresía registrada: pendiente de pago'));
  }

  migrate(): void {
    const client = this.client();
    if (!client) {
      return;
    }
    this.dialog
      .open<MigratedMembershipDialog, MigratedMembershipData, Membership>(MigratedMembershipDialog, {
        data: { client },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((saved) => this.afterChange(saved, 'Membresía migrada registrada'));
  }

  freeze(membership: Membership): void {
    this.openFreezeDialog({ membership }, 'Congelación registrada');
  }

  extendFreeze(membership: Membership, freeze: Freeze): void {
    this.openFreezeDialog({ membership, freeze }, 'Congelación ampliada');
  }

  cancelFreeze(freeze: Freeze): void {
    this.askReason({
      title: 'Cancelar congelación',
      message:
        'Si no ha empezado, no suma días y la membresía podrá congelarse de nuevo. Si está en curso, solo se suman los días ya transcurridos y no se podrá volver a congelar.',
      confirmLabel: 'Cancelar congelación',
    }).subscribe((reason) => {
      if (reason) {
        this.api.cancelFreeze(freeze.id, reason).subscribe({
          next: () => this.afterChange(true, 'Congelación cancelada'),
          error: (err) => this.notify(errorMessage(err)),
        });
      }
    });
  }

  cancelMembership(membership: Membership): void {
    this.askReason({
      title: 'Anular membresía',
      message:
        'La membresía no se borra: queda anulada con el motivo. Si tiene una congelación programada o en curso, se cancela (solo cuentan los días ya transcurridos). Si es la vigente y hay una siguiente, la siguiente empieza hoy. Para cambiar de plan, anula y registra otra.',
      confirmLabel: 'Anular',
    }).subscribe((reason) => {
      if (reason) {
        this.api.cancel(membership.id, reason).subscribe({
          next: () => this.afterChange(true, 'Membresía anulada'),
          error: (err) => this.notify(errorMessage(err)),
        });
      }
    });
  }

  private openFreezeDialog(data: FreezeDialogData, message: string): void {
    this.dialog
      .open<FreezeDialog, FreezeDialogData, Membership>(FreezeDialog, { data, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((saved) => this.afterChange(saved, message));
  }

  private askReason(data: ReasonDialogData) {
    return this.dialog
      .open<ReasonDialog, ReasonDialogData, string>(ReasonDialog, { data, autoFocus: 'first-tabbable' })
      .afterClosed();
  }

  private afterChange(changed: unknown, message: string): void {
    if (changed) {
      this.notify(message);
      this.load();
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'Cerrar', { duration: 4000 });
  }
}
