import { addDays, existingClient, expect, login, open, shown, test, TODAY } from '../support/app';
import { existingClientStep, newClientStep, payStep, planStep } from '../support/enrollment';

test.beforeEach(async ({ page }) => login(page, 'RECEPTIONIST'));

test('cédula existente, buscando con el botón Buscar: inscribe y cobra', async ({ page }) => {
  const client = await existingClient();
  await existingClientStep(page, client, 'Buscar');
  await planStep(page, 'Mensual E2E');
  const result = await payStep(page, 'Efectivo');
  expect(result).toContain(`${client.first} ${client.last}`);
  // El plan mensual cuenta desde el día del pago (el del reloj de pruebas) y dura 30 días.
  expect(result).toContain(`del ${shown(TODAY)} al ${shown(addDays(TODAY, 29))}`);
});

test('cédula nueva, buscando con Enter: registra al cliente, inscribe y cobra', async ({ page }) => {
  const client = await newClientStep(page, 'Enter');
  await planStep(page, 'Mensual E2E');
  const result = await payStep(page, 'Efectivo');
  expect(result).toContain(`${client.first} ${client.last}`);
  expect(result).toContain(`del ${shown(TODAY)} al ${shown(addDays(TODAY, 29))}`);
});

test('cédula nueva con el botón Buscar, y Enter sobre una cédula existente la encuentra', async ({ page }) => {
  const client = await newClientStep(page, 'Buscar');
  await planStep(page, 'Mensual E2E');
  await payStep(page, 'Efectivo');

  const existing = await existingClient();
  await existingClientStep(page, existing, 'Enter');
});

test('un plan de un día vence hoy y aparece en Vencimientos', async ({ page }) => {
  const client = await newClientStep(page, 'Enter');
  await planStep(page, 'Diario E2E');
  const result = await payStep(page, 'Efectivo');
  expect(result).toContain(`del ${shown(TODAY)} al ${shown(TODAY)}`);

  await open(page, '/expirations');
  await page.getByLabel('Buscar por documento o nombre').fill(client.doc);
  const row = page.locator('[data-row]').filter({ hasText: `${client.first} ${client.last}` });
  await expect(row).toContainText('Vence hoy');
  await expect(row.getByRole('link', { name: 'Renovar' })).toBeVisible();
});
