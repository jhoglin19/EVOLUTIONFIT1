import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { of } from 'rxjs';

import { CashClosing, CashClosingDetail } from '../../../shared/models/cash.model';
import { NOV_10 } from '../cash.fixtures';
import { CashService } from '../cash.service';
import { ClosingDetailDialog } from './closing-detail-dialog';

const DETAIL: CashClosingDetail = {
  closing: {
    id: 1,
    cashDate: '2026-11-10',
    windowStart: '2026-11-09T22:00:00',
    windowEnd: '2026-11-10T22:00:00',
    status: 'PENDING_COUNT',
    closingMode: 'ON_TIME',
    wasOpenDay: true,
    hasCashActivity: true,
    openingBase: 50000,
    cashIncome: 180000,
    cashVoided: 0,
    cashWithdrawals: 5000,
    expectedCash: 225000,
    closedAt: '2026-11-10T22:00:05',
  } as CashClosing,
  methods: NOV_10,
  movements: [],
  postCutMovements: [],
};

describe('ClosingDetailDialog', () => {
  it('muestra los totales del cierre guardado sin tocar el efectivo esperado', () => {
    const api = { closing: vi.fn(() => of(DETAIL)) };
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: 1 },
        { provide: CashService, useValue: api },
      ],
    });
    const fixture = TestBed.createComponent(ClosingDetailDialog);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(api.closing).toHaveBeenCalledWith(1);
    expect(el.querySelector('tr[data-total="all"]')!.textContent).toMatch(/2[.,]280[.,]000/);
    expect(el.querySelector('tr[data-total="other"]')!.textContent).toMatch(/2[.,]100[.,]000/);
    expect(el.textContent).toContain('lo cobrado en ese día de caja');
    expect(el.textContent).toContain('Efectivo esperado (en el cajón)');
    expect(el.textContent).toMatch(/225[.,]000/);
  });
});
