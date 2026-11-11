import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AlertCountService } from '../../core/services/alert-count.service';
import { BusinessInfoService } from '../../core/services/business-info.service';
import { Expirations } from './expirations';
import { alert } from './expirations.fixtures';
import { ExpirationsService } from './expirations.service';

function setup(tab?: string) {
  const api = {
    list: vi.fn(() =>
      of({
        content: [alert(), alert({ membershipId: 12, clientId: 6, clientName: 'Luis Gómez', clientPhone: null })],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      }),
    ),
  };
  const counts = {
    refresh: vi.fn(),
    value: signal({ expiring: 2, recentlyExpired: 4, warningDays: 5, recentlyExpiredDays: 15 }),
  };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ExpirationsService, useValue: api },
      { provide: AlertCountService, useValue: counts },
      { provide: BusinessInfoService, useValue: { tradeName: signal('Gimnasio Ficticio'), load: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(Expirations);
  if (tab) {
    fixture.componentRef.setInput('tab', tab);
  }
  fixture.detectChanges();
  return { fixture, api, counts, el: fixture.nativeElement as HTMLElement, component: fixture.componentInstance };
}

describe('Expirations', () => {
  it('lista las que vencen con días restantes y acciones', () => {
    const { el, api, counts } = setup();
    expect(api.list).toHaveBeenCalledWith('EXPIRING', '', 0, 20);
    expect(counts.refresh).toHaveBeenCalledWith(true);
    const rows = el.querySelectorAll('[data-row]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Vence en 2 días');
    expect(rows[1].textContent).toContain('Sin celular');
    expect((rows[0].querySelector('a[mat-flat-button]') as HTMLAnchorElement).getAttribute('href')).toBe(
      '/enrollment?clientId=5',
    );
  });

  it('el recordatorio por WhatsApp lleva el mensaje del dueño y se oculta sin celular', () => {
    const { el } = setup();
    const rows = el.querySelectorAll('[data-row]');
    const link = rows[0].querySelector('[data-whatsapp]') as HTMLAnchorElement;
    expect(link.href.startsWith('https://wa.me/573001234567?text=')).toBe(true);
    expect(decodeURIComponent(link.href.split('?text=')[1])).toBe(
      'Hola Ana Pérez, de parte de Gimnasio Ficticio, tu plan Mensual vence el 12/11/2026. ' +
        'Si quieres renovarlo, te esperamos.',
    );
    expect(link.target).toBe('_blank');
    expect(rows[1].querySelector('[data-whatsapp]')).toBeNull();
  });

  it('?tab=expired abre las vencidas recientes y cambiar de pestaña vuelve a la primera página', () => {
    const { api, component } = setup('expired');
    expect(api.list).toHaveBeenLastCalledWith('EXPIRED', '', 0, 20);
    component.pageIndex.set(2);
    component.tabChanged(0);
    expect(api.list).toHaveBeenLastCalledWith('EXPIRING', '', 0, 20);
  });
});
