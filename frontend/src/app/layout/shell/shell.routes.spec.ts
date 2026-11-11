import { EnvironmentInjector, createEnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { MatStepperIntl } from '@angular/material/stepper';
import { Route } from '@angular/router';

import { routes } from '../../app.routes';
import { EsPaginatorIntl } from '../../core/i18n/material-intl';
import { SHELL_ROUTES } from './shell.routes';

describe('SHELL_ROUTES', () => {
  const shell = SHELL_ROUTES[0];

  it('conserva todas las pantallas autenticadas y los permisos de ADMIN', () => {
    const paths = (shell.children ?? []).map((r: Route) => r.path);
    expect(paths).toEqual([
      '',
      'dashboard',
      'expirations',
      'clients',
      'clients/:id',
      'memberships',
      'plans',
      'enrollment',
      'payments',
      'cash',
      'cash/closings',
      'cash/report',
      'reports',
      'settings/cash',
      'settings/receipt',
      'users',
    ]);
    const adminOnly = (shell.children ?? []).filter((r: Route) => r.canActivate?.length).map((r: Route) => r.path);
    expect(adminOnly).toEqual(['cash/closings', 'cash/report', 'reports', 'settings/cash', 'settings/receipt', 'users']);
    expect(routes.find((r) => r.path === '')?.canActivate?.length).toBe(1); // sesión obligatoria
  });

  it('todas sus pantallas reciben el paginador y el asistente en español', () => {
    const injector = createEnvironmentInjector(shell.providers ?? [], TestBed.inject(EnvironmentInjector));
    const paginator = injector.get(MatPaginatorIntl);
    expect(paginator).toBeInstanceOf(EsPaginatorIntl);
    expect(paginator.getRangeLabel(0, 20, 1)).toBe('1 – 1 de 1');
    expect(injector.get(MatStepperIntl).optionalLabel).toBe('Opcional');
  });
});
