import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { Payment } from '../../shared/models/payment.model';
import { Payments } from './payments';
import { PaymentsService } from './payments.service';

const PAYMENT = {
  id: 7,
  clientId: 1,
  clientName: 'Ana Pérez',
  clientDocument: '1001',
  planName: 'Mensual',
  amount: 80000,
  discountAmount: 10000,
  method: 'CASH',
  status: 'COMPLETED',
  paidAt: '2026-11-10T10:00:00',
  receiptNumber: 'EF-000001',
} as Payment;

function setup(role: string, reason?: string) {
  const api = {
    list: vi.fn(() => of({ content: [PAYMENT], page: 0, size: 20, totalElements: 1, totalPages: 1 })),
    voidPayment: vi.fn(() => of(PAYMENT)),
    receipt: vi.fn(),
  };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: PaymentsService, useValue: api },
      { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(reason) }) } },
      { provide: AuthService, useValue: { hasRole: (...roles: string[]) => roles.includes(role) } },
    ],
  });
  const fixture = TestBed.createComponent(Payments);
  fixture.detectChanges();
  return { c: fixture.componentInstance, api, el: fixture.nativeElement as HTMLElement };
}

describe('Payments', () => {
  it('lista pagos con comprobante y descuento', () => {
    const { el } = setup('RECEPTIONIST');
    expect(el.textContent).toContain('EF-000001');
    expect(el.textContent).toContain('Efectivo');
    expect(el.textContent).toContain('Descuento');
    expect(el.querySelector('[aria-label="Ver comprobante"]')).not.toBeNull();
    expect(el.querySelector('[aria-label="Anular pago"]')).toBeNull();
  });

  it('el administrador anula con motivo', () => {
    const { c, api, el } = setup('ADMIN', 'Error');
    expect(el.querySelector('[aria-label="Anular pago"]')).not.toBeNull();
    c.voidPayment(PAYMENT);
    expect(api.voidPayment).toHaveBeenCalledWith(7, 'Error');
  });
});
