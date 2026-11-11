import { Component, computed, inject, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';

import { AlertCountService } from '../../core/services/alert-count.service';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  label: string;
  icon: string;
  path: string;
  /** Si se define, solo estos roles ven la opción. */
  roles?: string[];
  /** Muestra el número de alertas de vencimiento. */
  alerts?: boolean;
}

/** Agrega aquí las opciones a medida que se construyen los módulos. */
const NAV_ITEMS: NavItem[] = [
  { label: 'Inicio', icon: 'dashboard', path: '/dashboard' },
  { label: 'Vencimientos', icon: 'notification_important', path: '/expirations', alerts: true },
  { label: 'Nueva inscripción', icon: 'point_of_sale', path: '/enrollment' },
  { label: 'Clientes', icon: 'groups', path: '/clients' },
  { label: 'Membresías', icon: 'event_available', path: '/memberships' },
  { label: 'Pagos', icon: 'payments', path: '/payments' },
  { label: 'Caja', icon: 'point_of_sale', path: '/cash' },
  { label: 'Cierres de caja', icon: 'fact_check', path: '/cash/closings', roles: ['ADMIN'] },
  { label: 'Reporte mensual', icon: 'bar_chart', path: '/cash/report', roles: ['ADMIN'] },
  { label: 'Reportes', icon: 'insights', path: '/reports', roles: ['ADMIN'] },
  { label: 'Planes', icon: 'card_membership', path: '/plans' },
  { label: 'Configuración de caja', icon: 'tune', path: '/settings/cash', roles: ['ADMIN'] },
  { label: 'Datos del comprobante', icon: 'receipt_long', path: '/settings/receipt', roles: ['ADMIN'] },
  { label: 'Usuarios', icon: 'manage_accounts', path: '/users', roles: ['ADMIN'] },
];

@Component({
  selector: 'app-sidebar',
  imports: [MatListModule, MatIconModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  private readonly auth = inject(AuthService);
  private readonly alertCount = inject(AlertCountService);

  readonly navigated = output<void>();
  readonly alertTotal = this.alertCount.total;

  constructor() {
    // El número del menú se pone al día al cambiar de pantalla (como máximo una consulta por minuto).
    this.alertCount.refresh();
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.alertCount.refresh());
  }

  readonly items = computed(() =>
    NAV_ITEMS.filter((item) => !item.roles || this.auth.hasRole(...item.roles)),
  );
}
