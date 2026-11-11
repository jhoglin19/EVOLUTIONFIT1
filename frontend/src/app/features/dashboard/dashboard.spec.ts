import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AlertCountService } from '../../core/services/alert-count.service';
import { AuthService } from '../../core/services/auth.service';
import { BusinessInfoService } from '../../core/services/business-info.service';
import { AdminDashboard } from '../../shared/models/dashboard.model';
import { dashboard } from '../expirations/expirations.fixtures';
import { Dashboard } from './dashboard';
import { DashboardService } from './dashboard.service';

const INCOME: AdminDashboard = {
  today: '2026-11-10',
  monthStart: '2026-11-01',
  todayMethods: [{ method: 'CASH', receivedCount: 2, receivedAmount: 180000, voidedCount: 0, voidedAmount: 0 }],
  monthMethods: [{ method: 'NEQUI', receivedCount: 5, receivedAmount: 450000, voidedCount: 1, voidedAmount: 90000 }],
  monthVoidedCount: 1,
  monthVoidedAmount: 90000,
  monthDiscountCount: 2,
  monthDiscountAmount: 20000,
};

function setup(role: 'ADMIN' | 'RECEPTIONIST') {
  const api = { today: vi.fn(() => of(dashboard())), admin: vi.fn(() => of(INCOME)) };
  const counts = { set: vi.fn(), refresh: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: DashboardService, useValue: api },
      { provide: AlertCountService, useValue: counts },
      { provide: BusinessInfoService, useValue: { tradeName: signal('Gimnasio Ficticio'), load: vi.fn() } },
      {
        provide: AuthService,
        useValue: { user: signal({ firstName: 'Sara', role }), isAdmin: signal(role === 'ADMIN') },
      },
    ],
  });
  const fixture = TestBed.createComponent(Dashboard);
  fixture.detectChanges();
  return { fixture, api, counts, el: fixture.nativeElement as HTMLElement };
}

describe('Dashboard', () => {
  it('recepción ve el día sin ingresos y no pide los datos del ADMIN', () => {
    const { el, api, counts } = setup('RECEPTIONIST');
    expect(api.admin).not.toHaveBeenCalled();
    expect(el.querySelector('[data-income]')).toBeNull();
    expect(el.querySelector('[data-card="active"]')?.textContent).toContain('42');
    expect(el.querySelector('[data-card="expiring"]')?.textContent).toContain('próximos 5 días');
    expect(el.querySelector('[data-card="enrollments"]')?.textContent).toContain('1 nuevas · 3 renovaciones');
    expect(el.querySelector('[data-card="payments"]')?.textContent).toContain('1 anulados');
    expect(el.querySelector('[data-cash]')?.textContent).toContain('Arqueo pendiente');
    // Pagos del día: lista con el valor de cada uno, sin sumas.
    expect(el.querySelectorAll('[data-payment]').length).toBe(1);
    expect(counts.set).toHaveBeenCalledWith({ expiring: 2, recentlyExpired: 1, warningDays: 5, recentlyExpiredDays: 15 });
  });

  it('las alertas llevan a renovar o a cobrar la renovación pendiente', () => {
    const { el } = setup('RECEPTIONIST');
    const rows = el.querySelectorAll('[data-alert="expiring"]');
    const renew = rows[0].querySelector('a[mat-flat-button]') as HTMLAnchorElement;
    expect(renew.textContent).toContain('Renovar');
    expect(renew.getAttribute('href')).toBe('/enrollment?clientId=5');
    const charge = rows[1].querySelector('a[mat-flat-button]') as HTMLAnchorElement;
    expect(charge.textContent).toContain('Cobrar renovación');
    expect(charge.getAttribute('href')).toBe('/enrollment?membershipId=30');
    // Sin celular no hay botón de WhatsApp.
    expect(rows[0].querySelector('[data-whatsapp]')).not.toBeNull();
    expect(rows[1].querySelector('[data-whatsapp]')).toBeNull();
  });

  it('el ADMIN además ve los ingresos con la regla de Caja', () => {
    const { el, api } = setup('ADMIN');
    expect(api.admin).toHaveBeenCalled();
    const income = el.querySelector('[data-income]')!;
    expect(income.textContent).toContain('10 p.m.');
    expect(income.textContent).toContain('Total general recaudado');
    expect(income.textContent).not.toContain('Efectivo esperado');
  });
});
