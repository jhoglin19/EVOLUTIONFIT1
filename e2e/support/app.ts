import { randomUUID } from 'node:crypto';

import { Locator, Page, test as base, expect } from '@playwright/test';

/**
 * Ayudas de las pruebas de punta a punta. Todo pasa por la aplicación real: el navegador habla con el frontend
 * (4300), que habla con el backend de pruebas (8082) y la MySQL de pruebas (3308). Nada está simulado.
 *
 * La API se usa solo para preparar datos que la prueba no está probando (por ejemplo, un cliente que ya
 * existe) y para adelantar el reloj de pruebas. Lo que se prueba siempre se hace con clics en la pantalla.
 */

const API = process.env.E2E_API!;
/** Día de pruebas (el del reloj del backend), aaaa-mm-dd. */
export const TODAY = process.env.E2E_TODAY!;

type Role = 'ADMIN' | 'RECEPTIONIST';
const USERS: Record<Role, { username: string; password: string }> = {
  ADMIN: { username: process.env.E2E_ADMIN_USER!, password: process.env.E2E_ADMIN_PASSWORD! },
  RECEPTIONIST: { username: process.env.E2E_RECEPTION_USER!, password: process.env.E2E_RECEPTION_PASSWORD! },
};

// ---- datos únicos por prueba ----

let counter = 0;
const stamp = String(Date.now()).slice(-5);

/** Cédula única (10 dígitos): ninguna prueba depende de los datos de otra. */
export function uniqueDoc(): string {
  counter++;
  return `7${stamp}${String(counter).padStart(3, '0')}${Math.floor(Math.random() * 10)}`;
}

export function uniquePhone(): string {
  return `3${stamp}${String(counter).padStart(3, '0')}${Math.floor(Math.random() * 10)}`;
}

export function uniqueName(): { first: string; last: string } {
  return { first: `Prueba${stamp}${counter}`, last: `E2E${randomUUID().slice(0, 4)}` };
}

// ---- fechas ----

export function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** aaaa-mm-dd → dd/mm/aaaa, como se ve en pantalla. */
export const shown = (iso: string) => iso.split('-').reverse().join('/');

// ---- API real (preparación y reloj) ----

const tokens: Partial<Record<Role, string>> = {};

export async function api<T = unknown>(method: string, path: string, body?: unknown, role: Role = 'ADMIN'): Promise<T> {
  tokens[role] ??= await loginToken(role);
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens[role]}` },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${method} ${path} → ${res.status}: ${text.slice(0, 300)}`);
  }
  return (text ? JSON.parse(text) : null) as T;
}

export async function apiStatus(method: string, path: string): Promise<number> {
  tokens.ADMIN ??= await loginToken('ADMIN');
  const res = await fetch(API + path, { method, headers: { Authorization: `Bearer ${tokens.ADMIN}` } });
  return res.status;
}

async function loginToken(role: Role): Promise<string> {
  const res = await fetch(API + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identifier: USERS[role].username, password: USERS[role].password }),
  });
  if (!res.ok) {
    throw new Error(`No se pudo iniciar sesión por la API como ${role}: ${res.status}`);
  }
  return (await res.json()).token;
}

/** Hora del reloj de pruebas del backend (hora de Bogotá, sin zona). */
export async function backendNow(): Promise<string> {
  return (await api<{ now: string }>('GET', '/e2e/clock')).now;
}

export async function advanceClock(to: string): Promise<string> {
  return (await api<{ now: string }>('POST', '/e2e/clock/advance', { to })).now;
}

const asDate = (bogota: string) => new Date(`${bogota.slice(0, 19)}-05:00`);

export async function planId(name: string): Promise<number> {
  const page = await api<{ content: { id: number; name: string }[] }>('GET', '/plans?size=100');
  const plan = page.content.find((p) => p.name === name);
  if (!plan) {
    throw new Error(`No existe el plan ${name}`);
  }
  return plan.id;
}

export interface TestClient {
  id: number;
  doc: string;
  first: string;
  last: string;
  phone: string;
}

/** Cliente ya registrado (preparación por la API). */
export async function existingClient(): Promise<TestClient> {
  const doc = uniqueDoc();
  const { first, last } = uniqueName();
  const phone = uniquePhone();
  const c = await api<{ id: number }>('POST', '/clients', {
    documentType: 'CC', documentNumber: doc, firstName: first, lastName: last, email: null, phone,
    birthDate: null, address: null, notes: null,
  });
  return { id: c.id, doc, first, last, phone };
}

/** Cliente con una membresía pagada en efectivo (preparación por la API). */
export async function paidClient(plan = 'Mensual E2E', price = 90000): Promise<TestClient & { paymentId: number }> {
  const client = await existingClient();
  const paid = await api<{ id: number }>('POST', '/enrollments', {
    clientId: client.id, client: null, planId: await planId(plan), startDate: null, notes: null,
    payment: { requestId: randomUUID(), method: 'CASH', reference: null, notes: null, expectedAmount: price,
      discountAmount: null, discountReason: null },
  });
  return { ...client, paymentId: paid.id };
}

// ---- navegador ----

/**
 * `page` con el reloj del navegador igual al reloj de pruebas del backend: el frontend calcula "hoy" (por
 * ejemplo, el inicio por defecto de una congelación) con la hora del navegador.
 */
export const test = base.extend({
  page: async ({ page }, use) => {
    await page.clock.install({ time: asDate(await backendNow()) });
    await use(page);
  },
});
export { expect };

/** Vuelve a igualar el reloj del navegador después de adelantar el del backend. */
export async function syncBrowserClock(page: Page): Promise<void> {
  await page.clock.setSystemTime(asDate(await backendNow()));
}

/** Marca la página: si algo la recarga (un envío de formulario sin manejar), la marca se pierde. */
export async function markPage(page: Page): Promise<void> {
  await page.evaluate(() => ((window as unknown as Record<string, boolean>).__e2eSamePage = true));
}

export async function expectNoReload(page: Page): Promise<void> {
  const same = await page.evaluate(() => (window as unknown as Record<string, boolean>).__e2eSamePage === true);
  expect(same, 'la página se recargó: algún formulario hizo un envío nativo').toBe(true);
}

export async function login(page: Page, role: Role): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Usuario o correo').fill(USERS[role].username);
  await page.getByLabel('Contraseña', { exact: true }).fill(USERS[role].password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await markPage(page);
}

/** Abre una pantalla por su dirección (como escribirla en la barra) y marca la página. */
export async function open(page: Page, path: string): Promise<void> {
  await page.goto(path);
  await expect(page.locator('app-sidebar')).toBeVisible();
  await markPage(page);
}

/** El botón visible con ese texto (el asistente por pasos tiene varios "Siguiente" ocultos). */
export function visibleButton(scope: Page | Locator, name: string): Locator {
  return scope.getByRole('button', { name, exact: true }).filter({ visible: true });
}

/** Elige una opción de un mat-select por su etiqueta. */
export async function choose(page: Page, label: string, option: string | RegExp): Promise<void> {
  await page.getByRole('combobox', { name: label }).click();
  await page.getByRole('option', { name: option }).click();
}
