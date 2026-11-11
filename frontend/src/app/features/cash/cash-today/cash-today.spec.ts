import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { CashClosing, CashToday } from '../../../shared/models/cash.model';
import { NOV_10 } from '../cash.fixtures';
import { CashService } from '../cash.service';
import { CashTodayPage } from './cash-today';

const TODAY: CashToday = {
  started: true,
  startDate: '2026-11-10',
  cashDate: '2026-11-11',
  windowStart: '2026-11-10T22:00:00',
  cutoffAt: '2026-11-11T22:00:00',
  openDay: true,
  summary: {
    openingBase: 50000,
    cashIncome: 90000,
    cashVoided: 0,
    cashWithdrawals: 5000,
    expectedCash: 135000,
    hasCashActivity: true,
    methods: [{ method: 'NEQUI', receivedCount: 1, receivedAmount: 90000, voidedCount: 0, voidedAmount: 0 }],
  },
  movements: [
    {
      type: 'WITHDRAWAL',
      id: 4,
      at: '2026-11-11T09:00:00',
      method: 'CASH',
      amount: 5000,
      cashEffect: -5000,
      description: 'Servilletas',
      receiptNumber: null,
      category: 'SUPPLIES',
      voided: false,
    },
  ],
  pendingCounts: 1,
};

const PENDING = { id: 3, cashDate: '2026-11-10', expectedCash: 140000, status: 'PENDING_COUNT' } as CashClosing;

function setup(role: string, today: CashToday = TODAY, reason?: string) {
  const api = {
    today: vi.fn(() => of(today)),
    pendingCounts: vi.fn(() => of(today.started ? [PENDING] : [])),
    voidWithdrawal: vi.fn(() => of({})),
  };
  const open = vi.fn(() => ({ afterClosed: () => of(reason) }));
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: CashService, useValue: api },
      { provide: MatDialog, useValue: { open } },
      { provide: AuthService, useValue: { hasRole: (...roles: string[]) => roles.includes(role) } },
    ],
  });
  const fixture = TestBed.createComponent(CashTodayPage);
  fixture.detectChanges();
  return { c: fixture.componentInstance, api, open, el: fixture.nativeElement as HTMLElement };
}

describe('CashTodayPage', () => {
  it('recepción ve el efectivo esperado, el informe, los movimientos y su arqueo pendiente', () => {
    const { el } = setup('RECEPTIONIST');
    expect(el.textContent).toContain('Efectivo esperado');
    expect(el.textContent).toMatch(/135[.,]000/);
    expect(el.textContent).toContain('Nequi');
    expect(el.textContent).toContain('Insumos · Servilletas');
    expect(el.textContent).toContain('Arqueo pendiente');
    expect(el.textContent).toContain('Registrar retiro');
    expect(el.querySelector('[aria-label="Anular retiro"]')).toBeNull();
  });

  it('el informe por método trae los totales y el total general no descuenta retiros', () => {
    const { el } = setup('RECEPTIONIST', {
      ...TODAY,
      summary: { ...TODAY.summary!, cashIncome: 180000, expectedCash: 225000, methods: NOV_10 },
    });
    const all = el.querySelector('tr[data-total="all"]')!.textContent!;
    expect(all).toContain('Total general recaudado');
    expect(all).toMatch(/2[.,]280[.,]000/);
    expect(el.querySelector('tr[data-total="cash"]')!.textContent).toMatch(/180[.,]000/);
    expect(el.querySelector('tr[data-total="other"]')!.textContent).toMatch(/2[.,]100[.,]000/);
    expect(el.textContent).toContain('Efectivo esperado (en el cajón)');
    expect(el.textContent).toMatch(/225[.,]000/); // 50.000 + 180.000 − 5.000 de retiros
  });

  it('abre el arqueo del día pendiente', () => {
    const { c, open } = setup('RECEPTIONIST');
    c.count(PENDING);
    expect(open).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ data: { closingId: 3 } }));
  });

  it('el administrador anula un retiro con motivo', () => {
    const { c, api, el } = setup('ADMIN', TODAY, 'Error');
    expect(el.querySelector('[aria-label="Anular retiro"]')).not.toBeNull();
    c.voidWithdrawal(TODAY.movements[0]);
    expect(api.voidWithdrawal).toHaveBeenCalledWith(4, 'Error');
  });

  it('sin fecha de inicio avisa que la caja no ha empezado', () => {
    const { el } = setup('RECEPTIONIST', { ...TODAY, started: false, startDate: null, summary: null, movements: [] });
    expect(el.textContent).toContain('La caja todavía no ha empezado');
    expect(el.textContent).not.toContain('Registrar retiro');
    expect(el.textContent).not.toContain('Configurar caja');
  });
});
