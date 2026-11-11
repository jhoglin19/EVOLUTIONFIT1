import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { IncomeReport } from '../../shared/models/report.model';
import { Reports } from './reports';
import { ReportsService } from './reports.service';

const INCOME: IncomeReport = {
  from: '2026-11-01',
  to: '2026-11-10',
  grouping: 'DAY',
  methods: [
    { method: 'CASH', receivedCount: 2, receivedAmount: 100000, voidedCount: 1, voidedAmount: 90000 },
    { method: 'NEQUI', receivedCount: 1, receivedAmount: 10000, voidedCount: 0, voidedAmount: 0 },
  ],
  periods: [
    {
      start: '2026-11-10',
      end: '2026-11-10',
      receivedCount: 1,
      receivedAmount: 10000,
      voidedCount: 1,
      voidedAmount: 90000,
      netAmount: -80000,
      cashNet: -90000,
      otherNet: 10000,
    },
  ],
};

function setup() {
  const api = {
    income: vi.fn(() => of(INCOME)),
    expirations: vi.fn(() =>
      of({ from: 'x', to: 'y', total: 4, expired: 4, renewed: 1, pendingPayment: 0, notRenewed: 3, renewalRate: 25, rows: [] }),
    ),
    plans: vi.fn(() => of({ from: 'x', to: 'y', totalSold: 0, totalRevenue: 0, rows: [] })),
    export: vi.fn(() => of(new Blob(['x'], { type: 'text/csv' }))),
  };
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ReportsService, useValue: api }] });
  const fixture = TestBed.createComponent(Reports);
  fixture.detectChanges();
  return { fixture, api, el: fixture.nativeElement as HTMLElement, component: fixture.componentInstance };
}

describe('Reports', () => {
  it('abre en ingresos del mes en curso con la regla de Caja y la aclaración del corte', () => {
    const { el, api, component } = setup();
    const { from, to } = component.range.getRawValue();
    expect(from.endsWith('-01')).toBe(true);
    expect(api.income).toHaveBeenCalledWith(from, to, 'DAY');
    expect(el.querySelector('[data-cutoff-note]')?.textContent).toContain('entre las 10 p.m. y las 12 a.m.');
    // neto = recibido − anulado, también con las anulaciones: 110.000 − 90.000.
    expect(el.querySelector('[data-total-net]')?.textContent).toMatch(/20[.,]000/);
  });

  it('cambia de reporte y de rango', () => {
    const { api, component, fixture, el } = setup();
    component.tabChanged(1);
    fixture.detectChanges();
    expect(api.expirations).toHaveBeenCalled();
    expect(el.querySelector('[data-renewal-rate]')?.textContent).toContain('25');
    component.applyPreset('lastMonth');
    expect(api.expirations).toHaveBeenCalledTimes(2);
  });

  it('no consulta con un rango al revés', () => {
    const { api, component } = setup();
    component.range.setValue({ from: '2026-11-10', to: '2026-11-01' });
    component.load();
    expect(component.error()).toContain('Revisa las fechas');
    expect(api.income).toHaveBeenCalledTimes(1);
  });

  it('exporta el reporte activo a CSV', () => {
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:reporte');
    const saved: string[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      saved.push(this.download);
    });
    const { api, component } = setup();
    component.tabChanged(3);
    component.export();
    const { from, to } = component.range.getRawValue();
    expect(api.export).toHaveBeenCalledWith('plans', from, to, undefined);
    expect(createUrl).toHaveBeenCalledWith(expect.any(Blob));
    expect(saved).toEqual([`plans_${from}_${to}.csv`]);
    click.mockRestore();
    createUrl.mockRestore();
  });
});
