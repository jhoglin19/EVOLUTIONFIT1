import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PaymentRequest } from '../../shared/models/payment.model';
import { PaymentsService } from './payments.service';

const PAYMENT: PaymentRequest = {
  requestId: '3f1c2a9e-1b2c-4d5e-8f90-123456789abc',
  method: 'NEQUI',
  reference: '123',
  notes: null,
  expectedAmount: 90000,
  discountAmount: null,
  discountReason: null,
};

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return { api: TestBed.inject(PaymentsService), http: TestBed.inject(HttpTestingController) };
}

describe('PaymentsService', () => {
  it('inscribe y cobra con las rutas del backend', () => {
    const { api, http } = setup();
    api.enroll({ clientId: 1, client: null, planId: 2, startDate: null, notes: null, payment: PAYMENT }).subscribe();
    const enroll = http.expectOne('/api/enrollments');
    expect(enroll.request.method).toBe('POST');
    expect(enroll.request.body.payment.requestId).toBe(PAYMENT.requestId);

    api.pay(10, PAYMENT).subscribe();
    expect(http.expectOne('/api/memberships/10/payments').request.body).toEqual(PAYMENT);
  });

  it('descarga el comprobante como PDF y anula con motivo', () => {
    const { api, http } = setup();
    api.receipt(7).subscribe();
    expect(http.expectOne('/api/payments/7/receipt.pdf').request.responseType).toBe('blob');

    api.voidPayment(7, 'Error').subscribe();
    const voided = http.expectOne('/api/payments/7/void');
    expect(voided.request.method).toBe('POST');
    expect(voided.request.body).toEqual({ reason: 'Error' });
  });

  it('solo envía los filtros que tienen valor', () => {
    const { api, http } = setup();
    api.list({ from: '2026-11-01', to: null, method: 'CASH', status: null, query: ' EF-0 ' }, 0, 20).subscribe();
    const req = http.expectOne((r) => r.url === '/api/payments');
    expect(req.request.params.get('from')).toBe('2026-11-01');
    expect(req.request.params.has('to')).toBe(false);
    expect(req.request.params.get('method')).toBe('CASH');
    expect(req.request.params.get('q')).toBe('EF-0');
  });
});
