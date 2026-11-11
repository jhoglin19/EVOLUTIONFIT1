import { Component, computed, inject, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';

import { AlertCountService } from '../../core/services/alert-count.service';
import { AuthService } from '../../core/services/auth.service';

type NavGroup = 'Operación' | 'Caja' | 'Administración';

interface NavItem {
  label: string;
  icon: string;
  path: string;
  group: NavGroup;
  /** Si se define, solo estos roles ven la opción. */
  roles?: string[];
  /** Muestra el número de alertas de vencimiento. */
  alerts?: boolean;
}

/** Grupos del menú, en orden (docs/DISENO_VISUAL.md, sección 4). Un grupo sin opciones visibles no se muestra. */
const NAV_GROUPS: NavGroup[] = ['Operación', 'Caja', 'Administración'];

/** Agrega aquí las opciones a medida que se construyen los módulos. */
const NAV_ITEMS: NavItem[] = [
  { label: 'Inicio', icon: 'dashboard', path: '/dashboard', group: 'Operación' },
  { label: 'Vencimientos', icon: 'notification_important', path: '/expirations', group: 'Operación', alerts: true },
  { label: 'Nueva inscripción', icon: 'person_add', path: '/enrollment', group: 'Operación' },
  { label: 'Clientes', icon: 'groups', path: '/clients', group: 'Operación' },
  { label: 'Membresías', icon: 'event_available', path: '/memberships', group: 'Operación' },
  { label: 'Pagos', icon: 'payments', path: '/payments', group: 'Operación' },
  { label: 'Planes', icon: 'card_membership', path: '/plans', group: 'Operación' },
  { label: 'Caja', icon: 'point_of_sale', path: '/cash', group: 'Caja' },
  { label: 'Cierres de caja', icon: 'fact_check', path: '/cash/closings', group: 'Caja', roles: ['ADMIN'] },
  { label: 'Reporte mensual', icon: 'bar_chart', path: '/cash/report', group: 'Caja', roles: ['ADMIN'] },
  { label: 'Reportes', icon: 'insights', path: '/reports', group: 'Administración', roles: ['ADMIN'] },
  { label: 'Configuración de caja', icon: 'tune', path: '/settings/cash', group: 'Administración', roles: ['ADMIN'] },
  { label: 'Datos del comprobante', icon: 'receipt_long', path: '/settings/receipt', group: 'Administración', roles: ['ADMIN'] },
  { label: 'Usuarios', icon: 'manage_accounts', path: '/users', group: 'Administración', roles: ['ADMIN'] },
];

@Component({
  selector: 'app-sidebar',
  imports: [MatRippleModule, MatIconModule, RouterLink, RouterLinkActive],
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

  /** Opciones visibles agrupadas, sin grupos vacíos. La visibilidad por rol es la misma de items(). */
  readonly groups = computed(() =>
    NAV_GROUPS.map((name) => ({ name, items: this.items().filter((item) => item.group === name) })).filter(
      (group) => group.items.length > 0,
    ),
  );

  logout(): void {
    this.auth.logout();
  }
}
