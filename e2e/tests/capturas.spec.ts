import { mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { Locator, Page } from '@playwright/test';

import { expect, login, open, openDialog, paidClient, test } from '../support/app';

/**
 * Capturas de pantalla para revisar el diseño visual (docs/DISENO_VISUAL.md). Solo corre con E2E_CAPTURAS=1
 * (proyecto "capturas", después de los flujos para que haya datos). No verifica nada funcional: abre cada pantalla
 * y la fotografía. Los diálogos se cierran con Escape, sin guardar nada.
 *
 * Salida: e2e/capturas/<E2E_ETAPA>/<ancho>/<nombre>.png (ignorada por git).
 */
const ETAPA = process.env.E2E_ETAPA || 'sin-etapa';
const OUT = join(fileURLToPath(new URL('../capturas/', import.meta.url)), ETAPA);

const SIZES = [
  { width: 1440, height: 900 },
  { width: 1280, height: 800 },
];

const ADMIN_SCREENS: { name: string; path: string }[] = [
  { name: '01-inicio', path: '/dashboard' },
  { name: '02-vencimientos', path: '/expirations' },
  { name: '03-nueva-inscripcion', path: '/enrollment' },
  { name: '04-clientes', path: '/clients' },
  { name: '05-membresias', path: '/memberships' },
  { name: '06-pagos', path: '/payments' },
  { name: '07-caja', path: '/cash' },
  { name: '08-cierres-de-caja', path: '/cash/closings' },
  { name: '09-reporte-mensual', path: '/cash/report' },
  { name: '10-reportes', path: '/reports' },
  { name: '11-planes', path: '/plans' },
  { name: '12-configuracion-de-caja', path: '/settings/cash' },
  { name: '13-datos-del-comprobante', path: '/settings/receipt' },
  { name: '14-usuarios', path: '/users' },
];

async function settle(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => document.fonts.ready);
  // Animaciones de Material (diálogos, barras de progreso) terminan en menos de 300 ms.
  await page.waitForTimeout(400);
}

async function shot(page: Page, size: { width: number }, name: string): Promise<void> {
  const dir = join(OUT, String(size.width));
  mkdirSync(dir, { recursive: true });
  await settle(page);
  await page.screenshot({ path: join(dir, `${name}.png`), fullPage: true });
}

/** Cierra el diálogo sin guardar (Escape funciona en todos, se llame "Cancelar" o "Volver" su botón). */
async function closeDialog(page: Page, dialog: Locator): Promise<void> {
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
}

for (const size of SIZES) {
  test.describe(`${size.width}px`, () => {
    test.use({ viewport: size });

    test('pantallas del ADMIN', async ({ page }) => {
      test.setTimeout(180_000);
      const client = await paidClient();
      await login(page, 'ADMIN');
      for (const screen of ADMIN_SCREENS) {
        await open(page, screen.path);
        await shot(page, size, screen.name);
      }
      await open(page, `/clients/${client.id}`);
      await expect(page.getByRole('heading', { name: `${client.first} ${client.last}` })).toBeVisible();
      await shot(page, size, '15-ficha-del-cliente');
    });

    test('diálogos', async ({ page }) => {
      await login(page, 'ADMIN');
      await open(page, '/clients');
      let dialog = await openDialog(page, page.getByRole('button', { name: 'Nuevo cliente' }));
      await shot(page, size, '20-dialogo-nuevo-cliente');
      await closeDialog(page, dialog);

      await open(page, '/cash');
      dialog = await openDialog(page, page.getByRole('button', { name: 'Registrar retiro' }));
      await shot(page, size, '21-dialogo-retiro');
      await closeDialog(page, dialog);

      await open(page, '/payments');
      const pay = page.getByRole('button', { name: 'Anular pago' }).first();
      dialog = await openDialog(page, pay);
      await shot(page, size, '22-dialogo-motivo');
      await closeDialog(page, dialog);
    });

    test('recepción y login', async ({ page }) => {
      await page.goto('/login');
      await shot(page, size, '00-login');
      await login(page, 'RECEPTIONIST');
      await shot(page, size, '30-recepcion-inicio');
      await open(page, '/cash');
      await shot(page, size, '31-recepcion-caja');
    });
  });
}
