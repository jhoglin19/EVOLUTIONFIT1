import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { BusinessInfoService } from '../../core/services/business-info.service';

import { AuthService } from '../../core/services/auth.service';
import { Client } from '../../shared/models/client.model';
import { EnrollmentRequest, Payment, PaymentRequest } from '../../shared/models/payment.model';
import { Plan } from '../../shared/models/plan.model';
import { ClientsService } from '../clients/clients.service';
import { membership } from '../memberships/membership.fixtures';
import { MembershipsService } from '../memberships/memberships.service';
import { PaymentsService } from '../payments/payments.service';
import { PlansService } from '../plans/plans.service';
import { Enrollment } from './enrollment';

const PLAN: Plan = {
  id: 2,
  name: 'Mensual',
  durationDays: 30,
  price: 90000,
  active: true,
  createdAt: null,
};
const CLIENT: Client = {
  id: 1,
  documentType: 'CC',
  documentNumber: '1001',
  firstName: 'Ana',
  lastName: 'Pérez',
  active: true,
  createdAt: null,
};
const PAID = {
  id: 9,
  clientId: 1,
  clientName: 'Ana Pérez',
  clientPhone: '300 123 4567',
  planName: 'Mensual',
  amount: 90000,
  method: 'CASH',
  status: 'COMPLETED',
  membershipStatus: 'ACTIVE',
  membershipStartDate: '2026-11-10',
  membershipEndDate: '2026-12-09',
  receiptNumber: 'EF-000001',
} as Payment;

function setup(role: string, query: Record<string, string> = {}) {
  const payments = {
    enroll: vi.fn((_request: EnrollmentRequest) => of(PAID)),
    pay: vi.fn((_membershipId: number, _request: PaymentRequest) => of(PAID)),
    receipt: vi.fn(),
  };
  const memberships = {
    preview: vi.fn(() =>
      of({
        planName: 'Mensual',
        price: 90000,
        startDate: '2026-11-10',
        endDate: '2026-12-09',
        scheduled: false,
      }),
    ),
    get: vi.fn(() => of(membership({ id: 20, status: 'PENDING' }))),
    paymentPreview: vi.fn(() =>
      of({
        membershipId: 20,
        planName: 'Mensual',
        listPrice: 90000,
        startDate: '2026-11-10',
        endDate: '2026-12-09',
        scheduled: false,
        requestedStartDate: '2026-11-05',
        requestedStartPassed: true,
      }),
    ),
  };
  const clients = {
    list: vi.fn(() =>
      of({ content: [CLIENT], page: 0, size: 10, totalElements: 1, totalPages: 1 }),
    ),
    get: vi.fn(() => of(CLIENT)),
  };
  TestBed.configureTestingModule({
    providers: [
      {
        provide: BusinessInfoService,
        useValue: { load: vi.fn(), set: vi.fn(), tradeName: () => 'Gimnasio Ficticio' },
      },
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
      },
      { provide: PaymentsService, useValue: payments },
      { provide: MembershipsService, useValue: memberships },
      { provide: ClientsService, useValue: clients },
      {
        provide: PlansService,
        useValue: {
          list: vi.fn(() =>
            of({ content: [PLAN], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
          ),
        },
      },
      { provide: AuthService, useValue: { hasRole: (...roles: string[]) => roles.includes(role) } },
    ],
  });
  const fixture = TestBed.createComponent(Enrollment);
  fixture.detectChanges();
  return {
    fixture,
    c: fixture.componentInstance,
    payments,
    clients,
    el: fixture.nativeElement as HTMLElement,
  };
}

describe('Enrollment', () => {
  it('la referencia es obligatoria para transferencia, Nequi y Daviplata', () => {
    const { c } = setup('RECEPTIONIST');
    const reference = c.paymentForm.controls.reference;
    c.paymentForm.controls.method.setValue('NEQUI');
    expect(reference.valid).toBe(false);
    reference.setValue('123');
    expect(reference.valid).toBe(true);
    reference.setValue('');
    c.paymentForm.controls.method.setValue('CASH');
    expect(reference.valid).toBe(true);
  });

  it('el descuento solo lo ve y lo aplica el administrador', () => {
    const recepcion = setup('RECEPTIONIST');
    expect(recepcion.el.textContent).not.toContain('Descuento en COP');
    recepcion.c.paymentForm.controls.discountAmount.setValue(10000);
    expect(recepcion.c.discount()).toBe(0);
    TestBed.resetTestingModule();

    const admin = setup('ADMIN');
    admin.c.planForm.controls.planId.setValue(2);
    admin.c.paymentForm.controls.discountAmount.setValue(10000);
    expect(admin.c.discountProblem()).toContain('motivo');
    admin.c.paymentForm.controls.discountReason.setValue('Convenio');
    expect(admin.c.discountProblem()).toBeNull();
    expect(admin.c.total()).toBe(80000);
    admin.c.paymentForm.controls.discountAmount.setValue(100000);
    expect(admin.c.discountProblem()).toContain('no puede superar');
  });

  it('un cliente ya registrado se reutiliza en lugar de duplicarlo', () => {
    const { c, clients } = setup('RECEPTIONIST');
    c.search.setValue('1.001');
    c.findClient();
    expect(clients.list).toHaveBeenCalledWith('1001', null, 0, 10);
    expect(c.client()).toEqual(CLIENT);
    expect(c.clientStepDone()).toBe(true);
  });

  it('inscribe a un cliente nuevo en una sola solicitud con el total calculado', () => {
    const { c, clients, payments } = setup('RECEPTIONIST');
    clients.list.mockReturnValue(
      of({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }),
    );
    c.search.setValue('2002');
    c.findClient();
    c.clientForm.patchValue({ firstName: 'Luis', lastName: 'Gómez' });
    c.planForm.controls.planId.setValue(2);
    c.paymentForm.patchValue({ method: 'NEQUI', reference: ' 987 ' });

    c.confirm();

    const request = payments.enroll.mock.calls[0]![0];
    expect(request.clientId).toBeNull();
    expect(request.client).toMatchObject({
      documentNumber: '2002',
      firstName: 'Luis',
      lastName: 'Gómez',
    });
    expect(request.planId).toBe(2);
    expect(request.payment).toMatchObject({
      method: 'NEQUI',
      reference: '987',
      expectedAmount: 90000,
      discountAmount: null,
    });
    expect(c.result()).toBe(PAID);
  });

  it('un doble clic envía una sola solicitud y un reintento usa el mismo identificador', () => {
    const { c, payments } = setup('RECEPTIONIST', { clientId: '1' });
    c.planForm.controls.planId.setValue(2);
    payments.enroll.mockReturnValue(NEVER as never); // la primera sigue en curso
    c.confirm();
    c.confirm();
    expect(payments.enroll).toHaveBeenCalledTimes(1);
    TestBed.resetTestingModule();

    const retry = setup('RECEPTIONIST', { clientId: '1' });
    retry.c.planForm.controls.planId.setValue(2);
    retry.payments.enroll.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 0 })) as never,
    );
    retry.c.confirm();
    expect(retry.c.error()).toContain('No se pudo conectar');
    retry.c.confirm();
    const [first, second] = retry.payments.enroll.mock.calls.map(
      (call) => call[0].payment.requestId,
    );
    expect(first).toBe(retry.c.currentRequestId());
    expect(second).toBe(first);
  });

  it('al cobrar una pendiente avisa si la fecha elegida ya pasó', () => {
    const { el, c, payments } = setup('RECEPTIONIST', { membershipId: '20' });
    expect(c.mode()).toBe('pay');
    expect(el.textContent).toContain('ya pasó: la membresía empezará hoy');
    c.confirm();
    expect(payments.pay).toHaveBeenCalledWith(
      20,
      expect.objectContaining({ expectedAmount: 90000 }),
    );
  });

  it('al terminar ofrece descargar el PDF y abrir WhatsApp, sin imprimir', () => {
    const { c, fixture, el } = setup('RECEPTIONIST', { clientId: '1' });
    c.planForm.controls.planId.setValue(2);
    c.confirm();
    fixture.detectChanges();
    expect(el.textContent).toContain('Descargar PDF');
    expect(el.textContent).not.toContain('mprimir');
    const link = el.querySelector<HTMLAnchorElement>('.done a[href^="https://wa.me/"]')!;
    expect(link.textContent).toContain('Abrir WhatsApp');
    expect(link.href.startsWith('https://wa.me/573001234567?text=')).toBe(true);
  });

  it('si el cliente no tiene teléfono no se muestra WhatsApp', () => {
    const { c, fixture, el, payments } = setup('RECEPTIONIST', { clientId: '1' });
    payments.enroll.mockReturnValue(of({ ...PAID, clientPhone: null }));
    c.planForm.controls.planId.setValue(2);
    c.confirm();
    fixture.detectChanges();
    expect(el.textContent).toContain('Descargar PDF');
    expect(el.textContent).not.toContain('Abrir WhatsApp');
    expect(el.textContent).toContain('no tiene teléfono');
  });
});
