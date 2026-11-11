import { Routes } from '@angular/router';

import { roleGuard } from '../../core/guards/auth.guards';
import { provideSpanishMaterialIntl } from '../../core/i18n/material-intl';
import { Shell } from './shell';

/**
 * Pantallas con sesión iniciada. Los proveedores de esta ruta valen para todas sus pantallas: el
 * paginador y el asistente por pasos de Angular Material quedan en español de Colombia.
 */
export const SHELL_ROUTES: Routes = [
  {
    path: '',
    component: Shell,
    providers: provideSpanishMaterialIntl(),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('../../features/dashboard/dashboard').then((m) => m.Dashboard),
        title: 'Inicio · EvolutionFit',
      },
      {
        path: 'expirations',
        loadComponent: () =>
          import('../../features/expirations/expirations').then((m) => m.Expirations),
        title: 'Vencimientos · EvolutionFit',
      },
      {
        path: 'clients',
        loadComponent: () => import('../../features/clients/clients').then((m) => m.Clients),
        title: 'Clientes · EvolutionFit',
      },
      {
        path: 'clients/:id',
        loadComponent: () =>
          import('../../features/clients/client-detail/client-detail').then((m) => m.ClientDetail),
        title: 'Ficha del cliente · EvolutionFit',
      },
      {
        path: 'memberships',
        loadComponent: () =>
          import('../../features/memberships/memberships').then((m) => m.Memberships),
        title: 'Membresías · EvolutionFit',
      },
      {
        path: 'plans',
        loadComponent: () => import('../../features/plans/plans').then((m) => m.Plans),
        title: 'Planes · EvolutionFit',
      },
      {
        path: 'enrollment',
        loadComponent: () =>
          import('../../features/enrollment/enrollment').then((m) => m.Enrollment),
        title: 'Inscripción · EvolutionFit',
      },
      {
        path: 'payments',
        loadComponent: () => import('../../features/payments/payments').then((m) => m.Payments),
        title: 'Pagos · EvolutionFit',
      },
      {
        path: 'cash',
        loadComponent: () =>
          import('../../features/cash/cash-today/cash-today').then((m) => m.CashTodayPage),
        title: 'Caja · EvolutionFit',
      },
      {
        path: 'cash/closings',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () =>
          import('../../features/cash/cash-closings/cash-closings').then((m) => m.CashClosingsPage),
        title: 'Cierres de caja · EvolutionFit',
      },
      {
        path: 'cash/report',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () =>
          import('../../features/cash/cash-report/cash-report').then((m) => m.CashReportPage),
        title: 'Reporte mensual de caja · EvolutionFit',
      },
      {
        path: 'reports',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('../../features/reports/reports').then((m) => m.Reports),
        title: 'Reportes · EvolutionFit',
      },
      {
        path: 'settings/cash',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () =>
          import('../../features/cash/cash-settings/cash-settings').then((m) => m.CashSettingsPage),
        title: 'Configuración de caja · EvolutionFit',
      },
      {
        path: 'settings/receipt',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () =>
          import('../../features/settings/receipt-settings').then((m) => m.ReceiptSettingsPage),
        title: 'Datos del comprobante · EvolutionFit',
      },
      {
        path: 'users',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('../../features/users/users').then((m) => m.Users),
        title: 'Usuarios · EvolutionFit',
      },
    ],
  },
];
