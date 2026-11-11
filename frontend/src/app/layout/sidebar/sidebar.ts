import { Component, computed, inject, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  label: string;
  icon: string;
  path: string;
  /** Si se define, solo estos roles ven la opción. */
  roles?: string[];
}

/** Agrega aquí las opciones a medida que se construyen los módulos. */
const NAV_ITEMS: NavItem[] = [
  { label: 'Inicio', icon: 'dashboard', path: '/dashboard' },
  { label: 'Nueva inscripción', icon: 'point_of_sale', path: '/enrollment' },
  { label: 'Clientes', icon: 'groups', path: '/clients' },
  { label: 'Membresías', icon: 'event_available', path: '/memberships' },
  { label: 'Pagos', icon: 'payments', path: '/payments' },
  { label: 'Caja', icon: 'point_of_sale', path: '/cash' },
  { label: 'Cierres de caja', icon: 'fact_check', path: '/cash/closings', roles: ['ADMIN'] },
  { label: 'Reporte mensual', icon: 'bar_chart', path: '/cash/report', roles: ['ADMIN'] },
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

  readonly navigated = output<void>();

  readonly items = computed(() =>
    NAV_ITEMS.filter((item) => !item.roles || this.auth.hasRole(...item.roles)),
  );
}
