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
  clientPhone: '300 123 4567',
  planName: 'Mensual',
  membershipStartDate: '2026-11-10',
  membershipEndDate: '2026-12-09',
  amount: 80000,
  discountAmount: 10000,
  method: 'CASH',
  status: 'COMPLETED',
  paidAt: '2026-11-10T10:00:00',
  receiptNumber: 'EF-000001',
} as Payment;

function setup(role: string, reason?: string, rows: Payment[] = [PAYMENT]) {
  const api = {
    list: vi.fn(() => of({ content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 })),
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
    expect(el.querySelector('[aria-label="Descargar PDF"]')).not.toBeNull();
    expect(el.textContent).not.toContain('Imprimir');
    expect(el.querySelector('[aria-label="Anular pago"]')).toBeNull();
  });

  it('el administrador anula con motivo', () => {
    const { c, api, el } = setup('ADMIN', 'Error');
    expect(el.querySelector('[aria-label="Anular pago"]')).not.toBeNull();
    c.voidPayment(PAYMENT);
    expect(api.voidPayment).toHaveBeenCalledWith(7, 'Error');
  });

  it('WhatsApp abre wa.me al teléfono del cliente con +57 y el mensaje', () => {
    const { el } = setup('RECEPTIONIST');
    const link = el.querySelector<HTMLAnchorElement>('a[aria-label="Abrir WhatsApp"]')!;
    expect(link.href.startsWith('https://wa.me/573001234567?text=')).toBe(true);
    expect(decodeURIComponent(link.href)).toContain('comprobante EF-000001');
    expect(link.target).toBe('_blank');
  });

  it('sin teléfono o con el pago anulado no se muestra WhatsApp', () => {
    const { el } = setup('RECEPTIONIST', undefined, [
      { ...PAYMENT, id: 8, clientPhone: null },
      { ...PAYMENT, id: 9, status: 'VOIDED' },
    ]);
    expect(el.querySelectorAll('[aria-label="Descargar PDF"]').length).toBe(2);
    expect(el.querySelector('[aria-label="Abrir WhatsApp"]')).toBeNull();
  });
});
