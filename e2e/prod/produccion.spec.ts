import { readFileSync } from 'node:fs';

import { ConsoleMessage, expect, Request, test } from '@playwright/test';

/**
 * Producción simulada (HTTPS, CSP de Caddy, perfil prod): iniciar sesión, abrir Clientes y la ficha, descargar el
 * comprobante en PDF, y que el navegador no registre errores de CSP ni fallas al cargar fuentes o imágenes.
 * Datos: los crea scripts/prod-smoke.sh por la API antes de esta prueba.
 */
const USER = process.env.E2E_PROD_USER!;
const PASSWORD = process.env.E2E_PROD_PASSWORD!;
const CLIENT_NAME = process.env.E2E_PROD_CLIENT_NAME!;

test('iniciar sesión, abrir Clientes y descargar un comprobante sin errores en la consola', async ({ page }) => {
  const problems: string[] = [];
  page.on('console', (m: ConsoleMessage) => {
    if (m.type() === 'error' || /Content Security Policy|Refused to/i.test(m.text())) {
      problems.push(`consola (${m.type()}): ${m.text()}`);
    }
  });
  page.on('pageerror', (e) => problems.push(`error de página: ${e.message}`));
  page.on('requestfailed', (r: Request) => problems.push(`falló ${r.resourceType()} ${r.url()}: ${r.failure()?.errorText}`));
  page.on('response', (r) => {
    const type = r.request().resourceType();
    if (['font', 'image', 'stylesheet', 'script'].includes(type) && r.status() >= 400) {
      problems.push(`${type} ${r.url()} respondió ${r.status()}`);
    }
  });
  // Cualquier bloqueo de la CSP (también los que no llegan a la consola).
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (e) => {
      console.error(`CSP bloqueó ${e.blockedURI} (${e.violatedDirective})`);
    });
  });

  await page.goto('/login');
  await page.getByLabel('Usuario o correo').fill(USER);
  await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page).toHaveURL(/\/dashboard/);
  // Fuentes propias cargadas (Barlow Condensed de los títulos e Inter del texto).
  await page.evaluate(() => document.fonts.ready);
  const fonts = await page.evaluate(() => [...document.fonts].filter((f) => f.status === 'loaded').map((f) => f.family));
  expect(fonts.join(' ')).toMatch(/Barlow Condensed/);
  expect(fonts.join(' ')).toMatch(/Inter/);

  await page.locator('nav a[href="/clients"]').click();
  await expect(page).toHaveURL(/\/clients$/);
  await page.getByRole('link', { name: CLIENT_NAME }).click();
  await expect(page.getByRole('heading', { level: 1, name: CLIENT_NAME })).toBeVisible();
  // El logo de la barra lateral cargó (imagen con tamaño real).
  const logo = page.locator('.plaque img').first();
  await expect(logo).toBeVisible();
  expect(await logo.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Descargar PDF' }).first().click(),
  ]);
  const file = await download.path();
  const pdf = readFileSync(file);
  expect(pdf.subarray(0, 5).toString()).toBe('%PDF-');
  expect(pdf.length).toBeGreaterThan(1000);
  expect(download.suggestedFilename()).toMatch(/\.pdf$/);

  await page.waitForTimeout(500); // que lleguen los eventos tardíos de la consola
  expect(problems, problems.join('\n')).toEqual([]);
});
