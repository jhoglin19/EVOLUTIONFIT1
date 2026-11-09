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
        path: 'users',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/users/users').then((m) => m.Users),
        title: 'Usuarios · EvolutionFit',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
