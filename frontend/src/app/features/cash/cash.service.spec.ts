import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CashService } from './cash.service';

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return { api: TestBed.inject(CashService), http: TestBed.inject(HttpTestingController) };
}

describe('CashService', () => {
  it('consulta el día en curso, registra retiros y arqueos con las rutas del backend', () => {
    const { api, http } = setup();
    api.today().subscribe();
    expect(http.expectOne('/api/cash/today').request.method).toBe('GET');

    const withdrawal = { requestId: 'abc', amount: 5000, category: 'SUPPLIES' as const, concept: 'Servilletas' };
    api.withdraw(withdrawal).subscribe();
    const w = http.expectOne('/api/cash/withdrawals');
    expect(w.request.method).toBe('POST');
    expect(w.request.body).toEqual(withdrawal);

    api.countPreview(3).subscribe();
    http.expectOne('/api/cash/closings/3/count-preview');
    api.count(3, { countedCash: 140000, note: null, expectedAdjustedCash: 140000 }).subscribe();
    expect(http.expectOne('/api/cash/closings/3/count').request.body.expectedAdjustedCash).toBe(140000);
    api.pendingCounts().subscribe();
    http.expectOne('/api/cash/closings/pending');
  });

  it('revisa, reabre, anula retiros y consulta el reporte mensual', () => {
    const { api, http } = setup();
    api.review(3, 'Revisado').subscribe();
    expect(http.expectOne('/api/cash/closings/3/review').request.body).toEqual({ note: 'Revisado' });
    api.reopenCount(3, 'Mal digitado').subscribe();
    expect(http.expectOne('/api/cash/closings/3/reopen-count').request.body).toEqual({ reason: 'Mal digitado' });
    api.voidWithdrawal(9, 'Error').subscribe();
    expect(http.expectOne('/api/cash/withdrawals/9/void').request.body).toEqual({ reason: 'Error' });
    api.monthly('2026-11').subscribe();
    expect(http.expectOne((r) => r.url === '/api/cash/reports/monthly').request.params.get('month')).toBe('2026-11');
  });

  it('solo envía los filtros con valor', () => {
    const { api, http } = setup();
    api.closings({ from: '2026-11-01', to: null, status: 'PENDING_REVIEW' }, 0, 31).subscribe();
    const req = http.expectOne((r) => r.url === '/api/cash/closings');
    expect(req.request.params.get('from')).toBe('2026-11-01');
    expect(req.request.params.has('to')).toBe(false);
    expect(req.request.params.get('status')).toBe('PENDING_REVIEW');

    api.withdrawals(null, '2026-11-30', null).subscribe();
    const w = http.expectOne((r) => r.url === '/api/cash/withdrawals');
    expect(w.request.params.has('from')).toBe(false);
    expect(w.request.params.has('category')).toBe(false);
  });
});
