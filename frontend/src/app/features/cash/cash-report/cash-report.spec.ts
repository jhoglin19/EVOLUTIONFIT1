import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { MonthlyReport } from '../../../shared/models/cash.model';
import { NOV_10 } from '../cash.fixtures';
import { CashService } from '../cash.service';
import { CashReportPage } from './cash-report';

const REPORT: MonthlyReport = {
  month: '2026-11',
  closedDays: 1,
  cashIncome: 180000,
  cashVoided: 0,
  cashWithdrawals: 5000,
  countedDifference: 0,
  pendingCounts: 0,
  pendingReviews: 0,
  methods: NOV_10,
  withdrawalsByCategory: [{ category: 'SUPPLIES', label: 'Insumos', count: 1, amount: 5000 }],
  days: [],
};

describe('CashReportPage', () => {
  it('suma los totales del mes por método; los retiros no bajan el total general', () => {
    TestBed.configureTestingModule({ providers: [{ provide: CashService, useValue: { monthly: vi.fn(() => of(REPORT)) } }] });
    const fixture = TestBed.createComponent(CashReportPage);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(fixture.componentInstance.totalNet(REPORT)).toBe(2280000);
    expect(el.querySelector('tr[data-total="cash"]')!.textContent).toMatch(/180[.,]000/);
    expect(el.querySelector('tr[data-total="other"]')!.textContent).toMatch(/2[.,]100[.,]000/);
    expect(el.querySelector('tr[data-total="all"]')!.textContent).toMatch(/2[.,]280[.,]000/);
    expect(el.textContent).toContain('Total general recaudado (todos los métodos)');
    expect(el.textContent).toContain('lo cobrado en el mes');
  });
});
