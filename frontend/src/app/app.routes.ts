import { Routes } from '@angular/router';

import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
    title: 'Iniciar sesión · EvolutionFit',
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
        title: 'Inicio · EvolutionFit',
      },
      {
        path: 'clients',
        loadComponent: () => import('./features/clients/clients').then((m) => m.Clients),
        title: 'Clientes · EvolutionFit',
      },
      {
        path: 'clients/:id',
        loadComponent: () =>
          import('./features/clients/client-detail/client-detail').then((m) => m.ClientDetail),
        title: 'Ficha del cliente · EvolutionFit',
      },
      {
        path: 'memberships',
        loadComponent: () => import('./features/memberships/memberships').then((m) => m.Memberships),
        title: 'Membresías · EvolutionFit',
      },
      {
        path: 'plans',
        loadComponent: () => import('./features/plans/plans').then((m) => m.Plans),
        title: 'Planes · EvolutionFit',
      },
      {
        path: 'enrollment',
        loadComponent: () => import('./features/enrollment/enrollment').then((m) => m.Enrollment),
        title: 'Inscripción · EvolutionFit',
      },
      {
        path: 'payments',
        loadComponent: () => import('./features/payments/payments').then((m) => m.Payments),
        title: 'Pagos · EvolutionFit',
      },
      {
        path: 'settings/receipt',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () =>
          import('./features/settings/receipt-settings').then((m) => m.ReceiptSettingsPage),
        title: 'Datos del comprobante · EvolutionFit',
      },
      {
        path: 'users',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/users/users').then((m) => m.Users),
        title: 'Usuarios · EvolutionFit',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
