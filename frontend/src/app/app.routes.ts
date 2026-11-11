import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/guards/auth.guards';

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
    // Rutas autenticadas en un bloque perezoso: así sus proveedores (textos de Material en español) no
    // engordan el bundle inicial.
    loadChildren: () => import('./layout/shell/shell.routes').then((m) => m.SHELL_ROUTES),
  },
  { path: '**', redirectTo: '' },
];
