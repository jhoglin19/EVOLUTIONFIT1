import { TestBed } from '@angular/core/testing';

import { CashClosing, CashToday, CountPreview } from '../shared/models/cash.model';
import { Client } from '../shared/models/client.model';
import { membership } from '../features/memberships/membership.fixtures';
import { AppHarness, button, startApp, type, watchNativeSubmits } from './app-harness';

/**
 * Flujos críticos con la aplicación real (rutas, proveedores del shell, MatDialog y formularios reales):
 * se hace clic y se escribe en el DOM como un usuario. Reproducen los fallos vistos en el navegador, donde un
 * <form> sin [formGroup] recargaba la página en vez de ejecutar la acción.
 */

const CLIENT: Client = {
  id: 5,
  documentType: 'CC',
  documentNumber: '1020304050',
  firstName: 'Ana',
  lastName: 'Pérez',
  phone: '3001234567',
  active: true,
} as Client;

const PLANS = { content: [{ id: 2, name: 'Mensual', durationDays: 30, price: 90000, active: true }], page: 0, size: 100, totalElements: 1, totalPages: 1 };

const PENDING: CashClosing = {
  id: 3,
  cashDate: '2026-11-09',
  windowStart: '2026-11-08T22:00:00',
  windowEnd: '2026-11-09T22:00:00',
  status: 'PENDING_COUNT',
  expectedCash: 140000,
} as CashClosing;

const TODAY: CashToday = {
  started: true,
  startDate: '2026-11-01',
  cashDate: '2026-11-10',
  windowStart: '2026-11-09T22:00:00',
  cutoffAt: '2026-11-10T22:00:00',
  openDay: true,
  summary: {
    openingBase: 50000,
    cashIncome: 0,
    cashVoided: 0,
    cashWithdrawals: 0,
    expectedCash: 50000,
    hasCashActivity: false,
    methods: [],
  },
  movements: [],
  pendingCounts: 1,
} as CashToday;

const PREVIEW: CountPreview = {
  closing: PENDING,
  postCutIncome: 0,
  postCutOutflow: 0,
  expectedAdjustedCash: 140000,
  postCutMovements: [],
} as unknown as CountPreview;

let app: AppHarness;
let submits: ReturnType<typeof watchNativeSubmits>;

const overlay = () => document.querySelector('.cdk-overlay-container') as HTMLElement;

beforeEach(() => {
  submits = watchNativeSubmits();
});

afterEach(() => {
  submits.stop();
  sessionStorage.clear();
  document.querySelector('.cdk-overlay-container')?.replaceChildren();
});

describe('Asistente de inscripción: paso Cliente', () => {
  async function open() {
    app = await startApp('RECEPTIONIST', '/enrollment');
    await app.respond({ 'GET /api/plans': PLANS });
    await app.settle();
    return app.page().querySelector<HTMLInputElement>('form.search input')!;
  }

  it('el botón Buscar consulta el documento y muestra el cliente existente', async () => {
    const input = await open();
    type(input, '1.020.304.050');
    button(app.page(), 'Buscar').click();
    await app.settle();

    const req = await app.request((r) => r.url === '/api/clients');
    expect(req.request.params.get('q')).toBe('1020304050');
    req.flush({ content: [CLIENT], page: 0, size: 10, totalElements: 1, totalPages: 1 });
    await app.settle();

    expect(app.page().textContent).toContain('Cliente existente');
    expect(app.page().textContent).toContain('Ana Pérez');
    expect(submits.unhandled()).toBe(0); // en el navegador, un envío sin manejar recarga la página
  });

  it('Enter en el campo también busca; un documento nuevo abre el formulario', async () => {
    const input = await open();
    type(input, '99887766');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    await app.settle();

    (await app.request((r) => r.url === '/api/clients')).flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    await app.settle();

    expect(app.page().textContent).toContain('No está registrado');
    expect(submits.unhandled()).toBe(0);
  });

  it('si la búsqueda falla, el usuario ve el error', async () => {
    const input = await open();
    type(input, '1020304050');
    button(app.page(), 'Buscar').click();
    await app.settle();

    (await app.request((r) => r.url === '/api/clients')).flush({ message: 'El servidor no responde' }, { status: 503, statusText: 'Service Unavailable' });
    await app.settle();

    expect(app.page().querySelector('[role="alert"]')?.textContent?.trim().length).toBeGreaterThan(0);
  });
});

describe('Ficha del cliente: anular membresía (ADMIN)', () => {
  const data = (status: 'ACTIVE' | 'CANCELLED') => {
    const m = membership({ id: 10, clientId: 5, status, cancellationReason: status === 'CANCELLED' ? 'Error' : null });
    return {
      'GET /api/clients/5': CLIENT,
      'GET /api/clients/5/memberships/current': status === 'ACTIVE' ? { current: m } : {},
      'GET /api/clients/5/memberships': [m],
      'GET /api/clients/5/payments': [],
    };
  };

  it('confirmar con motivo anula la membresía y la ficha se actualiza', async () => {
    app = await startApp('ADMIN', '/clients/5');
    await app.respond(data('ACTIVE'));
    await app.settle();

    app.page().querySelector<HTMLButtonElement>('button[aria-label="Anular membresía"]')!.click();
    await app.settle();
    const dialog = overlay();
    type(dialog.querySelector('textarea')!, 'Error de digitación');
    button(dialog, 'Anular').click();
    await app.settle();

    const req = await app.request((r) => r.method === 'POST' && r.url === '/api/memberships/10/cancel');
    expect(req.request.body).toEqual({ reason: 'Error de digitación' });
    req.flush(membership({ id: 10, status: 'CANCELLED' }));
    await app.settle();

    await app.respond(data('CANCELLED'));
    await app.settle();
    expect(app.page().querySelector('button[aria-label="Anular membresía"]')).toBeNull();
    expect(submits.unhandled()).toBe(0);
  });

  it('sin motivo no envía nada y el diálogo sigue abierto', async () => {
    app = await startApp('ADMIN', '/clients/5');
    await app.respond(data('ACTIVE'));
    await app.settle();

    app.page().querySelector<HTMLButtonElement>('button[aria-label="Anular membresía"]')!.click();
    await app.settle();
    button(overlay(), 'Anular').click();
    await app.settle();
    await new Promise((resolve) => setTimeout(resolve, 300));
    await app.settle();

    app.http.expectNone((r) => r.url.endsWith('/cancel'));
    expect(overlay().textContent).toContain('El motivo es obligatorio');
    expect(submits.unhandled()).toBe(0);
  });

  it('si el backend rechaza la anulación, el usuario ve el motivo', async () => {
    app = await startApp('ADMIN', '/clients/5');
    await app.respond(data('ACTIVE'));
    await app.settle();

    app.page().querySelector<HTMLButtonElement>('button[aria-label="Anular membresía"]')!.click();
    await app.settle();
    type(overlay().querySelector('textarea')!, 'Cambio de plan');
    button(overlay(), 'Anular').click();
    await app.settle();

    (await app.request((r) => r.url === '/api/memberships/10/cancel')).flush({ message: 'La membresía ya está anulada' }, { status: 409, statusText: 'Conflict' });
    await app.settle();
    expect(document.body.textContent).toContain('La membresía ya está anulada');
  });
});

describe('Caja: arqueo (RECEPTIONIST)', () => {
  it('registrar el contado guarda el arqueo y el día deja de estar pendiente', async () => {
    app = await startApp('RECEPTIONIST', '/cash');
    await app.respond({ 'GET /api/cash/today': TODAY, 'GET /api/cash/closings/pending': [PENDING] });
    await app.settle();
    expect(app.page().textContent).toContain('Arqueo pendiente');

    button(app.page(), 'Registrar arqueo').click();
    await app.settle();
    await app.respond({ 'GET /api/cash/closings/3/count-preview': PREVIEW });
    await app.settle();

    const dialog = overlay();
    type(dialog.querySelector<HTMLInputElement>('input[type="number"]')!, '140000');
    await app.settle();
    button(dialog, 'Registrar arqueo').click();
    await app.settle();

    const req = await app.request((r) => r.method === 'POST' && r.url === '/api/cash/closings/3/count');
    expect(req.request.body).toEqual({ countedCash: 140000, note: null, expectedAdjustedCash: 140000 });
    req.flush({ ...PENDING, status: 'COUNTED', countedCash: 140000, difference: 0 });
    await app.settle();

    await app.respond({ 'GET /api/cash/today': { ...TODAY, pendingCounts: 0 }, 'GET /api/cash/closings/pending': [] });
    await app.settle();
    expect(app.page().textContent).not.toContain('Arqueo pendiente');
    expect(submits.unhandled()).toBe(0);
  });
});

afterEach(() => {
  TestBed.resetTestingModule();
});
