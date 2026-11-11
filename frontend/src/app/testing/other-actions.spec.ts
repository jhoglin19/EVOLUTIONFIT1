import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { membership } from '../features/memberships/membership.fixtures';
import { CashClosing } from '../shared/models/cash.model';
import { Client } from '../shared/models/client.model';
import { Payment } from '../shared/models/payment.model';
import { AppHarness, button, startApp, type, watchNativeSubmits } from './app-harness';

/**
 * Las demás acciones de las pantallas, con la aplicación real: cada una debe llegar al backend y mostrar el
 * resultado, sin recargar la página, y mostrar el error si el backend falla. Datos ficticios.
 */

const CLIENT = { id: 5, documentType: 'CC', documentNumber: '1020304050', firstName: 'Ana', lastName: 'Pérez',
  phone: '3001234567', active: true } as Client;

const PAYMENT = {
  id: 90, membershipId: 10, clientId: 5, clientName: 'Ana Pérez', clientDocument: '1020304050', clientPhone: '3001234567',
  planName: 'Mensual', membershipStartDate: '2026-11-10', membershipEndDate: '2026-12-09', membershipStatus: 'ACTIVE',
  listPrice: 90000, discountAmount: 0, amount: 90000, method: 'CASH', status: 'COMPLETED', paidAt: '2026-11-10T09:00:00',
  receiptNumber: 'EF-000010',
} as Payment;

const page = <T>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });

const REVIEW: CashClosing = { id: 3, cashDate: '2026-11-09', status: 'PENDING_REVIEW', difference: -2000,
  expectedCash: 140000, countedCash: 138000, wasOpenDay: true } as CashClosing;

let app: AppHarness;
let submits: ReturnType<typeof watchNativeSubmits>;
const overlay = () => document.querySelector('.cdk-overlay-container') as HTMLElement;

beforeEach(() => (submits = watchNativeSubmits()));
afterEach(() => {
  expect(submits.unhandled()).toBe(0); // ningún envío recarga la página
  submits.stop();
  sessionStorage.clear();
  overlay()?.replaceChildren();
  TestBed.resetTestingModule();
});

it('cobrar una membresía pendiente: Siguiente y "Cobrar y activar" envían el pago', async () => {
  app = await startApp('RECEPTIONIST', '/enrollment?membershipId=10');
  const pending = membership({ id: 10, clientId: 5, status: 'PENDING' });
  await app.respond({
    'GET /api/memberships/10': pending,
    'GET /api/memberships/10/payment-preview': { membershipId: 10, planName: 'Mensual', listPrice: 90000,
      startDate: '2026-11-10', endDate: '2026-12-09', scheduled: false, requestedStartPassed: false },
  });
  await app.respond({ 'GET /api/clients/5': CLIENT });

  button(app.page(), 'Siguiente').click();
  await app.settle();
  button(app.page(), 'Cobrar y activar').click();

  const req = await app.request((r) => r.method === 'POST' && r.url === '/api/memberships/10/payments');
  expect(req.request.body).toMatchObject({ method: 'CASH', expectedAmount: 90000 });
  req.flush(PAYMENT);
  await app.settle();
  expect(app.page().textContent).toContain('EF-000010');
});

it('anular un pago (ADMIN): el motivo llega al backend y la lista se actualiza', async () => {
  app = await startApp('ADMIN', '/payments');
  await app.respond({ 'GET /api/payments': page([PAYMENT]) });

  app.page().querySelector<HTMLButtonElement>('button[aria-label="Anular pago"]')!.click();
  await app.settle();
  type(overlay().querySelector('textarea')!, 'Pago duplicado');
  button(overlay(), 'Anular').click();

  const req = await app.request((r) => r.method === 'POST' && r.url === '/api/payments/90/void');
  expect(req.request.body).toEqual({ reason: 'Pago duplicado' });
  req.flush({ ...PAYMENT, status: 'VOIDED' });
  await app.respond({ 'GET /api/payments': page([{ ...PAYMENT, status: 'VOIDED' }]) });
  expect(app.page().textContent).toContain('Anulado');
});

it('congelar desde la ficha: el diálogo envía la congelación y muestra el error del backend', async () => {
  app = await startApp('RECEPTIONIST', '/clients/5');
  const m = membership({ id: 10, clientId: 5 });
  await app.respond({
    'GET /api/clients/5': CLIENT,
    'GET /api/clients/5/memberships/current': { current: m },
    'GET /api/clients/5/memberships': [m],
    'GET /api/clients/5/payments': [],
  });

  button(app.page(), 'Congelar').click();
  await app.settle();
  const dialog = overlay();
  type(dialog.querySelector<HTMLInputElement>('input[type="number"]')!, '7');
  type(dialog.querySelector('textarea')!, 'Viaje');
  await app.settle();
  dialog.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

  const req = await app.request((r) => r.method === 'POST' && r.url === '/api/memberships/10/freezes');
  expect(req.request.body).toMatchObject({ days: 7, reason: 'Viaje' });
  req.flush({ message: 'La membresía ya usó su congelación' }, { status: 409, statusText: 'Conflict' });
  await app.settle();
  expect(overlay().textContent).toContain('La membresía ya usó su congelación');
});

it('registrar un retiro (RECEPTIONIST) llega al backend', async () => {
  app = await startApp('RECEPTIONIST', '/cash');
  await app.respond({
    'GET /api/cash/today': { started: true, startDate: '2026-11-01', cashDate: '2026-11-10', openDay: true,
      summary: { openingBase: 50000, cashIncome: 90000, cashVoided: 0, cashWithdrawals: 0, expectedCash: 140000,
        hasCashActivity: true, methods: [] }, movements: [], pendingCounts: 0 },
    'GET /api/cash/closings/pending': [],
  });

  button(app.page(), 'Registrar retiro').click();
  await app.settle();
  const dialog = overlay();
  type(dialog.querySelector<HTMLInputElement>('input[type="number"]')!, '5000');
  type(dialog.querySelector<HTMLInputElement>('input[formcontrolname="concept"]')!, 'Servilletas');
  await app.settle();
  button(dialog, 'Registrar retiro').click();

  const req = await app.request((r) => r.method === 'POST' && r.url === '/api/cash/withdrawals');
  expect(req.request.body).toMatchObject({ amount: 5000, category: 'SUPPLIES', concept: 'Servilletas' });
});

it('revisar una diferencia de arqueo (ADMIN) envía la nota', async () => {
  app = await startApp('ADMIN', '/cash/closings');
  await app.respond({ 'GET /api/cash/closings': page([REVIEW]), 'GET /api/cash/withdrawals': [] });

  app.page().querySelector<HTMLButtonElement>('button[aria-label="Revisar diferencia"]')!.click();
  await app.settle();
  type(overlay().querySelector('textarea')!, 'Faltaron $2.000 de cambio');
  button(overlay(), 'Marcar revisada').click();

  const req = await app.request((r) => r.method === 'POST' && r.url === '/api/cash/closings/3/review');
  expect(req.request.body).toEqual({ note: 'Faltaron $2.000 de cambio' });
});

it('guardar la configuración de caja (ADMIN) envía los cambios', async () => {
  app = await startApp('ADMIN', '/settings/cash');
  await app.respond({
    'GET /api/cash/settings': { openingBase: 50000, startDate: '2026-11-01', startDateLocked: true,
      openDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'], closedOnHolidays: true, closingTime: '22:00' },
    'GET /api/cash/calendar': { year: 2026, openDays: [], closedOnHolidays: true, days: [] },
  });

  type(app.page().querySelector<HTMLInputElement>('input[formcontrolname="openingBase"]')!, '60000');
  await app.settle();
  button(app.page(), 'Guardar').click();

  const req = await app.request((r) => r.method === 'PUT' && r.url === '/api/cash/settings');
  expect(req.request.body).toMatchObject({ openingBase: 60000 });
});

it('reportes (ADMIN): "Consultar" y Enter en la fecha consultan sin recargar; el error se muestra', async () => {
  app = await startApp('ADMIN', '/reports');
  (await app.request((r) => r.url === '/api/reports/income')).flush({
    from: '2026-11-01', to: '2026-11-10', grouping: 'DAY', methods: [], periods: [] });
  await app.settle();

  button(app.page(), 'Consultar').click();
  (await app.request((r) => r.url === '/api/reports/income')).flush(
    { message: 'El rango no puede superar 366 días' }, { status: 400, statusText: 'Bad Request' });
  await app.settle();
  expect(app.page().querySelector('[role="alert"]')?.textContent).toContain('366 días');

  const from = app.page().querySelector<HTMLInputElement>('input[formcontrolname="from"]')!;
  from.form!.requestSubmit(); // lo que hace el navegador con Enter en un campo del formulario
  await app.request((r) => r.url === '/api/reports/income');
});

describe('eliminar cliente (ADMIN)', () => {
  const empty = {
    'GET /api/clients/5': CLIENT,
    'GET /api/clients/5/memberships/current': {},
    'GET /api/clients/5/memberships': [],
    'GET /api/clients/5/payments': [],
  };

  it('sin historial: confirma, borra y vuelve a Clientes', async () => {
    app = await startApp('ADMIN', '/clients/5');
    await app.respond(empty);

    button(app.page(), 'Eliminar cliente').click();
    await app.settle();
    expect(overlay().textContent).toContain('Se borra para siempre');
    button(overlay(), 'Eliminar').click();

    const req = await app.request((r) => r.method === 'DELETE' && r.url === '/api/clients/5');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await app.respond({ 'GET /api/clients': page([]) });
    expect(TestBed.inject(Router).url).toBe('/clients');
  });

  it('si el backend encuentra historial (accesos), muestra el motivo y no sale de la ficha', async () => {
    app = await startApp('ADMIN', '/clients/5');
    await app.respond(empty);

    button(app.page(), 'Eliminar cliente').click();
    await app.settle();
    button(overlay(), 'Eliminar').click();
    (await app.request((r) => r.method === 'DELETE')).flush(
      { message: 'No se puede eliminar: el cliente tiene historial (0 membresía(s), 0 pago(s), 1 acceso(s)). Puedes desactivarlo.' },
      { status: 409, statusText: 'Conflict' },
    );
    await app.settle();
    expect(document.body.textContent).toContain('Puedes desactivarlo');
    expect(TestBed.inject(Router).url).toBe('/clients/5');
  });

  it('con historial o para recepción no aparece el botón', async () => {
    app = await startApp('ADMIN', '/clients/5');
    const m = membership({ id: 10, clientId: 5, status: 'CANCELLED', cancellationReason: 'x' });
    await app.respond({ ...empty, 'GET /api/clients/5/memberships': [m] });
    expect(app.page().querySelector('[data-delete-client]')).toBeNull();
    TestBed.resetTestingModule();
    overlay()?.replaceChildren();

    app = await startApp('RECEPTIONIST', '/clients/5');
    await app.respond(empty);
    expect(app.page().querySelector('[data-delete-client]')).toBeNull();
  });
});
